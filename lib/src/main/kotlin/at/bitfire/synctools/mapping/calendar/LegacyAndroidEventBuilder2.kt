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
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.ICalendar
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Attendee
import java.util.Locale
import java.util.logging.Logger

/**
 * Legacy mapper from an [Event] data object to Android content provider data rows
 * (former "build..." methods).
 *
 * Important: To use recurrence exceptions, you MUST set _SYNC_ID and ORIGINAL_SYNC_ID
 * in populateEvent() / buildEvent. Setting _ID and ORIGINAL_ID is not sufficient.
 */
@Deprecated("Use AndroidEventBuilder instead", level = DeprecationLevel.ERROR)
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
        val row = buildEventRow(recurrence)

        val entity = Entity(row)
        val from = recurrence ?: event

        for (reminder in from.alarms)
            entity.addSubValue(Reminders.CONTENT_URI, buildReminder(reminder))

        for (attendee in from.attendees)
            entity.addSubValue(Attendees.CONTENT_URI, buildAttendee(attendee))

        // extended properties
        if (event.categories.isNotEmpty())
            entity.addSubValue(ExtendedProperties.CONTENT_URI, buildCategories(event.categories))

        event.url?.let { url ->
            entity.addSubValue(ExtendedProperties.CONTENT_URI, buildUrl(url.toString()))
        }

        for (unknownProperty in event.unknownProperties) {
            val values = buildUnknownProperty(unknownProperty)
            if (values != null)
                entity.addSubValue(ExtendedProperties.CONTENT_URI, values)
        }

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
        return contentValuesOf()

        /*
        // time fields

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

        // text fields
        row.put(Events.TITLE, from.summary)
        row.put(Events.EVENT_LOCATION, from.location)
        row.put(Events.DESCRIPTION, from.description)

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

        return row*/
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

    private fun buildCategories(categories: List<String>): ContentValues {
        // concatenate, separate by backslash
        val rawCategories = categories.joinToString(AndroidEvent2.CATEGORIES_SEPARATOR.toString()) { category ->
            // drop occurrences of CATEGORIES_SEPARATOR in category names
            category.filter { it != AndroidEvent2.CATEGORIES_SEPARATOR }
        }
        return contentValuesOf(
            ExtendedProperties.NAME to AndroidEvent2.EXTNAME_CATEGORIES,
            ExtendedProperties.VALUE to rawCategories
        )
    }


    private fun buildUnknownProperty(property: Property): ContentValues? {
        if (property.value == null) {
            logger.warning("Ignoring unknown property with null value")
            return null
        }
        if (property.value.length > UnknownProperty.MAX_UNKNOWN_PROPERTY_SIZE) {
            logger.warning("Ignoring unknown property with ${property.value.length} octets (too long)")
            return null
        }

        return contentValuesOf(
            ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
            ExtendedProperties.VALUE to UnknownProperty.toJsonString(property)
        )
    }

    private fun buildUrl(url: String) = contentValuesOf(
        ExtendedProperties.NAME to AndroidEvent2.EXTNAME_URL,
        ExtendedProperties.VALUE to url
    )

}