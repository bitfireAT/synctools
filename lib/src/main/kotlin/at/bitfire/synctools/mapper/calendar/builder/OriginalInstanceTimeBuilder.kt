/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapper.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.component.VEvent

object OriginalInstanceTimeBuilder: FeatureBuilder {

    override fun intoEntity(syncProperties: AndroidEvent2Builder.SyncProperties, vEvent: VEvent, mainVEvent: VEvent, entity: Entity) {
        val recurrenceId = vEvent.recurrenceId
        if (recurrenceId == null)
            return

        entity.entityValues.put(Events.ORIGINAL_INSTANCE_TIME, recurrenceId.date.time)
    }

}