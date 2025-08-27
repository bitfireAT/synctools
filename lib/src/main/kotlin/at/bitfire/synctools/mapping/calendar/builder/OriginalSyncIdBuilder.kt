/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.component.VEvent

/**
 * Note: We never provide explicit reference to Events._ID because the calendar provider doesn't process it correctly.
 */
class OriginalSyncIdBuilder(
    private val syncId: String
): AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        if (from === main) {
            // main event, don't set ORIGINAL_ values
            to.entityValues.putNull(Events.ORIGINAL_SYNC_ID)

        } else {
            // exception
            to.entityValues.put(Events.ORIGINAL_SYNC_ID, syncId)
        }
        return true
    }

}