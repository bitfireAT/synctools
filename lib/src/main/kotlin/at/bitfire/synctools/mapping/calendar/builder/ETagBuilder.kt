/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import net.fortuna.ical4j.model.component.VEvent

class ETagBuilder(
    private val eTag: String?,
    private val scheduleTag: String?
): AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues
        if (from === main) {
            // only set ETag and Schedule-Tag for main event
            values.put(AndroidEvent2.COLUMN_ETAG, eTag)
            values.put(AndroidEvent2.COLUMN_SCHEDULE_TAG, scheduleTag)
        } else {
            values.putNull(AndroidEvent2.COLUMN_ETAG)
            values.putNull(AndroidEvent2.COLUMN_SCHEDULE_TAG)
        }
    }

}