/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapper.calendar

import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Uid

/**
 * Splits ICalendar components by UID.
 */
class CalendarUidSplitter(
    private val calendar: Calendar
) {

    fun associateEvents(): Map<Uid?, AssociatedVEvents> {
        val vEvents = calendar.getComponents<VEvent>().toMutableList()

        // Note: UID is REQUIRED in RFC 5545 section 3.6.1, but optional in RFC 2445 section 4.6.1,
        // so it's possible that the Uid is null.
        val byUid: Map<Uid?, List<VEvent>> = vEvents.groupBy { it.uid }

        // TODO reduce to highest SEQUENCE

        val result = mutableMapOf<Uid?, AssociatedVEvents>()
        for ((uid, vEventsWithUid) in byUid) {
            val mainVEvent = vEventsWithUid.last { it.recurrenceId == null }
            val exceptions = vEventsWithUid.filter { it.recurrenceId != null }
            result[uid] = AssociatedVEvents(mainVEvent, exceptions)
        }

        return result
    }

}