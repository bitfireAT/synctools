/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import androidx.annotation.VisibleForTesting
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.CalendarComponent

/**
 * Splits iCalendar components by UID.
 */
class CalendarUidSplitter<T: CalendarComponent> {

    fun associateByUid(calendar: Calendar, componentName: String): Map<String?, AssociatedComponents<T>> {
        // get all components of type T (for instance: all VEVENTs)
        val allComponents = calendar.getComponents<T>(componentName).toMutableList()

        // Note for VEVENT: UID is REQUIRED in RFC 5545 section 3.6.1, but optional in RFC 2445 section 4.6.1,
        // so it's possible that the Uid is null.
        val byUid: Map<String?, List<T>> = allComponents
            .groupBy { it.uid?.value }
            .mapValues { filterBySequence(it.value) }

        val result = mutableMapOf<String?, AssociatedComponents<T>>()
        for ((uid, vEventsWithUid) in byUid) {
            val mainVEvent = vEventsWithUid.lastOrNull { it.recurrenceId == null }
            val exceptions = vEventsWithUid.filter { it.recurrenceId != null }
            result[uid] = AssociatedComponents(mainVEvent, exceptions)
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
    @VisibleForTesting
    internal fun filterBySequence(events: List<T>): List<T> {
        // group by RECURRENCE-ID (may be null)
        val byRecurId = events.groupBy { it.recurrenceId?.value }.values

        // for every RECURRENCE-ID: keep only event with highest sequence
        val latest = byRecurId.map { sameUidAndRecurId ->
            sameUidAndRecurId.maxBy { it.sequence?.sequenceNo ?: 0 }
        }

        return latest
    }

}