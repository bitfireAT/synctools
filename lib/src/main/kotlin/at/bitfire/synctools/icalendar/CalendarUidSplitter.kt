/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VEvent

/**
 * Splits iCalendar components by UID.
 */
class CalendarUidSplitter(
    private val calendar: Calendar
) {

    fun associateEvents(): Map<String?, AssociatedVEvents> {
        val allVEvents = calendar.getComponents<VEvent>(Component.VEVENT).toMutableList()

        // Note: UID is REQUIRED for events in RFC 5545 section 3.6.1, but optional in RFC 2445 section 4.6.1,
        // so it's possible that the Uid is null.
        val byUid: Map<String?, List<VEvent>> = allVEvents
            .groupBy { it.uid?.value }
            .mapValues { filterBySequence(it.value) }

        val result = mutableMapOf<String?, AssociatedVEvents>()
        for ((uid, vEventsWithUid) in byUid) {
            val mainVEvent = vEventsWithUid.last { it.recurrenceId == null }
            val exceptions = vEventsWithUid.filter { it.recurrenceId != null }
            result[uid] = AssociatedVEvents(mainVEvent, exceptions)
        }

        return result
    }

    /**
     * Keeps only the events with the highest SEQUENCE (per RECURRENCE-ID).
     *
     * @param events    list of VEVENTs with the same UID, but different RECURRENCE-IDs and SEQUENCEs
     *
     * @return same as input list, but each RECURRENCE-ID event only with the highest SEQUENCE
     */
    private fun filterBySequence(events: List<VEvent>): List<VEvent> {
        // group by RECURRENCE-ID (may be null)
        val byRecurId = events.groupBy { it.recurrenceId?.value }.values

        // for every RECURRENCE-ID: keep only event with highest sequence
        val latest = byRecurId.map { sameUidAndRecurId ->
            sameUidAndRecurId.maxBy { it.sequence?.sequenceNo ?: 0 }
        }

        return latest
    }

}