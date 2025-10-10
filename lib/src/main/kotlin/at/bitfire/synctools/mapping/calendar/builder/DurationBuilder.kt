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
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toRfc5545Duration
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Duration
import java.time.Period

class DurationBuilder: AndroidEntityBuilder {

    override fun build(from: Event, main: Event, to: Entity) {
        val values = to.entityValues

        /* The calendar provider requires
            - DTEND when the event is non-recurring, and
            - DURATION when the event is recurring.

        So we'll skip if this event is not a recurring main event (only main events can be recurring). */
        if (from !== main || (from.rRules.isEmpty() && from.rDates.isEmpty())) {
            values.putNull(Events.DURATION)
            return
        }

        val dtStart = from.requireDtStart()
        val duration = from.duration
            ?: calculateFromDtEnd(dtStart, from.dtEnd)
            ?: defaultDuration(DateUtils.isDate(dtStart))

        /* [RFC 5545 3.8.2.5]
        > When the "DURATION" property relates to a "DTSTART" property that is specified as a DATE value, then the
        >"DURATION" property MUST be specified as a "dur-day" or "dur-week" value.

        The calendar provider automatically converts the DURATION of an all-day event to unit: days,
        so we don't have to take care of that. */

        // TemporalAmount can have months and years, but the RFC 5545 must only contain weeks, days and time.
        // So we have to recalculate the months/years to days according to their position in the calendar.
        val durationStr = duration.duration.toRfc5545Duration(dtStart.date.toInstant())
        values.put(Events.DURATION, durationStr)
    }

    @VisibleForTesting
    internal fun calculateFromDtEnd(dtStart: DtStart, dtEnd: DtEnd?): Duration? {
        if (dtEnd == null)
            return null

        return if (DateUtils.isDateTime(dtStart) && DateUtils.isDateTime(dtEnd)) {
            // DTSTART and DTEND are DATE-TIME → calculate difference between timestamps
            val seconds = (dtEnd.date.time - dtStart.date.time) / 1000
            Duration(java.time.Duration.ofSeconds(seconds))
        } else {
            // Either DTSTART or DTEND or both are DATE:
            // - DTSTART and DTEND are DATE → DURATION is exact number of days (no time part)
            // - DTSTART is DATE, DTEND is DATE-TIME → only use date part of DTEND → DURATION is exact number of days (no time part)
            // - DTSTART is DATE-TIME, DTEND is DATE → amend DTEND with time of DTSTART → DURATION is exact number of days (no time part)
            val startDate = dtStart.date.toLocalDate()
            val endDate = dtEnd.date.toLocalDate()
            System.err.println("startDate = $startDate, endDate = $endDate")
            Duration(Period.between(startDate, endDate))
        }
    }

    private fun defaultDuration(allDay: Boolean): Duration =
        Duration(
            if (allDay)
                Period.ofDays(1)
            else
                java.time.Duration.ofSeconds(0)
        )

}