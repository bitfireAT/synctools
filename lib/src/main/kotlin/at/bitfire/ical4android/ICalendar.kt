/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.ical4android.ICalendar.Companion.CALENDAR_NAME
import at.bitfire.synctools.BuildConfig
import at.bitfire.synctools.exception.InvalidRemoteResourceException
import at.bitfire.synctools.icalendar.ICalendarParser
import at.bitfire.synctools.icalendar.validation.ICalPreprocessor
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.component.Daylight
import net.fortuna.ical4j.model.component.Observance
import net.fortuna.ical4j.model.component.Standard
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.validate.ValidationException
import java.io.Reader
import java.io.StringReader
import java.time.Duration
import java.time.Period
import java.util.LinkedList
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

open class ICalendar {

    open var uid: String? = null
    open var sequence: Int? = null

    /** list of CUAs which have edited the event since last sync */
    open var userAgents = LinkedList<String>()

    companion object {

        private val logger
            get() = Logger.getLogger(ICalendar::class.java.name)

        // known iCalendar properties

        const val CALENDAR_NAME = "X-WR-CALNAME"
        const val CALENDAR_COLOR = "X-APPLE-CALENDAR-COLOR"


        // PRODID generation

        /**
         * Extends the given `PRODID` with the user agents (typically calendar app name and version).
         * This way the `PRODID` does not only identify the app that actually produces the iCalendar,
         * but also the used front-end app, which may be helpful when debugging the iCalendar.
         *
         * @param userAgents    list of involved user agents
         *                      (preferably in `package name/version` format, for instance `com.example.mycalendar/1.0`)
         *
         * @return original `PRODID` with user agents in parentheses
         */
        fun ProdId.withUserAgents(userAgents: List<String>) =
            if (userAgents.isEmpty())
                this
            else
                ProdId(value + " (${userAgents.joinToString(", ")})")


        // parser

        /**
         * Parses an iCalendar resource and applies [ICalPreprocessor] to increase compatibility.
         *
         * @param reader        where the iCalendar is read from
         * @param properties    Known iCalendar properties (like [CALENDAR_NAME]) will be put into this map. Key: property name; value: property value
         *
         * @return parsed iCalendar resource
         *
         * @throws InvalidRemoteResourceException when the iCalendar can't be parsed
         */
        @Deprecated("Use ICalendarParser directly")
        fun fromReader(
            reader: Reader,
            properties: MutableMap<String, String>? = null
        ): Calendar {
            logger.fine("Parsing iCalendar stream")

            val calendar = ICalendarParser().parse(reader)

            // fill calendar properties
            properties?.let {
                calendar.getProperty<Property>(CALENDAR_NAME)?.let { calName ->
                    properties[CALENDAR_NAME] = calName.value
                }

                calendar.getProperty<Property>(Color.PROPERTY_NAME)?.let { calColor ->
                    properties[Color.PROPERTY_NAME] = calColor.value
                }
                calendar.getProperty<Property>(CALENDAR_COLOR)?.let { calColor ->
                    properties[CALENDAR_COLOR] = calColor.value
                }
            }

            return calendar
        }


        // time zone helpers

        /**
         * Minifies a VTIMEZONE so that only these observances are kept:
         *
         *   - the last STANDARD observance matching [start], and
         *   - the last DAYLIGHT observance matching [start], and
         *   - observances beginning after [start]
         *
         * Additionally, TZURL properties are filtered.
         *
         * @param originalTz    time zone definition to minify
         * @param start         start date for components (usually DTSTART); *null* if unknown
         * @return              minified time zone definition
         */
        fun minifyVTimeZone(originalTz: VTimeZone, start: Date?): VTimeZone {
            var newTz: VTimeZone? = null
            val keep = mutableSetOf<Observance>()

            if (start != null) {
                // find latest matching STANDARD/DAYLIGHT observances
                var latestDaylight: Pair<Date, Observance>? = null
                var latestStandard: Pair<Date, Observance>? = null
                for (observance in originalTz.observances) {
                    val latest = observance.getLatestOnset(start)

                    if (latest == null)         // observance begins after "start", keep in any case
                        keep += observance
                    else
                        when (observance) {
                            is Standard ->
                                if (latestStandard == null || latest > latestStandard.first)
                                    latestStandard = Pair(latest, observance)
                            is Daylight ->
                                if (latestDaylight == null || latest > latestDaylight.first)
                                    latestDaylight = Pair(latest, observance)
                        }
                }

                // keep latest STANDARD observance
                latestStandard?.second?.let { keep += it }

                // Check latest DAYLIGHT for whether it can apply in the future. Otherwise, DST is not
                // used in this time zone anymore and the DAYLIGHT component can be dropped completely.
                latestDaylight?.second?.let { daylight ->
                    // check whether start time is in DST
                    if (latestStandard != null) {
                        val latestStandardOnset = latestStandard.second.getLatestOnset(start)
                        val latestDaylightOnset = daylight.getLatestOnset(start)
                        if (latestStandardOnset != null && latestDaylightOnset != null && latestDaylightOnset > latestStandardOnset) {
                            // we're currently in DST
                            keep += daylight
                            return@let
                        }
                    }

                    // check RRULEs
                    for (rRule in daylight.getProperties<RRule>(Property.RRULE)) {
                        val nextDstOnset = rRule.recur.getNextDate(daylight.startDate.date, start)
                        if (nextDstOnset != null) {
                            // there will be a DST onset in the future -> keep DAYLIGHT
                            keep += daylight
                            return@let
                        }
                    }
                    // no RRULE, check whether there's an RDATE in the future
                    for (rDate in daylight.getProperties<RDate>(Property.RDATE)) {
                        if (rDate.dates.any { it >= start }) {
                            // RDATE in the future
                            keep += daylight
                            return@let
                        }
                    }
                }

                // construct minified time zone that only contains the ID and relevant observances
                val relevantProperties = PropertyList<Property>().apply {
                    add(originalTz.timeZoneId)
                }
                val relevantObservances = ComponentList<Observance>().apply {
                    addAll(keep)
                }
                newTz = VTimeZone(relevantProperties, relevantObservances)

                // validate minified timezone
                try {
                    newTz.validate()
                } catch (e: ValidationException) {
                    // This should never happen!
                    logger.log(Level.WARNING, "Minified timezone is invalid, using original one", e)
                    newTz = null
                }
            }

            // use original time zone if we couldn't calculate a minified one
            return newTz ?: originalTz
        }

        /**
         * Takes a string with a timezone definition and returns the time zone ID.
         * @param timezoneDef time zone definition (VCALENDAR with VTIMEZONE component)
         * @return time zone id (TZID) if VTIMEZONE contains a TZID, null otherwise
         */
        fun timezoneDefToTzId(timezoneDef: String): String? {
            try {
                val builder = CalendarBuilder()
                val cal = builder.build(StringReader(timezoneDef))
                val timezone = cal.getComponent(VTimeZone.VTIMEZONE) as VTimeZone?
                timezone?.timeZoneId?.let { return it.value }
            } catch (e: ParserException) {
                logger.log(Level.SEVERE, "Can't understand time zone definition", e)
            }
            return null
        }

        /**
         * Validates an iCalendar resource.
         *
         * Debug builds only: throws [ValidationException] when the resource is invalid.
         * Release builds only: prints a warning to the log when the resource is invalid.
         *
         * @param ical iCalendar resource to be validated
         *
         * @throws ValidationException when the resource is invalid (only if [BuildConfig.DEBUG] is set)
         */
        fun softValidate(ical: Calendar) {
            try {
                ical.validate(true)
            } catch (e: ValidationException) {
                if (BuildConfig.DEBUG)
                    // debug build, re-throw ValidationException
                    throw e
                else
                    logger.log(Level.WARNING, "iCalendar validation failed - This is only a warning!", e)
            }
        }


        // misc. iCalendar helpers

        /**
         * Calculates the minutes before/after an event/task a given alarm occurs.
         *
         * @param alarm the alarm to calculate the minutes from
         * @param reference reference [VEvent] or [VToDo] to take start/end time from (required for calculations)
         * @param allowRelEnd *true*: caller accepts minutes related to the end;
         * *false*: caller only accepts minutes related to the start
         *
         * Android's alarm granularity is minutes. This methods calculates with milliseconds, but the result
         * is rounded down to minutes (seconds cut off).
         *
         * @return Pair of values:
         *
         * 1. whether the minutes are related to the start or end (always [Related.START] if [allowRelEnd] is *false*)
         * 2. number of minutes before start/end (negative value means number of minutes *after* start/end)
         *
         * May be *null* if there's not enough information to calculate the number of minutes.
         */
        fun vAlarmToMin(alarm: VAlarm, reference: ICalendar, allowRelEnd: Boolean): Pair<Related, Int>? {
            val trigger = alarm.trigger ?: return null

            val minutes: Int    // minutes before/after the event
            var related = trigger.getParameter<Related>(Parameter.RELATED) ?: Related.START

            // event/task start time
            val start: java.util.Date?
            var end: java.util.Date?
            when (reference) {
                is Event -> {
                    start = reference.dtStart?.date
                    end = reference.dtEnd?.date
                }
                is Task -> {
                    start = reference.dtStart?.date
                    end = reference.due?.date
                }
                else -> throw IllegalArgumentException("reference must be Event or Task")
            }

            // event/task end time
            if (end == null && start != null) {
                val duration = when (reference) {
                    is Event -> reference.duration?.duration
                    is Task -> reference.duration?.duration
                    else -> throw IllegalArgumentException("reference must be Event or Task")
                }
                if (duration != null)
                    end = java.util.Date.from(start.toInstant() + duration)
            }

            // event/task duration
            val duration: Duration? =
                    if (start != null && end != null)
                        Duration.between(start.toInstant(), end.toInstant())
                    else
                        null

            val triggerDur = trigger.duration
            val triggerTime = trigger.dateTime

            if (triggerDur != null) {
                // TRIGGER value is a DURATION. Important:
                // 1) Negative values in TRIGGER mean positive values in Reminders.MINUTES and vice versa.
                // 2) Android doesn't know alarm seconds, but only minutes. Cut off seconds from the final result.
                // 3) DURATION can be a Duration (time-based) or a Period (date-based), which have to be treated differently.
                var millisBefore =
                    when (triggerDur) {
                        is Duration -> -triggerDur.toMillis()
                        is Period -> // TODO: Take time zones into account (will probably be possible with ical4j 4.x).
                            // For instance, an alarm one day before the DST change should be 23/25 hours before the event.
                            -triggerDur.days.toLong()*24*3600000     // months and years are not used in DURATION values; weeks are calculated to days
                        else -> throw AssertionError("triggerDur must be Duration or Period")
                    }

                if (related == Related.END && !allowRelEnd) {
                    if (duration == null) {
                        logger.warning("Event/task without duration; can't calculate END-related alarm")
                        return null
                    }
                    // move alarm towards end
                    related = Related.START
                    millisBefore -= duration.toMillis()
                }
                minutes = (millisBefore / 60000).toInt()

            } else if (triggerTime != null && start != null) {
                // TRIGGER value is a DATE-TIME, calculate minutes from start time
                related = Related.START
                minutes = Duration.between(triggerTime.toInstant(), start.toInstant()).toMinutes().toInt()

            } else {
                logger.log(Level.WARNING, "VALARM TRIGGER type is not DURATION or DATE-TIME (requires event DTSTART for Android), ignoring alarm", alarm)
                return null
            }

            return Pair(related, minutes)
        }

    }


    protected fun generateUID() {
        uid = UUID.randomUUID().toString()
    }

}