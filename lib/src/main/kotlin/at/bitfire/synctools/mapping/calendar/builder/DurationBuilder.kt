/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.annotation.VisibleForTesting
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.abs
import at.bitfire.ical4android.util.TimeApiExtensions.toDuration
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toRfc5545Duration
import at.bitfire.synctools.icalendar.requireDtStart
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import java.time.Duration
import java.time.Period
import java.time.temporal.TemporalAmount

class DurationBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues

        /* The calendar provider requires
            - DTEND when the event is non-recurring, and
            - DURATION when the event is recurring.

        So we'll skip if this event is not a recurring main event (only main events can be recurring). */
        val rRules = from.getProperties<RRule>(Property.RRULE)
        val rDates = from.getProperties<RDate>(Property.RDATE)
        if (from !== main || (rRules.isEmpty() && rDates.isEmpty())) {
            values.putNull(Events.DURATION)
            return
        }

        val dtStart = from.requireDtStart()

        // calculate DURATION from DTEND - DTSTART, if necessary
        val calculatedDuration = from.duration?.duration
            ?: calculateFromDtEnd(dtStart, from.endDate)    // ignores DTEND < DTSTART

        // use default duration, if necessary
        val duration = calculatedDuration?.abs()    // always use positive duration
            ?: defaultDuration(DateUtils.isDate(dtStart))

        /* [RFC 5545 3.8.2.5]
        > When the "DURATION" property relates to a "DTSTART" property that is specified as a DATE value, then the
        >"DURATION" property MUST be specified as a "dur-day" or "dur-week" value.

        The calendar provider theoretically converts the DURATION of an all-day event to unit "days",
        so we wouldn't have to take care of that. However it expects seconds to be in "P<n>S" format,
        whereas we provide an RFC 5545-compliant "PT<n>S", which causes the provider to crash:
        https://github.com/bitfireAT/synctools/issues/144. So we must convert it ourselves to be on the safe side. */
        val alignedDuration = alignWithDtStart(duration, dtStart)

        /* TemporalAmount can have months and years, but the RFC 5545 value must only contain weeks, days and time.
        So we have to recalculate the months/years to days according to their position in the calendar.

        The calendar provider accepts every DURATION that `com.android.calendarcommon2.Duration` can parse,
        which is weeks, days, hours, minutes and seconds, like for the RFC 5545 duration. */
        val durationStr = alignedDuration.toRfc5545Duration(dtStart.date.toInstant())
        values.put(Events.DURATION, durationStr)
    }

    /**
     * Aligns the given temporal amount (taken from DURATION) to the VALUE-type (DATE-TIME/DATE) of DTSTART.
     *
     * @param amount    temporal amount that shall be aligned
     * @param dtStart   DTSTART to compare with
     *
     * @return Temporal amount that is
     *
     * - a [Period] (days/months/years that can't be represented by an exact number of seconds) when [dtStart] is a DATE, and
     * - a [Duration] (exact time that can be represented by an exact number of seconds) when [dtStart] is a DATE-TIME.
     */
    @VisibleForTesting
    internal fun alignWithDtStart(amount: TemporalAmount, dtStart: DtStart): TemporalAmount {
        if (DateUtils.isDate(dtStart)) {
            // DTSTART is DATE
            return if (amount is Duration) {
                // amount is Duration, change to Period of days instead
                Period.ofDays(amount.toDays().toInt())
            } else {
                // amount is already Period
                amount
            }

        } else {
            // DTSTART is DATE-TIME
            return if (amount is Period) {
                // amount is Period, change to Duration instead
                amount.toDuration(dtStart.date.toInstant())
            } else {
                // amount is already Duration
                amount
            }
        }
    }

    /**
     * Calculates the DURATION from DTEND - DTSTART, if possible.
     *
     * @param dtStart   start date/date-time
     * @param dtEnd     (optional) end date/date-time (ignored if not after [dtStart])
     *
     * @return temporal amount ([Period] or [Duration]) or `null` if no valid end time was available
     */
    @VisibleForTesting
    internal fun calculateFromDtEnd(dtStart: DtStart, dtEnd: DtEnd?): TemporalAmount? {
        if (dtEnd == null || dtEnd.date.toInstant() <= dtStart.date.toInstant())
            return null

        return if (DateUtils.isDateTime(dtStart) && DateUtils.isDateTime(dtEnd)) {
            // DTSTART and DTEND are DATE-TIME → calculate difference between timestamps
            val seconds = (dtEnd.date.time - dtStart.date.time) / 1000
            Duration.ofSeconds(seconds)
        } else {
            // Either DTSTART or DTEND or both are DATE:
            // - DTSTART and DTEND are DATE → DURATION is exact number of days (no time part)
            // - DTSTART is DATE, DTEND is DATE-TIME → only use date part of DTEND → DURATION is exact number of days (no time part)
            // - DTSTART is DATE-TIME, DTEND is DATE → amend DTEND with time of DTSTART → DURATION is exact number of days (no time part)
            val startDate = dtStart.date.toLocalDate()
            val endDate = dtEnd.date.toLocalDate()
            Period.between(startDate, endDate)
        }
    }

    private fun defaultDuration(allDay: Boolean): TemporalAmount =
        if (allDay)
            Period.ofDays(1)
        else
            Duration.ZERO

}