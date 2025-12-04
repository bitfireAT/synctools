/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import androidx.annotation.VisibleForTesting
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.abs
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
import java.time.temporal.TemporalAmount

/**
 * Common methods mapping logic
 */
object MappingUtil {

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
    fun dtEndFromDefault(dtStart: DtStart): DtEnd =
        if (DateUtils.isDate(dtStart)) {
            // DATE → one day duration
            val endDate: LocalDate = dtStart.date.toLocalDate().plusDays(1)
            DtEnd(endDate.toIcal4jDate())
        } else {
            // DATE-TIME → same as DTSTART to indicate there was no DTEND set
            DtEnd(dtStart.value, dtStart.timeZone)
        }

    /**
     * Calculates the DTEND from DTSTART + DURATION, if possible.
     *
     * @param dtStart   start date/date-time
     * @param duration  (optional) duration
     *
     * @return end date/date-time (same value type as [dtStart]) or `null` if [duration] was not given
     */
    @VisibleForTesting
    internal fun dtEndFromDuration(dtStart: DtStart, duration: TemporalAmount?): DtEnd? {
        if (duration == null)
            return null

        val dur = duration.abs()   // always take positive temporal amount

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

}