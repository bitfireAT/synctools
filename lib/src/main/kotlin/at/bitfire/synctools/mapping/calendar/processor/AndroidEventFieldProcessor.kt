/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import at.bitfire.ical4android.Event

interface AndroidEventFieldProcessor {

    /**
     * Takes specific data from an event (= event row plus data rows, taken from the content provider)
     * and maps it to the [Event] data class.
     *
     * If [from] references the same object as [main], this method is called for a main event (not an exception).
     * If [from] references another object as [main], this method is called for an exception (not a main event).
     *
     * So you can use (note the referential equality operator):
     *
     * ```
     * val isMainEvent = from === main  // or
     * val isException = from !== main
     * ```
     *
     * In a later step of refactoring, it should map to [net.fortuna.ical4j.model.component.VEvent].
     *
     * @param from      event from content provider
     * @param main      main event from content provider
     * @param to        destination object where the mapped data are stored
     *                  (no explicit `null` values needed for fields that are not present)
     */
    fun process(from: Entity, main: Entity, to: Event)

}