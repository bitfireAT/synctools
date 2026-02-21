/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.ical4android.ICalendar.Companion.CALENDAR_NAME
import at.bitfire.synctools.BuildConfig
import at.bitfire.synctools.exception.InvalidICalendarException
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
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.DtStart
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
         * @throws InvalidICalendarException when the iCalendar can't be parsed
         */
        @Deprecated("Use ICalendarParser directly")
        fun fromReader(
            reader: Reader,
            properties: MutableMap<String, String>? = null
        ): Calendar {
            TODO("ical4j 4.x")
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
            TODO("ical4j 4.x")
        }

        /**
         * Takes a string with a timezone definition and returns the time zone ID.
         * @param timezoneDef time zone definition (VCALENDAR with VTIMEZONE component)
         * @return time zone id (TZID) if VTIMEZONE contains a TZID, null otherwise
         */
        fun timezoneDefToTzId(timezoneDef: String): String? {
            TODO("ical4j 4.x")
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
         * @param alarm         the alarm to calculate the minutes from
         * @param refStart      reference `DTSTART` from the calendar component
         * @param refEnd        reference `DTEND` (`VEVENT`) or `DUE` (`VTODO`) from the calendar component
         * @param allowRelEnd   *true*: caller accepts minutes related to the end;
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
        fun <T : java.time.temporal.Temporal> vAlarmToMin(
            alarm: VAlarm,
            refStart: DtStart<T>?,
            refEnd: DateProperty<T>?,
            refDuration: net.fortuna.ical4j.model.property.Duration?,
            allowRelEnd: Boolean
        ): Pair<Related, Int>? = TODO("ical4j 4.x")

    }


    fun generateUID() {
        uid = UUID.randomUUID().toString()
    }

}