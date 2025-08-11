/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import net.fortuna.ical4j.model.component.VEvent

class SequenceBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        /*
        When an event row is built from a VEvent, the local sequence is set to 0 (not null) because we use
        "null" to detect when an event has been created locally.
        */
        to.entityValues.put(AndroidEvent2.COLUMN_SEQUENCE, from.sequence?.sequenceNo ?: 0)
        return true
    }

}