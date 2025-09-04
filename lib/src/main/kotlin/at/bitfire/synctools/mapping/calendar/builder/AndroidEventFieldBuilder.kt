/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import net.fortuna.ical4j.model.component.VEvent

interface AndroidEventFieldBuilder {

    /**
     * Maps the given event into the provided [Entity].
     *
     * If [from] references the same object as [main], this method is called for a main event (not an exception).
     * If [from] references another object as [main], this method is called for an exception (not a main event).
     *
     * So you can use (note the referential equality operator):
     *
     * ```
     * val buildsMainEvent = from === main
     * ```
     *
     * @param from  event to map to
     * @param main  main event
     * @param to    destination object where built values are put into
     *
     * @return whether the resulting entity may be used; `false`: resulting entity should be discarded
     */
    fun build(from: VEvent, main: VEvent, to: Entity): Boolean

}