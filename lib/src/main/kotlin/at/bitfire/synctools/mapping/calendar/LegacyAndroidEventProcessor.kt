/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Reminders
import android.util.Patterns
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import at.bitfire.synctools.exception.InvalidLocalResourceException
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.util.TimeZones
import java.net.URI
import java.net.URISyntaxException
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Legacy mapper from Android event main + data rows to an [Event]
 * (former "populate..." methods).
 *
 * Important: To use recurrence exceptions, you MUST set _SYNC_ID and ORIGINAL_SYNC_ID
 * in populateEvent() / buildEvent. Setting _ID and ORIGINAL_ID is not sufficient.
 *
 * @param accountName   account name (used to generate self-attendee)
 */
class LegacyAndroidEventProcessor(
    private val accountName: String
) {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }


    fun populate(eventAndExceptions: EventAndExceptions, to: Event) {
        populateEvent(eventAndExceptions.main, to = to)
        populateExceptions(eventAndExceptions.exceptions, to = to)

        // post-processing
        useRetainedClassification(to)
    }

    /**
     * Reads data of an event from the calendar provider, i.e. converts the [entity] values into
     * an [Event] data object.
     *
     * @param entity            event row as returned by the calendar provider
     * @param groupScheduled    whether the event is group-scheduled (= the main event has attendees)
     * @param to                destination data object
     */
    private fun populateEvent(entity: Entity, to: Event) {
        // calculate some scheduling properties
        val hasAttendees = entity.subValues.any { it.uri == Attendees.CONTENT_URI }

        // main row
        populateEventRow(entity.entityValues, groupScheduled = hasAttendees, to = to)

        // data rows
        for (subValue in entity.subValues) {
            val subValues = subValue.values
            when (subValue.uri) {
                Attendees.CONTENT_URI -> populateAttendee(subValues, to = to)
                Reminders.CONTENT_URI -> populateReminder(subValues, to = to)
                ExtendedProperties.CONTENT_URI -> populateExtended(subValues, to = to)
            }
        }
    }

    private fun populateEventRow(row: ContentValues, groupScheduled: Boolean, to: Event) {
        logger.log(Level.FINE, "Read event entity from calender provider", row)

        row.getAsString(Events.MUTATORS)?.let { strPackages ->
            val packages = strPackages.split(AndroidEvent2.MUTATORS_SEPARATOR).toSet()
            to.userAgents.addAll(packages)
        }

        val allDay = (row.getAsInteger(Events.ALL_DAY) ?: 0) != 0
        val tsStart = row.getAsLong(Events.DTSTART) ?: throw InvalidLocalResourceException("Found event without DTSTART")

        var tsEnd = row.getAsLong(Events.DTEND)
        var duration =   // only use DURATION of DTEND is not defined
            if (tsEnd == null)
                row.getAsString(Events.DURATION)?.let { AndroidTimeUtils.parseDuration(it) }
            else
                null

        if (allDay) {
            to.dtStart = DtStart(Date(tsStart))

            // Android events MUST have duration or dtend [https://developer.android.com/reference/android/provider/CalendarContract.Events#operations].
            // Assume 1 day if missing (should never occur, but occurs).
            if (tsEnd == null && duration == null)
                duration = Duration.ofDays(1)

            if (duration != null) {
                // Some servers have problems with DURATION, so we always generate DTEND.
                val startDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(tsStart), ZoneOffset.UTC).toLocalDate()
                if (duration is Duration)
                    duration = Period.ofDays(duration.toDays().toInt())
                tsEnd = (startDate + duration).toEpochDay() * TimeApiExtensions.MILLIS_PER_DAY
                duration = null
            }

            if (tsEnd != null) {
                when {
                    tsEnd < tsStart ->
                        logger.warning("dtEnd $tsEnd (allDay) < dtStart $tsStart (allDay), ignoring")

                    tsEnd == tsStart ->
                        logger.fine("dtEnd $tsEnd (allDay) = dtStart, won't generate DTEND property")

                    else /* tsEnd > tsStart */ ->
                        to.dtEnd = DtEnd(Date(tsEnd))
                }
            }

        } else /* !allDay */ {
            // use DATE-TIME values

            // check time zone ID (calendar apps may insert no or an invalid ID)
            val startTzId = DateUtils.findAndroidTimezoneID(row.getAsString(Events.EVENT_TIMEZONE))
            val startTz = tzRegistry.getTimeZone(startTzId)
            val dtStartDateTime = DateTime(tsStart).apply {
                if (startTz != null) {  // null if there was not ical4j time zone for startTzId, which should not happen, but technically may happen
                    if (TimeZones.isUtc(startTz))
                        isUtc = true
                    else
                        timeZone = startTz
                }
            }
            to.dtStart = DtStart(dtStartDateTime)

            // Android events MUST have duration or dtend [https://developer.android.com/reference/android/provider/CalendarContract.Events#operations].
            // Assume 1 hour if missing (should never occur, but occurs).
            if (tsEnd == null && duration == null)
                duration = Duration.ofHours(1)

            if (duration != null) {
                // Some servers have problems with DURATION, so we always generate DTEND.
                val zonedStart = dtStartDateTime.toZonedDateTime()
                tsEnd = (zonedStart + duration).toInstant().toEpochMilli()
                duration = null
            }

            if (tsEnd != null) {
                if (tsEnd < tsStart)
                    logger.warning("dtEnd $tsEnd < dtStart $tsStart, ignoring")
                /*else if (tsEnd == tsStart)    // iCloud sends 404 when it receives an iCalendar with DTSTART but without DTEND
                    logger.fine("dtEnd $tsEnd == dtStart, won't generate DTEND property")*/
                else /* tsEnd > tsStart */ {
                    val endTz = row.getAsString(Events.EVENT_END_TIMEZONE)?.let { tzId ->
                        tzRegistry.getTimeZone(tzId)
                    } ?: startTz
                    to.dtEnd = DtEnd(DateTime(tsEnd).apply {
                        if (endTz != null) {
                            if (TimeZones.isUtc(endTz))
                                isUtc = true
                            else
                                timeZone = endTz
                        }
                    })
                }
            }

        }

        // recurrence
        try {
            row.getAsString(Events.RRULE)?.let { rulesStr ->
                for (rule in rulesStr.split(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR))
                    to.rRules += RRule(rule)
            }
            row.getAsString(Events.RDATE)?.let { datesStr ->
                val rDate = AndroidTimeUtils.androidStringToRecurrenceSet(datesStr, tzRegistry, allDay, tsStart) { RDate(it) }
                to.rDates += rDate
            }

            row.getAsString(Events.EXRULE)?.let { rulesStr ->
                for (rule in rulesStr.split(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR))
                    to.exRules += ExRule(null, rule)
            }
            row.getAsString(Events.EXDATE)?.let { datesStr ->
                val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(datesStr, tzRegistry, allDay) { ExDate(it) }
                to.exDates += exDate
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't parse recurrence rules, ignoring", e)
        }

        to.uid = row.getAsString(Events.UID_2445)
        to.sequence = row.getAsInteger(AndroidEvent2.COLUMN_SEQUENCE)
        to.isOrganizer = row.getAsBoolean(Events.IS_ORGANIZER)

        to.summary = row.getAsString(Events.TITLE)
        to.location = row.getAsString(Events.EVENT_LOCATION)
        to.description = row.getAsString(Events.DESCRIPTION)

        // color can be specified as RGB value and/or as index key (CSS3 color of AndroidCalendar)
        to.color =
            row.getAsString(Events.EVENT_COLOR_KEY)?.let { name ->      // try color key first
                try {
                    Css3Color.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    logger.warning("Ignoring unknown color name \"$name\"")
                    null
                }
            } ?:
                    row.getAsInteger(Events.EVENT_COLOR)?.let { color ->        // otherwise, try to find the color name from the value
                        Css3Color.entries.firstOrNull { it.argb == color }
                    }

        // status
        when (row.getAsInteger(Events.STATUS)) {
            Events.STATUS_CONFIRMED -> to.status = Status.VEVENT_CONFIRMED
            Events.STATUS_TENTATIVE -> to.status = Status.VEVENT_TENTATIVE
            Events.STATUS_CANCELED -> to.status = Status.VEVENT_CANCELLED
        }

        // availability
        to.opaque = row.getAsInteger(Events.AVAILABILITY) != Events.AVAILABILITY_FREE

        // scheduling
        if (groupScheduled) {
            // ORGANIZER must only be set for group-scheduled events (= events with attendees)
            if (row.containsKey(Events.ORGANIZER))
                try {
                    to.organizer = Organizer(URI("mailto", row.getAsString(Events.ORGANIZER), null))
                } catch (e: URISyntaxException) {
                    logger.log(Level.WARNING, "Error when creating ORGANIZER mailto URI, ignoring", e)
                }
        }

        // classification
        when (row.getAsInteger(Events.ACCESS_LEVEL)) {
            Events.ACCESS_PUBLIC -> to.classification = Clazz.PUBLIC
            Events.ACCESS_PRIVATE -> to.classification = Clazz.PRIVATE
            Events.ACCESS_CONFIDENTIAL -> to.classification = Clazz.CONFIDENTIAL
        }

        // exceptions from recurring events
        row.getAsLong(Events.ORIGINAL_INSTANCE_TIME)?.let { originalInstanceTime ->
            val originalAllDay = (row.getAsInteger(Events.ORIGINAL_ALL_DAY) ?: 0) != 0
            val originalDate =
                if (originalAllDay)
                    Date(originalInstanceTime)
                else
                    DateTime(originalInstanceTime)
            if (originalDate is DateTime) {
                to.dtStart?.let { dtStart ->
                    if (dtStart.isUtc)
                        originalDate.isUtc = true
                    else if (dtStart.timeZone != null)
                        originalDate.timeZone = dtStart.timeZone
                }
            }
            to.recurrenceId = RecurrenceId(originalDate)
        }
    }

    private fun populateAttendee(row: ContentValues, to: Event) {
        logger.log(Level.FINE, "Read event attendee from calender provider", row)

        try {
            val attendee: Attendee
            val email = row.getAsString(Attendees.ATTENDEE_EMAIL)
            val idNS = row.getAsString(Attendees.ATTENDEE_ID_NAMESPACE)
            val id = row.getAsString(Attendees.ATTENDEE_IDENTITY)

            if (idNS != null || id != null) {
                // attendee identified by namespace and ID
                attendee = Attendee(URI(idNS, id, null))
                email?.let { attendee.parameters.add(Email(it)) }
            } else
            // attendee identified by email address
                attendee = Attendee(URI("mailto", email, null))
            val params = attendee.parameters

            // always add RSVP (offer attendees to accept/decline)
            params.add(Rsvp.TRUE)

            row.getAsString(Attendees.ATTENDEE_NAME)?.let { cn -> params.add(Cn(cn)) }

            // type/relation mapping is complex and thus outsourced to AttendeeMappings
            AttendeeMappings.androidToICalendar(row, attendee)

            // status
            when (row.getAsInteger(Attendees.ATTENDEE_STATUS)) {
                Attendees.ATTENDEE_STATUS_INVITED -> params.add(PartStat.NEEDS_ACTION)
                Attendees.ATTENDEE_STATUS_ACCEPTED -> params.add(PartStat.ACCEPTED)
                Attendees.ATTENDEE_STATUS_DECLINED -> params.add(PartStat.DECLINED)
                Attendees.ATTENDEE_STATUS_TENTATIVE -> params.add(PartStat.TENTATIVE)
                Attendees.ATTENDEE_STATUS_NONE -> { /* no information, don't add PARTSTAT */ }
            }

            to.attendees.add(attendee)
        } catch (e: URISyntaxException) {
            logger.log(Level.WARNING, "Couldn't parse attendee information, ignoring", e)
        }
    }

    private fun populateReminder(row: ContentValues, to: Event) {
        logger.log(Level.FINE, "Read event reminder from calender provider", row)

        val alarm = VAlarm(Duration.ofMinutes(-row.getAsLong(Reminders.MINUTES)))

        val props = alarm.properties
        when (row.getAsInteger(Reminders.METHOD)) {
            Reminders.METHOD_EMAIL -> {
                if (Patterns.EMAIL_ADDRESS.matcher(accountName).matches()) {
                    props += Action.EMAIL
                    // ACTION:EMAIL requires SUMMARY, DESCRIPTION, ATTENDEE
                    props += Summary(to.summary)
                    props += Description(to.description ?: to.summary)
                    // Android doesn't allow to save email reminder recipients, so we always use the
                    // account name (should be account owner's email address)
                    props += Attendee(URI("mailto", accountName, null))
                } else {
                    logger.warning("Account name is not an email address; changing EMAIL reminder to DISPLAY")
                    props += Action.DISPLAY
                    props += Description(to.summary)
                }
            }

            // default: set ACTION:DISPLAY (requires DESCRIPTION)
            else -> {
                props += Action.DISPLAY
                props += Description(to.summary)
            }
        }
        to.alarms += alarm
    }

    private fun populateExtended(row: ContentValues, to: Event) {
        val name = row.getAsString(ExtendedProperties.NAME)
        val rawValue = row.getAsString(ExtendedProperties.VALUE)
        logger.log(Level.FINE, "Read extended property from calender provider", arrayOf(name, rawValue))

        try {
            when (name) {
                AndroidEvent2.EXTNAME_CATEGORIES ->
                    to.categories += rawValue.split(AndroidEvent2.CATEGORIES_SEPARATOR)

                AndroidEvent2.EXTNAME_URL ->
                    try {
                        to.url = URI(rawValue)
                    } catch(_: URISyntaxException) {
                        logger.warning("Won't process invalid local URL: $rawValue")
                    }

                AndroidEvent2.EXTNAME_ICAL_UID ->
                    // only consider iCalUid when there's no uid
                    if (to.uid == null)
                        to.uid = rawValue

                UnknownProperty.CONTENT_ITEM_TYPE ->
                    to.unknownProperties += UnknownProperty.fromJsonString(rawValue)
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't parse extended property", e)
        }
    }

    private fun populateExceptions(exceptions: List<Entity>, to: Event) {
        for (exception in exceptions) {
            val exceptionEvent = Event()

            // exceptions are required to have a RECURRENCE-ID
            val recurrenceId = exceptionEvent.recurrenceId ?: continue

            // convert exception row to Event
            populateEvent(exception, to = exceptionEvent)

            // generate EXDATE instead of RECURRENCE-ID exceptions for cancelled instances
            if (exceptionEvent.status == Status.VEVENT_CANCELLED) {
                val list = DateList(
                    if (DateUtils.isDate(recurrenceId)) Value.DATE else Value.DATE_TIME,
                    recurrenceId.timeZone
                )
                list.add(recurrenceId.date)
                to.exDates += ExDate(list).apply {
                    if (DateUtils.isDateTime(recurrenceId)) {
                        if (recurrenceId.isUtc)
                            setUtc(true)
                        else
                            timeZone = recurrenceId.timeZone
                    }
                }

            } else /* exceptionEvent.status != Status.VEVENT_CANCELLED */ {
                // make sure that all components have the same ORGANIZER [RFC 6638 3.1]
                exceptionEvent.organizer = to.organizer

                // add exception to list of exceptions
                to.exceptions += exceptionEvent
            }
        }
    }

    private fun useRetainedClassification(event: Event) {
        var retainedClazz: Clazz? = null
        val it = event.unknownProperties.iterator()
        while (it.hasNext()) {
            val prop = it.next()
            if (prop is Clazz) {
                retainedClazz = prop
                it.remove()
            }
        }

        if (event.classification == null)
            // no classification, use retained one if possible
            event.classification = retainedClazz
    }

}