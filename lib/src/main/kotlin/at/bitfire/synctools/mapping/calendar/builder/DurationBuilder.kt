/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.util.TimeApiExtensions.toRfc5545Duration
import at.bitfire.synctools.icalendar.DurationCalculator
import at.bitfire.synctools.icalendar.isAllDay
import at.bitfire.synctools.icalendar.isRecurring
import at.bitfire.synctools.mapping.calendar.builder.DefaultValues.defaultDuration
import net.fortuna.ical4j.model.component.VEvent
import java.time.Duration
import java.time.Period
import java.time.temporal.TemporalAmount

class DurationBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        /*
        Events.DURATION is required by Android if event is recurring:
        https://developer.android.com/reference/android/provider/CalendarContract.Events#operations

        So if we need it (recurring event) and it's not available in the VEvent, we

        1. try to derive it from DTSTART/DTEND,
        2. otherwise use a default duration.
        */

        val recurring = from.isRecurring()
        if (!recurring) {
            // set DURATION to null for non-recurring events
            to.entityValues.putNull(Events.DURATION)
            return true
        }
        // recurring

        // get DURATION from VEvent
        val durationOrNull: TemporalAmount? = from.duration?.duration

        // if there's no VEvent DURATION, try to calculate it from DTSTART/DTEND
        val dtStartDate = from.startDate?.date ?: return false
        val allDay = dtStartDate.isAllDay()

        val dtEndDate = from.endDate?.date
        val calculatedDuration: TemporalAmount? =
            if (dtEndDate != null)
                DurationCalculator.calculateDuration(
                    dtStartDate = dtStartDate,
                    dtEndDate = dtEndDate
                )
            else
                durationOrNull

        // align DURATION according to DTSTART value type (DATE/DATE-TIME)
        val alignedDuration: TemporalAmount? = if (calculatedDuration != null && allDay && calculatedDuration is Duration) {
            // exact time period (Duration), rewrite to Period (days) whose actual length may vary
            Period.ofDays(calculatedDuration.toDays().toInt())
        } else
            calculatedDuration

        // fall back to default duration
        val duration = calculatedDuration ?: defaultDuration(allDay)

        // RFC 5545 doesn't allow years and months for DURATION, so we can't use duration.toString()
        to.entityValues.put(Events.DURATION, duration.toRfc5545Duration(dtStartDate.toInstant()))

        return true
    }

}