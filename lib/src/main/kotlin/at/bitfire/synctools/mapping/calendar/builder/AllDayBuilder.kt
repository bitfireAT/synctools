/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.isAllDay
import net.fortuna.ical4j.model.component.VEvent

class AllDayBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        val dtStartDate = from.startDate?.date

        to.entityValues.put(
            Events.ALL_DAY,
            if (dtStartDate != null && dtStartDate.isAllDay()) 1 else 0
        )

        return true
    }

}