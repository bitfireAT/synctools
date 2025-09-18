/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toRfc5545Duration
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import at.bitfire.synctools.exception.InvalidLocalResourceException
import at.bitfire.synctools.mapping.calendar.builder.AccessLevelBuilder
import at.bitfire.synctools.mapping.calendar.builder.AllDayBuilder
import at.bitfire.synctools.mapping.calendar.builder.AndroidEntityBuilder
import at.bitfire.synctools.mapping.calendar.builder.AttendeesBuilder
import at.bitfire.synctools.mapping.calendar.builder.AvailabilityBuilder
import at.bitfire.synctools.mapping.calendar.builder.CalendarIdBuilder
import at.bitfire.synctools.mapping.calendar.builder.CategoriesBuilder
import at.bitfire.synctools.mapping.calendar.builder.ColorBuilder
import at.bitfire.synctools.mapping.calendar.builder.DescriptionBuilder
import at.bitfire.synctools.mapping.calendar.builder.DirtyAndDeletedBuilder
import at.bitfire.synctools.mapping.calendar.builder.ETagBuilder
import at.bitfire.synctools.mapping.calendar.builder.LocationBuilder
import at.bitfire.synctools.mapping.calendar.builder.OrganizerBuilder
import at.bitfire.synctools.mapping.calendar.builder.OriginalInstanceTimeBuilder
import at.bitfire.synctools.mapping.calendar.builder.RecurrenceFieldsBuilder
import at.bitfire.synctools.mapping.calendar.builder.RemindersBuilder
import at.bitfire.synctools.mapping.calendar.builder.RetainedClassificationBuilder
import at.bitfire.synctools.mapping.calendar.builder.SequenceBuilder
import at.bitfire.synctools.mapping.calendar.builder.StatusBuilder
import at.bitfire.synctools.mapping.calendar.builder.SyncFlagsBuilder
import at.bitfire.synctools.mapping.calendar.builder.SyncIdBuilder
import at.bitfire.synctools.mapping.calendar.builder.TitleBuilder
import at.bitfire.synctools.mapping.calendar.builder.UidBuilder
import at.bitfire.synctools.mapping.calendar.builder.UnknownPropertiesBuilder
import at.bitfire.synctools.mapping.calendar.builder.UrlBuilder
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.DtEnd
import java.time.Duration
import java.time.Period

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
    calendar: AndroidCalendar,
    private val event: Event,

    // AndroidEvent-level fields
    syncId: String?,
    eTag: String?,
    scheduleTag: String?,
    flags: Int
) {

    private val fieldBuilders: Array<AndroidEntityBuilder> = arrayOf(
        // sync columns (as defined in CalendarContract.EventsColumns)
        SyncIdBuilder(syncId),
        DirtyAndDeletedBuilder(),
        ETagBuilder(eTag = eTag, scheduleTag = scheduleTag),
        SyncFlagsBuilder(flags),
        SequenceBuilder(),
        // event columns
        CalendarIdBuilder(calendar.id),
        TitleBuilder(),
        DescriptionBuilder(),
        LocationBuilder(),
        ColorBuilder(calendar),
        StatusBuilder(),
        AllDayBuilder(),
        AccessLevelBuilder(),
        AvailabilityBuilder(),
        RecurrenceFieldsBuilder(),
        OriginalInstanceTimeBuilder(),
        OrganizerBuilder(calendar.ownerAccount),
        UidBuilder(),
        // sub-rows (alphabetically, by class name)
        AttendeesBuilder(calendar),
        CategoriesBuilder(),
        RemindersBuilder(),
        RetainedClassificationBuilder(),
        UnknownPropertiesBuilder(),
        UrlBuilder()
    )

    private val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }


    fun build() =
        EventAndExceptions(
            main = buildEvent(null),
            exceptions = event.exceptions.map { exception ->
                buildEvent(exception)
            }
        )

    fun buildEvent(recurrence: Event?): Entity {
        // build main row from legacy builders
        val row = buildEventRow(recurrence)

        // additionally apply new builders
        val entity = Entity(row)
        for (builder in fieldBuilders)
            builder.build(from = recurrence ?: event, main = event, to = entity)

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
        val row = ContentValues()

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

        // time fields
        row.put(Events.DTSTART, dtStart.date.time)
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
        }

        return row
    }

}