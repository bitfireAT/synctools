/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.annotation.VisibleForTesting
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

class EndTimeBuilder: AndroidEntityBuilder {

    override fun build(from: Event, main: Event, to: Entity) {
        val values = to.entityValues

        /* The calendar provider requires
           - DTEND when the event is non-recurring, and
           - DURATION when the event is recurring.

        So we'll skip if this event is a recurring main event (only main events can be recurring). */
        if (from === main && (from.rRules.isNotEmpty() || from.rDates.isNotEmpty())) {
            values.putNull(Events.DTEND)
            return
        }

        val dtStart = from.requireDtStart()
        val dtEnd = from.dtEnd?.let { alignWithDtStart(it, dtStart = dtStart) }
            ?: calculateFromDuration(dtStart, from.duration)
            ?: calculateFromDefault(dtStart)

        // end time: UNIX timestamp
        values.put(Events.DTEND, dtEnd.date.time)

        // end time: timezone ID
        if (DateUtils.isDateTime(dtEnd)) {
            /* DTEND is a DATE-TIME. This can be:
               - date/time with timezone ID ("DTEND;TZID=Europe/Vienna:20251006T155623")
               - UTC ("DTEND:20251006T155623Z")
               - floating time ("DTEND:20251006T155623") */

            if (dtEnd.isUtc) {
                // UTC
                values.put(Events.EVENT_END_TIMEZONE, AndroidTimeUtils.TZID_UTC)

            } else if (dtEnd.timeZone != null) {
                // timezone reference – make sure that time zone is known by Android
                values.put(Events.EVENT_END_TIMEZONE, DateUtils.findAndroidTimezoneID(dtEnd.timeZone.id))

            } else {
                // floating time, use system default
                values.put(Events.EVENT_END_TIMEZONE, ZoneId.systemDefault().id)
            }

        } else {
            // DTEND is a DATE
            values.put(Events.EVENT_END_TIMEZONE, AndroidTimeUtils.TZID_UTC)
        }
    }


    /**
     * Aligns the given DTEND to the VALUE-type (DATE-TIME/DATE) of DTSTART.
     *
     * @param dtEnd     DTEND to be aligned
     * @param dtStart   DTSTART to compare with
     *
     * @return
     *
     * - DTEND and DTSTART are both either DATE or DATE-TIME → original DTEND
     * - DTEND is DATE, DTSTART is DATE-TIME → DTEND is amended to DATE-TIME with time and timezone from DTSTART
     * - DTEND is DATE-TIME, DTSTART is DATE → DTEND is reduced to its date component
     */
    @VisibleForTesting
    internal fun alignWithDtStart(dtEnd: DtEnd, dtStart: DtStart): DtEnd {
        if (DateUtils.isDate(dtEnd)) {
            // DTEND is DATE
            if (DateUtils.isDate(dtStart)) {
                // DTEND is DATE, DTSTART is DATE
                return dtEnd
            } else {
                // DTEND is DATE, DTSTART is DATE-TIME → amend with time and timezone
                TODO()
            }
        } else {
            // DTEND is DATE-TIME
            if (DateUtils.isDate(dtStart)) {
                // DTEND is DATE-TIME, DTSTART is DATE → only take date part
                TODO()
            } else {
                // DTEND is DATE-TIME, DTSTART is DATE-TIME
                return dtEnd
            }
        }
    }

    @VisibleForTesting
    internal fun calculateFromDuration(dtStart: DtStart, duration: net.fortuna.ical4j.model.property.Duration?): DtEnd? {
        if (duration == null)
            return null

        val dur = duration.duration
        return if (DateUtils.isDate(dtStart)) {
            // DTSTART is DATE
            if (dur is Period) {
                // date-based amount of time ("4 days")
                val result = dtStart.date.toLocalDate() + dur
                DtEnd(result.toIcal4jDate())
            } else if (dur is Duration) {
                // time-based amount of time ("34 minutes")
                val days = dur.toDays()
                val result = dtStart.date.toLocalDate() + Period.ofDays(days.toInt())
                DtEnd(result.toIcal4jDate())
            } else
                throw IllegalStateException()   // TemporalAmount neither Period nor Duration

        } else {
            // DTSTART is DATE-TIME
            // We can add both date-based (Period) and time-based (Duration) amounts of time to an exact date/time.
            val result = (dtStart.date as DateTime).toZonedDateTime() + dur
            DtEnd(result.toIcal4jDateTime())
        }
    }

    /**
     * Chooses a DTEND value for the content provider when the iCalendar doesn't have a DTEND.
     *
     * RFC 5545 says the following about empty DTEND values:
     *
     * > For cases where a "VEVENT" calendar component specifies a "DTSTART" property with a DATE value type but no
     * > "DTEND" nor "DURATION" property, the event's duration is taken to be one day. For cases where a "VEVENT" calendar
     * > component specifies a "DTSTART" property with a DATE-TIME value type but no "DTEND" property, the event
     * > ends on the same calendar date and time of day specified by the "DTSTART" property.
     *
     * In iCalendar, `DTEND` is non-inclusive at must be at a later time than `DTEND`. However in Android we can use
     * the same value for both the `DTSTART` and the `DTEND` field, and so we use this to indicate a missing DTEND in
     * the original iCalendar.
     *
     * @param dtStart   start time to calculate end time from
     * @return End time to use for content provider:
     *
     * - when [dtStart] is a `DATE`: [dtStart] + 1 day
     * - when [dtStart] is a `DATE-TIME`: [dtStart]
     */
    @VisibleForTesting
    internal fun calculateFromDefault(dtStart: DtStart): DtEnd =
        if (DateUtils.isDate(dtStart)) {
            // DATE → one day duration
            val endDate: LocalDate = dtStart.date.toLocalDate().plusDays(1)
            DtEnd(endDate.toIcal4jDate())
        } else {
            // DATE-TIME → same as DTSTART to indicate there was no DTEND set
            DtEnd(dtStart.value, dtStart.timeZone)
        }

}