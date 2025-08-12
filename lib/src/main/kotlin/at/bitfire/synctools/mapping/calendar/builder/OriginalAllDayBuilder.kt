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

class OriginalAllDayBuilder(): AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        if (from === main) {
            // main event, don't set ORIGINAL_ values
            to.entityValues.putNull(Events.ORIGINAL_ALL_DAY)

        } else {
            // exception

            // require main event DTSTART (discard resulting entity if it's not available)
            val mainDtStart = main.startDate?.date ?: return false

            to.entityValues.put(Events.ORIGINAL_ALL_DAY, if (mainDtStart.isAllDay()) 1 else 0)
        }
        return true
    }

}