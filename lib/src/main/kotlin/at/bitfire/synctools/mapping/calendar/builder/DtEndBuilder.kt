/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.DurationCalculator
import at.bitfire.synctools.icalendar.alignAllDay
import at.bitfire.synctools.icalendar.isAllDay
import at.bitfire.synctools.icalendar.isRecurring
import at.bitfire.synctools.mapping.calendar.builder.DefaultValues.defaultDuration
import net.fortuna.ical4j.model.component.VEvent

class DtEndBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        /*
        Events.DTEND is required by Android if event is non-recurring:
        https://developer.android.com/reference/android/provider/CalendarContract.Events#operations

        So if we need it (non-recurring event) and it's not available in the VEvent, we try to derive
        it from DTSTART / DURATION (using a default duration as fallback).
        */

        val recurring = from.isRecurring()
        if (recurring) {
            // set DTEND to null for recurring events
            to.entityValues.putNull(Events.DTEND)
            return true
        }
        // non-recurring

        // we need a start date (discard result if DTSTART is not present)
        val dtStartDate = from.startDate?.date ?: return false

        val dtEndDate = from.endDate?.date
        val calculatedEndDate = dtEndDate?.alignAllDay(dtStartDate) ?: DurationCalculator.calculateEndDate(
            dtStartDate = dtStartDate,
            duration = from.duration?.duration ?: defaultDuration(dtStartDate.isAllDay())
        )

        /*
        ical4j uses GMT for the timestamp of all-day events because we have configured it that way
        (net.fortuna.ical4j.timezone.date.floating = false).

        However, this should be verified by a test → DtStartBuilderTest.
        */

        to.entityValues.put(Events.DTEND, calculatedEndDate.time)
        return true
    }

}