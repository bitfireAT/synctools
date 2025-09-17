/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Colors
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.ICalendar
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
import at.bitfire.synctools.mapping.calendar.builder.AndroidEventFieldBuilder
import at.bitfire.synctools.mapping.calendar.builder.CategoriesBuilder
import at.bitfire.synctools.mapping.calendar.builder.DescriptionBuilder
import at.bitfire.synctools.mapping.calendar.builder.LocationBuilder
import at.bitfire.synctools.mapping.calendar.builder.RetainedClassificationBuilder
import at.bitfire.synctools.mapping.calendar.builder.TitleBuilder
import at.bitfire.synctools.mapping.calendar.builder.UnknownPropertiesBuilder
import at.bitfire.synctools.mapping.calendar.builder.UrlBuilder
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
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
 *
 * Important: To use recurrence exceptions, you MUST set _SYNC_ID and ORIGINAL_SYNC_ID
 * in populateEvent() / buildEvent. Setting _ID and ORIGINAL_ID is not sufficient.
 *
 * Note: "Legacy" will be removed from the class name as soon as the [Event] dependency is
 * replaced by [at.bitfire.synctools.icalendar.AssociatedEvents].
 */
class LegacyAndroidEventBuilder2(
    private val calendar: AndroidCalendar,
    private val event: Event,

    // AndroidEvent-level fields
    private val id: Long?,
    private val syncId: String?,
    private val eTag: String?,
    private val scheduleTag: String?,
    private val flags: Int
) {

    private val fieldBuilders: Array<AndroidEventFieldBuilder> = arrayOf(
        CategoriesBuilder(),
        DescriptionBuilder(),
        LocationBuilder(),
        RetainedClassificationBuilder(),
        TitleBuilder(),
        UnknownPropertiesBuilder(),
        UrlBuilder()
    )

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }


    fun build() =
        EventAndExceptions(
            main = buildEvent(null),
            exceptions = event.exceptions.map { exception ->
                buildEvent(exception)
            }
        )

    fun buildEvent(recurrence: Event?): Entity {
        // build row from legacy builders
        val row = buildEventRow(recurrence)

        val entity = Entity(row)
        val from = recurrence ?: event

        for (reminder in from.alarms)
            entity.addSubValue(Reminders.CONTENT_URI, buildReminder(reminder))

        for (attendee in from.attendees)
            entity.addSubValue(Attendees.CONTENT_URI, buildAttendee(attendee))

        // additionally apply new builders
        for (builder in fieldBuilders)
            builder.build(from = from, main = event, to = entity)

        return entity
    }

    /**
     * Builds an Android [Events] row for a given event. Takes information from
     *
     * - `this` object: fields like calendar ID, sync ID, eTag etc,
     * - the [event]: all other fields.
     *
     * @param recurrence   event to be used as data source; *null*: use this AndroidEvent's main [event] as source
     */
    private fun buildEventRow(recurrence: Event?): ContentValues {
        // start with object-level (AndroidEvent) fields
        val row = contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DIRTY to 0,      // newly created event rows shall not be marked as dirty (relevant for update)
            Events.DELETED to 0,    // see above
            AndroidEvent2.COLUMN_FLAGS to flags
        )

        val isException = recurrence != null
        val from = recurrence ?: event

        val dtStart = from.dtStart ?: throw InvalidLocalResourceException("Events must have DTSTART")
        val allDay = DateUtils.isDate(dtStart)

        // make sure that time zone is supported by Android
        AndroidTimeUtils.androidifyTimeZone(dtStart, tzRegistry)

        val recurring = from.rRules.isNotEmpty() || from.rDates.isNotEmpty()

        /* [CalendarContract.Events SDK documentation]
           When inserting a new event the following fields must be included:
           - dtstart
           - dtend if the event is non-recurring
           - duration if the event is recurring
           - rrule or rdate if the event is recurring
           - eventTimezone
           - a calendar_id */

        if (!isException) {
            // main event
            row.put(Events._SYNC_ID, syncId)
            row.put(AndroidEvent2.COLUMN_ETAG, eTag)
            row.put(AndroidEvent2.COLUMN_SCHEDULE_TAG, scheduleTag)
        } else {
            // exception
            row.put(Events.ORIGINAL_SYNC_ID, syncId)
            row.put(Events.ORIGINAL_ALL_DAY, if (DateUtils.isDate(event.dtStart)) 1 else 0)

            var recurrenceDate = from.recurrenceId!!.date
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
            row.put(Events.ORIGINAL_INSTANCE_TIME, recurrenceDate.time)
        }

        // UID, sequence
        row.put(Events.UID_2445, from.uid)
        row.put(AndroidEvent2.COLUMN_SEQUENCE, from.sequence)

        // time fields
        row.put(Events.DTSTART, dtStart.date.time)
        row.put(Events.ALL_DAY, if (allDay) 1 else 0)
        row.put(Events.EVENT_TIMEZONE, AndroidTimeUtils.storageTzId(dtStart))

        var dtEnd = from.dtEnd
        AndroidTimeUtils.androidifyTimeZone(dtEnd, tzRegistry)

        var duration =
            if (dtEnd == null)
                from.duration?.duration
            else
                null
        if (allDay && duration is Duration)
            duration = Period.ofDays(duration.toDays().toInt())

        if (recurring && !isException) {
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
            row.put(Events.DURATION, duration?.toRfc5545Duration(dtStart.date.toInstant()))
            row.putNull(Events.DTEND)

            // add RRULEs
            if (from.rRules.isNotEmpty())
                row.put(Events.RRULE, from.rRules.joinToString(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR) { it.value })
            else
                row.putNull(Events.RRULE)

            if (from.rDates.isNotEmpty()) {
                // ignore RDATEs when there's also an infinite RRULE [https://issuetracker.google.com/issues/216374004]
                val infiniteRrule = from.rRules.any { rRule ->
                    rRule.recur.count == -1 &&  // no COUNT AND
                            rRule.recur.until == null   // no UNTIL
                }

                if (infiniteRrule)
                    logger.warning("Android can't handle infinite RRULE + RDATE [https://issuetracker.google.com/issues/216374004]; ignoring RDATE(s)")
                else {
                    for (rDate in from.rDates)
                        AndroidTimeUtils.androidifyTimeZone(rDate)

                    // Calendar provider drops DTSTART instance when using RDATE [https://code.google.com/p/android/issues/detail?id=171292]
                    val listWithDtStart = DateList()
                    listWithDtStart.add(dtStart.date)
                    from.rDates.addFirst(RDate(listWithDtStart))

                    row.put(Events.RDATE, AndroidTimeUtils.recurrenceSetsToAndroidString(from.rDates, dtStart.date))
                }
            } else
                row.putNull(Events.RDATE)

            if (from.exRules.isNotEmpty())
                row.put(Events.EXRULE, from.exRules.joinToString(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR) { it.value })
            else
                row.putNull(Events.EXRULE)

            if (from.exDates.isNotEmpty()) {
                for (exDate in from.exDates)
                    AndroidTimeUtils.androidifyTimeZone(exDate)
                row.put(Events.EXDATE, AndroidTimeUtils.recurrenceSetsToAndroidString(from.exDates, dtStart.date))
            } else
                row.putNull(Events.EXDATE)

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

            AndroidTimeUtils.androidifyTimeZone(dtEnd, tzRegistry)
            row.put(Events.DTEND, dtEnd.date.time)
            row.put(Events.EVENT_END_TIMEZONE, AndroidTimeUtils.storageTzId(dtEnd))
            row.putNull(Events.DURATION)
            row.putNull(Events.RRULE)
            row.putNull(Events.RDATE)
            row.putNull(Events.EXRULE)
            row.putNull(Events.EXDATE)
        }

        // color
        val color = from.color
        if (color != null) {
            // set event color (if it's available for this account)
            calendar.client.query(Colors.CONTENT_URI.asSyncAdapter(calendar.account), arrayOf(Colors.COLOR_KEY),
                "${Colors.COLOR_KEY}=? AND ${Colors.COLOR_TYPE}=${Colors.TYPE_EVENT}", arrayOf(color.name), null)?.use { cursor ->
                if (cursor.moveToNext())
                    row.put(Events.EVENT_COLOR_KEY, color.name)
                else
                    logger.fine("Ignoring event color \"${color.name}\" (not available for this account)")
            }
        } else {
            // reset color index and value
            row.putNull(Events.EVENT_COLOR_KEY)
            row.putNull(Events.EVENT_COLOR)
        }

        // scheduling
        val groupScheduled = from.attendees.isNotEmpty()
        if (groupScheduled) {
            row.put(Events.HAS_ATTENDEE_DATA, 1)
            row.put(Events.ORGANIZER, from.organizer?.let { organizer ->
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

        } else { /* !groupScheduled */
            row.put(Events.HAS_ATTENDEE_DATA, 0)
            row.put(Events.ORGANIZER, calendar.ownerAccount)
        }

        // Attention: don't update event with STATUS != null to STATUS = null (causes calendar provider operation to fail)!
        // In this case, the whole event must be deleted and inserted again.
        if (/* insert, not an update */ id == null || /* update, but we're not updating to null */ from.status != null)
            row.put(Events.STATUS, when (from.status) {
                null /* not possible by if statement */ -> null
                Status.VEVENT_CONFIRMED -> Events.STATUS_CONFIRMED
                Status.VEVENT_CANCELLED -> Events.STATUS_CANCELED
                else -> Events.STATUS_TENTATIVE
            })

        row.put(Events.AVAILABILITY, if (from.opaque) Events.AVAILABILITY_BUSY else Events.AVAILABILITY_FREE)
        row.put(Events.ACCESS_LEVEL, when (from.classification) {
                null -> Events.ACCESS_DEFAULT
                Clazz.PUBLIC -> Events.ACCESS_PUBLIC
                Clazz.CONFIDENTIAL -> Events.ACCESS_CONFIDENTIAL
                else /* including Events.ACCESS_PRIVATE */ -> Events.ACCESS_PRIVATE
            })

        return row
    }

    private fun buildAttendee(attendee: Attendee): ContentValues {
        val values = ContentValues()
        val organizer = event.organizerEmail ?:
            /* no ORGANIZER, use current account owner as ORGANIZER */
            calendar.ownerAccount ?: calendar.account.name

        val member = attendee.calAddress
        if (member.scheme.equals("mailto", true))   // attendee identified by email
            values.put(Attendees.ATTENDEE_EMAIL, member.schemeSpecificPart)
        else {
            // attendee identified by other URI
            values.put(Attendees.ATTENDEE_ID_NAMESPACE, member.scheme)
            values.put(Attendees.ATTENDEE_IDENTITY, member.schemeSpecificPart)

            attendee.getParameter<Email>(Parameter.EMAIL)?.let { email ->
                values.put(Attendees.ATTENDEE_EMAIL, email.value)
            }
        }

        attendee.getParameter<Cn>(Parameter.CN)?.let { cn ->
            values.put(Attendees.ATTENDEE_NAME, cn.value)
        }

        // type/relation mapping is complex and thus outsourced to AttendeeMappings
        AttendeeMappings.iCalendarToAndroid(attendee, values, organizer)

        val status = when(attendee.getParameter(Parameter.PARTSTAT) as? PartStat) {
            PartStat.ACCEPTED     -> Attendees.ATTENDEE_STATUS_ACCEPTED
            PartStat.DECLINED     -> Attendees.ATTENDEE_STATUS_DECLINED
            PartStat.TENTATIVE    -> Attendees.ATTENDEE_STATUS_TENTATIVE
            PartStat.DELEGATED    -> Attendees.ATTENDEE_STATUS_NONE
            else /* default: PartStat.NEEDS_ACTION */ -> Attendees.ATTENDEE_STATUS_INVITED
        }
        values.put(Attendees.ATTENDEE_STATUS, status)

        return values
    }

    private fun buildReminder(alarm: VAlarm): ContentValues {
        val method = when (alarm.action?.value?.uppercase(Locale.ROOT)) {
            Action.DISPLAY.value,
            Action.AUDIO.value -> Reminders.METHOD_ALERT    // will trigger an alarm on the Android device

            // Note: The calendar provider doesn't support saving specific attendees for email reminders.
            Action.EMAIL.value -> Reminders.METHOD_EMAIL

            else -> Reminders.METHOD_DEFAULT                // won't trigger an alarm on the Android device
        }

        val minutes = ICalendar.vAlarmToMin(alarm, event, false)?.second ?: Reminders.MINUTES_DEFAULT

        return contentValuesOf(
            Reminders.METHOD to method,
            Reminders.MINUTES to minutes
        )
    }

}