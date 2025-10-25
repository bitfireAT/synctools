/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import at.bitfire.ical4android.Event
import at.bitfire.synctools.storage.calendar.AndroidEvent2

class SequenceBuilder: AndroidEntityBuilder {

    override fun build(from: Event, main: Event, to: Entity) {
        /* When we build the SEQUENCE column from a real event, we set the sequence to 0 (not null), so that we
        can distinguish it from events which have been created locally and have never been uploaded yet. */
        to.entityValues.put(AndroidEvent2.COLUMN_SEQUENCE, from.sequence ?: 0)
    }

}