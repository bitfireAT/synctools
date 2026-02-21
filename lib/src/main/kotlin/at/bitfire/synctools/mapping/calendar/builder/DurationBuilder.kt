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
        TODO()
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
    internal fun alignWithDtStart(amount: TemporalAmount, dtStart: DtStart<*>): TemporalAmount {
        TODO()
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
    internal fun calculateFromDtEnd(dtStart: DtStart<*>, dtEnd: DtEnd<*>?): TemporalAmount? {
        TODO("ical4j 4.x")
    }

    private fun defaultDuration(allDay: Boolean): TemporalAmount =
        if (allDay)
            Period.ofDays(1)
        else
            Duration.ZERO

}