/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Colors
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Reminders
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.AndroidEvent.Companion.CATEGORIES_SEPARATOR
import at.bitfire.ical4android.AndroidEvent.Companion.COLUMN_ETAG
import at.bitfire.ical4android.AndroidEvent.Companion.COLUMN_FLAGS
import at.bitfire.ical4android.AndroidEvent.Companion.COLUMN_SCHEDULE_TAG
import at.bitfire.ical4android.AndroidEvent.Companion.COLUMN_SEQUENCE
import at.bitfire.ical4android.AndroidEvent.Companion.EXTNAME_CATEGORIES
import at.bitfire.ical4android.AndroidEvent.Companion.EXTNAME_URL
import at.bitfire.ical4android.AttendeeMappings
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.ICalendar
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.ical4android.util.TimeApiExtensions.requireZoneId
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalTime
import at.bitfire.ical4android.util.TimeApiExtensions.toRfc5545Duration
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import at.bitfire.synctools.exception.InvalidLocalResourceException
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.CalendarBatchOperation
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.Status
import java.time.Duration
import java.time.Period
import java.time.ZonedDateTime
import java.util.Locale
import java.util.logging.Logger

/**
 * Legacy mapper from an [Event] data object to Android content provider data rows
 * (former "build..." methods).
 */
class LegacyAndroidEventBuilder(
    private val calendar: AndroidCalendar,
    private val event: Event,

    // AndroidEvent-level fields
    private val id: Long?,
    private val syncId: String?,
    private val eTag: String?,
    private val scheduleTag: String?,
    private val flags: Int,
) {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    fun addOrUpdateRows(event: Event, batch: CalendarBatchOperation): Int? {
        val builder =
            if (id == null)
                CpoBuilder.newInsert(calendar.eventsUri)
            else
                CpoBuilder.newUpdate(calendar.eventUri(id))

        // return the index of the row containing the event ID in the results (only when adding an event)
        val idxEvent = if (id == null)
            batch.nextBackrefIdx()
        else
            null

        buildEvent(null, builder)
        batch += builder

        // add reminders
        event.alarms.forEach { insertReminder(batch, idxEvent, it) }

        // add attendees
        val organizer = event.organizerEmail ?:
        /* no ORGANIZER, use current account owner as ORGANIZER */
        calendar.ownerAccount ?: calendar.account.name
        event.attendees.forEach { insertAttendee(batch, idxEvent, it, organizer) }

        // add extended properties
        // CATEGORIES
        if (event.categories.isNotEmpty())
            insertCategories(batch, idxEvent)
        // CLASS
        retainClassification()
        // URL
        event.url?.let { url ->
            insertExtendedProperty(batch, idxEvent, EXTNAME_URL, url.toString())
        }
        // unknown properties
        event.unknownProperties.forEach {
            insertUnknownProperty(batch, idxEvent, it)
        }

        // add exceptions
        for (exception in event.exceptions) {
            /* I guess exceptions should be inserted using Events.CONTENT_EXCEPTION_URI so that we could
               benefit from some provider logic (for recurring exceptions e.g.). However, this method
               has some caveats:
               - For instance, only Events.SYNC_DATA1, SYNC_DATA3 and SYNC_DATA7 can be used
               in exception events (that's hardcoded in the CalendarProvider, don't ask me why).
               - Also, CONTENT_EXCEPTIONS_URI doesn't deal with exceptions for recurring events defined by RDATE
               (it checks for RRULE and aborts if no RRULE is found).
               So I have chosen the method of inserting the exception event manually.

               It's also noteworthy that linking the main event to the exception only works using _SYNC_ID
               and ORIGINAL_SYNC_ID (and not ID and ORIGINAL_ID, as one could assume). So, if you don't
               set _SYNC_ID in the main event and ORIGINAL_SYNC_ID in the exception, the exception will
               appear additionally (and not *instead* of the instance).
             */

            val recurrenceId = exception.recurrenceId
            if (recurrenceId == null) {
                logger.warning("Ignoring exception of event ${event.uid} without recurrenceId")
                continue
            }

            val exBuilder = CpoBuilder
                .newInsert(Events.CONTENT_URI.asSyncAdapter(calendar.account))
                .withEventId(Events.ORIGINAL_ID, idxEvent)

            buildEvent(exception, exBuilder)
            if (exBuilder.values[Events.ORIGINAL_SYNC_ID] == null && exBuilder.valueBackrefs[Events.ORIGINAL_SYNC_ID] == null)
                throw AssertionError("buildEvent(exception) must set ORIGINAL_SYNC_ID")

            var recurrenceDate = recurrenceId.date
            val dtStartDate = event.dtStart!!.date
            if (recurrenceDate is DateTime && dtStartDate !is DateTime) {
                // rewrite RECURRENCE-ID;VALUE=DATE-TIME to VALUE=DATE for all-day events
                val localDate = recurrenceDate.toLocalDate()
                recurrenceDate = Date(localDate.toIcal4jDate())

            } else if (recurrenceDate !is DateTime && dtStartDate is DateTime) {
                // rewrite RECURRENCE-ID;VALUE=DATE to VALUE=DATE-TIME for non-all-day-events
                val localDate = recurrenceDate.toLocalDate()
                // guess time and time zone from DTSTART
                val zonedTime = ZonedDateTime.of(
                    localDate,
                    dtStartDate.toLocalTime(),
                    dtStartDate.requireZoneId()
                )
                recurrenceDate = zonedTime.toIcal4jDateTime()
            }
            exBuilder   .withValue(Events.ORIGINAL_ALL_DAY, if (DateUtils.isDate(event.dtStart)) 1 else 0)
                .withValue(Events.ORIGINAL_INSTANCE_TIME, recurrenceDate.time)

            val idxException = batch.nextBackrefIdx()
            batch += exBuilder

            // add exception reminders
            exception.alarms.forEach { insertReminder(batch, idxException, it) }

            // add exception attendees
            exception.attendees.forEach { insertAttendee(batch, idxException, it, organizer) }
        }

        return idxEvent
    }

    /**
     * Builds an Android [Events] row for a given event. Takes information from
     *
     * - this [AndroidEvent] object: fields like calendar ID, sync ID, eTag etc,
     * - the [event]: all other fields.
     *
     * @param recurrence   event to be used as data source; *null*: use this AndroidEvent's main [event] as source
     * @param builder      data row builder to be used
     */
    private fun buildEvent(recurrence: Event?, builder: CpoBuilder) {
        val event = recurrence ?: event

        val dtStart = event.dtStart ?: throw InvalidLocalResourceException("Events must have DTSTART")
        val allDay = DateUtils.isDate(dtStart)

        // make sure that time zone is supported by Android
        AndroidTimeUtils.androidifyTimeZone(dtStart)

        val recurring = event.rRules.isNotEmpty() || event.rDates.isNotEmpty()

        /* [CalendarContract.Events SDK documentation]
           When inserting a new event the following fields must be included:
           - dtstart
           - dtend if the event is non-recurring
           - duration if the event is recurring
           - rrule or rdate if the event is recurring
           - eventTimezone
           - a calendar_id */

        // object-level (AndroidEvent) fields
        builder .withValue(Events.CALENDAR_ID, calendar.id)
            .withValue(Events.DIRTY, 0)     // newly created event rows shall not be marked as dirty
            .withValue(Events.DELETED, 0)   // or deleted
            .withValue(COLUMN_FLAGS, flags)

        if (recurrence == null)
            builder.withValue(Events._SYNC_ID, syncId)
                .withValue(COLUMN_ETAG, eTag)
                .withValue(COLUMN_SCHEDULE_TAG, scheduleTag)
        else
            builder.withValue(Events.ORIGINAL_SYNC_ID, syncId)

        // UID, sequence
        builder .withValue(Events.UID_2445, event.uid)
            .withValue(COLUMN_SEQUENCE, event.sequence)

        // time fields
        builder .withValue(Events.DTSTART, dtStart.date.time)
            .withValue(Events.ALL_DAY, if (allDay) 1 else 0)
            .withValue(Events.EVENT_TIMEZONE, AndroidTimeUtils.storageTzId(dtStart))

        var dtEnd = event.dtEnd
        AndroidTimeUtils.androidifyTimeZone(dtEnd)

        var duration =
            if (dtEnd == null)
                event.duration?.duration
            else
                null
        if (allDay && duration is Duration)
            duration = Period.ofDays(duration.toDays().toInt())

        if (recurring) {
            // duration must be set
            if (duration == null) {
                if (dtEnd != null) {
                    // calculate duration from dtEnd
                    duration = if (allDay)
                        Period.between(dtStart.date.toLocalDate(), dtEnd.date.toLocalDate())
                    else
                        Duration.between(dtStart.date.toInstant(), dtEnd.date.toInstant())
                } else {
                    // no dtEnd and no duration
                    duration = if (allDay)
                    /* [RFC 5545 3.6.1 Event Component]
                       For cases where a "VEVENT" calendar component
                       specifies a "DTSTART" property with a DATE value type but no
                       "DTEND" nor "DURATION" property, the event's duration is taken to
                       be one day. */
                        Period.ofDays(1)
                    else
                    /* For cases where a "VEVENT" calendar component
                       specifies a "DTSTART" property with a DATE-TIME value type but no
                       "DTEND" property, the event ends on the same calendar date and
                       time of day specified by the "DTSTART" property. */

                    // Duration.ofSeconds(0) causes the calendar provider to crash
                        Period.ofDays(0)
                }
            }

            // iCalendar doesn't permit years and months, only PwWdDThHmMsS
            builder .withValue(Events.DURATION, duration?.toRfc5545Duration(dtStart.date.toInstant()))
                .withValue(Events.DTEND, null)

            // add RRULEs
            if (event.rRules.isNotEmpty()) {
                builder.withValue(Events.RRULE, event.rRules
                    .joinToString(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR) { it.value })
            } else
                builder.withValue(Events.RRULE, null)

            if (event.rDates.isNotEmpty()) {
                // ignore RDATEs when there's also an infinite RRULE [https://issuetracker.google.com/issues/216374004]
                val infiniteRrule = event.rRules.any { rRule ->
                    rRule.recur.count == -1 &&  // no COUNT AND
                            rRule.recur.until == null   // no UNTIL
                }

                if (infiniteRrule)
                    logger.warning("Android can't handle infinite RRULE + RDATE [https://issuetracker.google.com/issues/216374004]; ignoring RDATE(s)")
                else {
                    for (rDate in event.rDates)
                        AndroidTimeUtils.androidifyTimeZone(rDate)

                    // Calendar provider drops DTSTART instance when using RDATE [https://code.google.com/p/android/issues/detail?id=171292]
                    val listWithDtStart = DateList()
                    listWithDtStart.add(dtStart.date)
                    event.rDates.addFirst(RDate(listWithDtStart))

                    builder.withValue(Events.RDATE, AndroidTimeUtils.recurrenceSetsToAndroidString(event.rDates, dtStart.date))
                }
            } else
                builder.withValue(Events.RDATE, null)

            if (event.exRules.isNotEmpty())
                builder.withValue(Events.EXRULE, event.exRules.joinToString(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR) { it.value })
            else
                builder.withValue(Events.EXRULE, null)

            if (event.exDates.isNotEmpty()) {
                for (exDate in event.exDates)
                    AndroidTimeUtils.androidifyTimeZone(exDate)
                builder.withValue(Events.EXDATE, AndroidTimeUtils.recurrenceSetsToAndroidString(event.exDates, dtStart.date))
            } else
                builder.withValue(Events.EXDATE, null)

        } else /* !recurring */ {
            // dtend must be set
            if (dtEnd == null) {
                if (duration != null) {
                    // calculate dtEnd from duration
                    if (allDay) {
                        val calcDtEnd = dtStart.date.toLocalDate() + duration
                        dtEnd = DtEnd(calcDtEnd.toIcal4jDate())
                    } else {
                        val zonedStartTime = (dtStart.date as DateTime).toZonedDateTime()
                        val calcEnd = zonedStartTime + duration
                        val calcDtEnd = DtEnd(calcEnd.toIcal4jDateTime())
                        calcDtEnd.timeZone = dtStart.timeZone
                        dtEnd = calcDtEnd
                    }
                } else {
                    // no dtEnd and no duration
                    dtEnd = if (allDay) {
                        /* [RFC 5545 3.6.1 Event Component]
                           For cases where a "VEVENT" calendar component
                           specifies a "DTSTART" property with a DATE value type but no
                           "DTEND" nor "DURATION" property, the event's duration is taken to
                           be one day. */
                        val calcDtEnd = dtStart.date.toLocalDate() + Period.ofDays(1)
                        DtEnd(calcDtEnd.toIcal4jDate())
                    } else
                    /* For cases where a "VEVENT" calendar component
                       specifies a "DTSTART" property with a DATE-TIME value type but no
                       "DTEND" property, the event ends on the same calendar date and
                       time of day specified by the "DTSTART" property. */
                        DtEnd(dtStart.value, dtStart.timeZone)
                }
            }

            AndroidTimeUtils.androidifyTimeZone(dtEnd)
            builder .withValue(Events.DTEND, dtEnd.date.time)
                .withValue(Events.EVENT_END_TIMEZONE, AndroidTimeUtils.storageTzId(dtEnd))
                .withValue(Events.DURATION, null)
                .withValue(Events.RRULE, null)
                .withValue(Events.RDATE, null)
                .withValue(Events.EXRULE, null)
                .withValue(Events.EXDATE, null)
        }

        // text fields
        builder.withValue(Events.TITLE, event.summary)
            .withValue(Events.EVENT_LOCATION, event.location)
            .withValue(Events.DESCRIPTION, event.description)

        // color
        val color = event.color
        if (color != null) {
            // set event color (if it's available for this account)
            calendar.client.query(Colors.CONTENT_URI.asSyncAdapter(calendar.account), arrayOf(Colors.COLOR_KEY),
                "${Colors.COLOR_KEY}=? AND ${Colors.COLOR_TYPE}=${Colors.TYPE_EVENT}", arrayOf(color.name), null)?.use { cursor ->
                if (cursor.moveToNext())
                    builder.withValue(Events.EVENT_COLOR_KEY, color.name)
                else
                    logger.fine("Ignoring event color \"${color.name}\" (not available for this account)")
            }
        } else {
            // reset color index and value
            builder .withValue(Events.EVENT_COLOR_KEY, null)
                .withValue(Events.EVENT_COLOR, null)
        }

        // scheduling
        val groupScheduled = event.attendees.isNotEmpty()
        if (groupScheduled) {
            builder .withValue(Events.HAS_ATTENDEE_DATA, 1)
                .withValue(Events.ORGANIZER, event.organizer?.let { organizer ->
                    val uri = organizer.calAddress
                    val email = if (uri.scheme.equals("mailto", true))
                        uri.schemeSpecificPart
                    else
                        organizer.getParameter<Email>(Parameter.EMAIL)?.value

                    if (email != null)
                        return@let email

                    logger.warning("Ignoring ORGANIZER without email address (not supported by Android)")
                    null
                } ?: calendar.ownerAccount)

        } else /* !groupScheduled */
            builder .withValue(Events.HAS_ATTENDEE_DATA, 0)
                .withValue(Events.ORGANIZER, calendar.ownerAccount)

        // Attention: don't update event with STATUS != null to STATUS = null (causes calendar provider operation to fail)!
        // In this case, the whole event must be deleted and inserted again.
        if (/* insert, not an update */ id == null || /* update, but we're not updating to null */ event.status != null)
            builder.withValue(Events.STATUS, when (event.status) {
                null /* not possible by if statement */ -> null
                Status.VEVENT_CONFIRMED -> Events.STATUS_CONFIRMED
                Status.VEVENT_CANCELLED -> Events.STATUS_CANCELED
                else -> Events.STATUS_TENTATIVE
            })

        builder .withValue(Events.AVAILABILITY, if (event.opaque) Events.AVAILABILITY_BUSY else Events.AVAILABILITY_FREE)
            .withValue(Events.ACCESS_LEVEL, when (event.classification) {
                null -> Events.ACCESS_DEFAULT
                Clazz.PUBLIC -> Events.ACCESS_PUBLIC
                Clazz.CONFIDENTIAL -> Events.ACCESS_CONFIDENTIAL
                else /* including Events.ACCESS_PRIVATE */ -> Events.ACCESS_PRIVATE
            })
    }

    private fun insertReminder(batch: CalendarBatchOperation, idxEvent: Int?, alarm: VAlarm) {
        val builder = CpoBuilder
            .newInsert(Reminders.CONTENT_URI.asSyncAdapter(calendar.account))
            .withEventId(Reminders.EVENT_ID, idxEvent)

        val method = when (alarm.action?.value?.uppercase(Locale.ROOT)) {
            Action.DISPLAY.value,
            Action.AUDIO.value -> Reminders.METHOD_ALERT    // will trigger an alarm on the Android device

            // Note: The calendar provider doesn't support saving specific attendees for email reminders.
            Action.EMAIL.value -> Reminders.METHOD_EMAIL

            else -> Reminders.METHOD_DEFAULT                // won't trigger an alarm on the Android device
        }

        val minutes = ICalendar.vAlarmToMin(alarm, event, false)?.second ?: Reminders.MINUTES_DEFAULT

        builder .withValue(Reminders.METHOD, method)
            .withValue(Reminders.MINUTES, minutes)
        batch += builder
    }

    private fun insertAttendee(batch: CalendarBatchOperation, idxEvent: Int?, attendee: Attendee, organizer: String) {
        val builder = CpoBuilder
            .newInsert(Attendees.CONTENT_URI.asSyncAdapter(calendar.account))
            .withEventId(Attendees.EVENT_ID, idxEvent)

        val member = attendee.calAddress
        if (member.scheme.equals("mailto", true))
        // attendee identified by email
            builder .withValue(Attendees.ATTENDEE_EMAIL, member.schemeSpecificPart)
        else {
            // attendee identified by other URI
            builder .withValue(Attendees.ATTENDEE_ID_NAMESPACE, member.scheme)
                .withValue(Attendees.ATTENDEE_IDENTITY, member.schemeSpecificPart)

            attendee.getParameter<Email>(Parameter.EMAIL)?.let { email ->
                builder.withValue(Attendees.ATTENDEE_EMAIL, email.value)
            }
        }

        attendee.getParameter<Cn>(Parameter.CN)?.let { cn ->
            builder.withValue(Attendees.ATTENDEE_NAME, cn.value)
        }

        // type/relation mapping is complex and thus outsourced to AttendeeMappings
        AttendeeMappings.iCalendarToAndroid(attendee, builder, organizer)

        val status = when(attendee.getParameter(Parameter.PARTSTAT) as? PartStat) {
            PartStat.ACCEPTED     -> Attendees.ATTENDEE_STATUS_ACCEPTED
            PartStat.DECLINED     -> Attendees.ATTENDEE_STATUS_DECLINED
            PartStat.TENTATIVE    -> Attendees.ATTENDEE_STATUS_TENTATIVE
            PartStat.DELEGATED    -> Attendees.ATTENDEE_STATUS_NONE
            else /* default: PartStat.NEEDS_ACTION */ -> Attendees.ATTENDEE_STATUS_INVITED
        }
        builder.withValue(Attendees.ATTENDEE_STATUS, status)
        batch += builder
    }

    private fun insertExtendedProperty(batch: CalendarBatchOperation, idxEvent: Int?, name: String, value: String) {
        val builder = CpoBuilder
            .newInsert(ExtendedProperties.CONTENT_URI.asSyncAdapter(calendar.account))
            .withEventId(ExtendedProperties.EVENT_ID, idxEvent)
            .withValue(ExtendedProperties.NAME, name)
            .withValue(ExtendedProperties.VALUE, value)
        batch += builder
    }

    private fun insertCategories(batch: CalendarBatchOperation, idxEvent: Int?) {
        val rawCategories = event.categories      // concatenate, separate by backslash
            .joinToString(CATEGORIES_SEPARATOR.toString()) { category ->
                // drop occurrences of CATEGORIES_SEPARATOR in category names
                category.filter { it != CATEGORIES_SEPARATOR }
            }
        insertExtendedProperty(batch, idxEvent, EXTNAME_CATEGORIES, rawCategories)
    }

    private fun insertUnknownProperty(batch: CalendarBatchOperation, idxEvent: Int?, property: Property) {
        if (property.value == null) {
            logger.warning("Ignoring unknown property with null value")
            return
        }
        if (property.value.length > UnknownProperty.MAX_UNKNOWN_PROPERTY_SIZE) {
            logger.warning("Ignoring unknown property with ${property.value.length} octets (too long)")
            return
        }

        insertExtendedProperty(batch, idxEvent, UnknownProperty.CONTENT_ITEM_TYPE, UnknownProperty.toJsonString(property))
    }

    /**
     * Retain classification other than PUBLIC and PRIVATE as unknown property so
     * that it can be reused when "server default" is selected.
     */
    private fun retainClassification() {
        event.classification?.let {
            if (it != Clazz.PUBLIC && it != Clazz.PRIVATE)
                event.unknownProperties += it
        }
    }


    private fun CpoBuilder.withEventId(column: String, idxEvent: Int?): CpoBuilder {
        if (idxEvent != null)
            withValueBackReference(column, idxEvent)
        else
            withValue(column, requireNotNull(id))
        return this
    }

}