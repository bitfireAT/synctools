/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import net.fortuna.ical4j.model.component.VEvent

class SyncObjectBuilder(
    private val calendarId: Long,
    private val syncId: String?,
    private val eTag: String?,
    private val scheduleTag: String?,
    private val flags: Int
): AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = contentValuesOf(
            Events.CALENDAR_ID to calendarId,
            AndroidEvent2.COLUMN_FLAGS to flags,

            Events.DIRTY to 0,      // newly created event rows shall not be marked as dirty (relevant for update)
            Events.DELETED to 0     // see above
        )

        if (from === main) {
            values.put(Events._SYNC_ID, syncId)
            values.put(AndroidEvent2.COLUMN_ETAG, eTag)
            values.put(AndroidEvent2.COLUMN_SCHEDULE_TAG, scheduleTag)
        } else {
            values.putNull(Events._SYNC_ID)
            values.putNull(AndroidEvent2.COLUMN_ETAG)
            values.putNull(AndroidEvent2.COLUMN_SCHEDULE_TAG)
        }

        to.entityValues.putAll(values)
    }

}