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
import at.bitfire.synctools.icalendar.AssociatedEvents
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Categories
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.Location
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Transp
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Url
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


    fun populate(eventAndExceptions: EventAndExceptions): AssociatedEvents {
        val mainEvent = buildVEvent(eventAndExceptions.main)
        val result = AssociatedEvents(
            main = mainEvent,
            exceptions = eventAndExceptions.exceptions.mapNotNull { exception ->
                populateException(exception, mainEvent)
            }
        )
        return result
    }

    /**
     * Reads data of an event from the calendar provider, i.e. converts the [entity] values into
     * an [Event] data object.
     *
     * @param entity            event row as returned by the calendar provider
     * @param groupScheduled    whether the event is group-scheduled (= the main event has attendees)
     * @param to                destination data object
     */
    private fun buildVEvent(entity: Entity): VEvent {
        // calculate some scheduling properties
        val hasAttendees = entity.subValues.any { it.uri == Attendees.CONTENT_URI }

        // main row
        val vEvent = VEvent()
        populateEventRow(entity.entityValues, groupScheduled = hasAttendees, to = vEvent)

        // data rows
        for (subValue in entity.subValues) {
            val subValues = subValue.values
            when (subValue.uri) {
                Attendees.CONTENT_URI -> populateAttendee(subValues, to = vEvent)
                Reminders.CONTENT_URI -> populateReminder(subValues, to = vEvent)
                ExtendedProperties.CONTENT_URI -> populateExtended(subValues, to = vEvent)
            }
        }

        // TODO useRetainedClass

        return vEvent
    }

    private fun populateEventRow(row: ContentValues, groupScheduled: Boolean, to: VEvent) {
        logger.log(Level.FINE, "Read event entity from calender provider", row)

        // TODO
        /*row.getAsString(Events.MUTATORS)?.let { strPackages ->
            val packages = strPackages.split(AndroidEvent2.MUTATORS_SEPARATOR).toSet()
            to.userAgents.addAll(packages)
        }*/

        val allDay = (row.getAsInteger(Events.ALL_DAY) ?: 0) != 0
        val tsStart = row.getAsLong(Events.DTSTART) ?: throw InvalidLocalResourceException("Found event without DTSTART")

        var tsEnd = row.getAsLong(Events.DTEND)
        var duration =   // only use DURATION of DTEND is not defined
            if (tsEnd == null)
                row.getAsString(Events.DURATION)?.let { AndroidTimeUtils.parseDuration(it) }
            else
                null

        if (allDay) {
            to.properties += DtStart(Date(tsStart))

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
                        to.properties += DtEnd(Date(tsEnd))
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
            to.properties += DtStart(dtStartDateTime)

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
                    to.properties += DtEnd(DateTime(tsEnd).apply {
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
                    to.properties += RRule(rule)
            }
            row.getAsString(Events.RDATE)?.let { datesStr ->
                val rDate = AndroidTimeUtils.androidStringToRecurrenceSet(datesStr, tzRegistry, allDay, tsStart) { RDate(it) }
                to.properties += rDate
            }

            row.getAsString(Events.EXRULE)?.let { rulesStr ->
                for (rule in rulesStr.split(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR))
                    to.properties += ExRule(null, rule)
            }
            row.getAsString(Events.EXDATE)?.let { datesStr ->
                val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(datesStr, tzRegistry, allDay) { ExDate(it) }
                to.properties += exDate
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't parse recurrence rules, ignoring", e)
        }

        row.getAsString(Events.UID_2445)?.let {
            to.properties += Uid(it)
        }
        row.getAsInteger(AndroidEvent2.COLUMN_SEQUENCE)?.let {
            to.properties += Sequence(it)
        }
        //to.isOrganizer = row.getAsBoolean(Events.IS_ORGANIZER)

        row.getAsString(Events.TITLE)?.let {
            to.properties += Summary(it)
        }
        row.getAsString(Events.EVENT_LOCATION)?.let {
            to.properties += Location(it)
        }
        row.getAsString(Events.DESCRIPTION)?.let {
            to.properties += Description(it)
        }

        // color can be specified as RGB value and/or as index key (CSS3 color of AndroidCalendar)
        val color =
            row.getAsString(Events.EVENT_COLOR_KEY)?.let { name ->      // try color key first
                try {
                    Css3Color.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    logger.warning("Ignoring unknown color name \"$name\"")
                    null
                }
            } ?: row.getAsInteger(Events.EVENT_COLOR)?.let { color ->        // otherwise, try to find the color name from the value
                Css3Color.entries.firstOrNull { it.argb == color }
            }
        if (color != null)
            to.properties += Color(null, color.name)

        // status
        when (row.getAsInteger(Events.STATUS)) {
            Events.STATUS_CONFIRMED -> to.properties += Status.VEVENT_CONFIRMED
            Events.STATUS_TENTATIVE -> to.properties += Status.VEVENT_TENTATIVE
            Events.STATUS_CANCELED -> to.properties += Status.VEVENT_CANCELLED
        }

        // availability
        if (row.getAsInteger(Events.AVAILABILITY) == Events.AVAILABILITY_FREE)
            to.properties += Transp.TRANSPARENT
        // Transp.OPAQUE is the default and used in all other cases

        // scheduling
        if (groupScheduled) {
            // ORGANIZER must only be set for group-scheduled events (= events with attendees)
            if (row.containsKey(Events.ORGANIZER))
                try {
                    to.properties += Organizer(URI("mailto", row.getAsString(Events.ORGANIZER), null))
                } catch (e: URISyntaxException) {
                    logger.log(Level.WARNING, "Error when creating ORGANIZER mailto URI, ignoring", e)
                }
        }

        // classification
        when (row.getAsInteger(Events.ACCESS_LEVEL)) {
            Events.ACCESS_PUBLIC -> to.properties += Clazz.PUBLIC
            Events.ACCESS_PRIVATE -> to.properties += Clazz.PRIVATE
            Events.ACCESS_CONFIDENTIAL -> to.properties += Clazz.CONFIDENTIAL
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
                to.startDate?.let { dtStart ->
                    if (dtStart.isUtc)
                        originalDate.isUtc = true
                    else if (dtStart.timeZone != null)
                        originalDate.timeZone = dtStart.timeZone
                }
            }
            to.properties += RecurrenceId(originalDate)
        }
    }

    private fun populateAttendee(row: ContentValues, to: VEvent) {
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

            to.properties += attendee
        } catch (e: URISyntaxException) {
            logger.log(Level.WARNING, "Couldn't parse attendee information, ignoring", e)
        }
    }

    private fun populateReminder(row: ContentValues, to: VEvent) {
        logger.log(Level.FINE, "Read event reminder from calender provider", row)

        val alarm = VAlarm(Duration.ofMinutes(-row.getAsLong(Reminders.MINUTES)))
        to.components += alarm

        val props = alarm.properties
        when (row.getAsInteger(Reminders.METHOD)) {
            Reminders.METHOD_EMAIL -> {
                if (Patterns.EMAIL_ADDRESS.matcher(accountName).matches()) {
                    props += Action.EMAIL
                    // ACTION:EMAIL requires SUMMARY, DESCRIPTION, ATTENDEE
                    props += Summary(to.summary?.value ?: "Reminder")   // email subject
                    props += Description(to.description?.value ?: "Reminder")   // email text
                    // Android doesn't allow to save email reminder recipients, so we always use the
                    // account name (should be account owner's email address)
                    props += Attendee(URI("mailto", accountName, null))
                } else {
                    logger.warning("Account name is not an email address; changing EMAIL reminder to DISPLAY")
                    props += Action.DISPLAY
                    props += Description(to.summary?.value ?: "Reminder")
                }
            }

            // default: set ACTION:DISPLAY (requires DESCRIPTION)
            else -> {
                props += Action.DISPLAY
                props += Description(to.summary?.value ?: "Reminder")   // text to be displayed
            }
        }
    }

    private fun populateExtended(row: ContentValues, to: VEvent) {
        val name = row.getAsString(ExtendedProperties.NAME)
        val rawValue = row.getAsString(ExtendedProperties.VALUE)
        logger.log(Level.FINE, "Read extended property from calender provider", arrayOf(name, rawValue))

        try {
            when (name) {
                AndroidEvent2.EXTNAME_CATEGORIES ->
                    to.properties += Categories(TextList(
                        rawValue.split(AndroidEvent2.CATEGORIES_SEPARATOR).toTypedArray()
                    ))

                AndroidEvent2.EXTNAME_URL ->
                    try {
                        to.properties += Url(URI(rawValue))
                    } catch(_: URISyntaxException) {
                        logger.warning("Won't process invalid local URL: $rawValue")
                    }

                AndroidEvent2.EXTNAME_ICAL_UID ->
                    // only consider iCalUid when there's no uid
                    if (to.uid == null)
                        to.properties += Uid(rawValue)

                UnknownProperty.CONTENT_ITEM_TYPE ->
                    to.properties += UnknownProperty.fromJsonString(rawValue)
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't parse extended property", e)
        }
    }

    private fun populateException(exception: Entity, mainEvent: VEvent): VEvent? {
        // convert exception row to Event
        val exceptionEvent = buildVEvent(exception)

        // TODO: only for non-group-scheduled?
        // generate EXDATE instead of RECURRENCE-ID exceptions for cancelled instances
        val recurrenceId = exceptionEvent.recurrenceId!!
        if (exceptionEvent.status == Status.VEVENT_CANCELLED) {
            val list = DateList(
                if (DateUtils.isDate(recurrenceId)) Value.DATE else Value.DATE_TIME,
                recurrenceId.timeZone
            )
            list.add(recurrenceId.date)
            mainEvent.properties += ExDate(list).apply {
                if (DateUtils.isDateTime(recurrenceId)) {
                    if (recurrenceId.isUtc)
                        setUtc(true)
                    else
                        timeZone = recurrenceId.timeZone
                }
            }
            return null
        }
        // exceptionEvent.status != Status.VEVENT_CANCELLED

        // make sure that all components have the same ORGANIZER [RFC 6638 3.1]
        exceptionEvent.properties.removeIf { it is Organizer }
        exceptionEvent.properties += mainEvent.organizer

        // TODO validations that were in EventWriter:
        // - EventValidator
        // - exception RECUR-ID has same value type as main DTSTART
        // - exception RECUR-ID has same time zone as main DTSTART (if date-time)

        return exceptionEvent
    }

    private fun useRetainedClassification(from: Entity, to: VEvent) {
        val extendedProperties = from.subValues
            .filter { it.uri == ExtendedProperties.CONTENT_URI }
            .map { it.values }
        val unknownProperties = extendedProperties.filter {
            it.getAsString(ExtendedProperties.NAME) == UnknownProperty.CONTENT_ITEM_TYPE
        }.map {
            UnknownProperty.fromJsonString(it.getAsString(ExtendedProperties.VALUE))
        }

        unknownProperties.filterIsInstance<Clazz>().firstOrNull()?.let { retainedClassification ->
            // no classification, use retained one if possible
            if (to.classification == null)
                to.properties += retainedClassification
        }
    }

}