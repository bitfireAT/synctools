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
     * In a later step of refactoring, it should map to [net.fortuna.ical4j.model.component.VEvent].
     *
     * @param entity    event from content provider
     * @param to        data object where the mapped data shall be stored
     */
    fun process(entity: Entity, to: Event)

}