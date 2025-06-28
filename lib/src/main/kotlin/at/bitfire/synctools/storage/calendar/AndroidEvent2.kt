/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.content.Entity
import android.provider.CalendarContract.Events

/**
 * Represents an event in the Android calendar provider.
 */
data class AndroidEvent2(
    val mainEvent: Entity,
    val exceptions: List<Entity>
) {

    val id: Long?
        get() = mainEvent.entityValues?.getAsLong(Events._ID)

    val syncId: String?
        get() = mainEvent.entityValues.getAsString(Events._SYNC_ID)

    val eTag: String?
        get() = mainEvent.entityValues.getAsString(COLUMN_ETAG)

    val flags: Int
        get() = mainEvent.entityValues.getAsInteger(COLUMN_FLAGS) ?: 0


    companion object {

        /**
         * Custom sync column to store the last known ETag of an event.
         */
        const val COLUMN_ETAG = Events.SYNC_DATA1

        /**
         * Custom sync column to store sync flags of an event.
         */
        const val COLUMN_FLAGS = Events.SYNC_DATA2

        const val FLAGS_REMOTELY_PRESENT = 1

    }

}