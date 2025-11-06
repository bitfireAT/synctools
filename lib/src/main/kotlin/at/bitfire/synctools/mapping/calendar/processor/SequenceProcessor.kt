/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Sequence

class SequenceProcessor: AndroidEventFieldProcessor {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val seqNo = from.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE)
        if (seqNo != null && seqNo > 0)
            to.properties += Sequence(seqNo)
    }

}