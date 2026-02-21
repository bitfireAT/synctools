/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import androidx.annotation.VisibleForTesting
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.CalendarComponent

class CalendarUidSplitter<T: CalendarComponent> {

    /**
     * Splits iCalendar components by UID and classifies them as main events (without RECURRENCE-ID)
     * or exceptions (with RECURRENCE-ID).
     *
     * When there are multiple components with the same UID and RECURRENCE-ID, but different SEQUENCE,
     * this method keeps only the ones with the highest SEQUENCE.
     */
    fun associateByUid(calendar: Calendar, componentName: String): Map<String?, AssociatedComponents<T>> {
        TODO("ical4j 4.x")
    }

    /**
     * Keeps only the events with the highest SEQUENCE (per RECURRENCE-ID).
     *
     * @param events    list of VEVENTs with the same UID, but different RECURRENCE-IDs (may be `null`) and SEQUENCEs
     *
     * @return same as input list, but each RECURRENCE-ID occurs only with the highest SEQUENCE
     */
    @VisibleForTesting
    internal fun filterBySequence(events: List<T>): List<T> {
        TODO("ical4j 4.x")
    }

}