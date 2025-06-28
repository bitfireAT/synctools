/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapper.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.component.VEvent

object DtEndBuilder: FeatureBuilder {

    override fun intoEntity(syncProperties: AndroidEvent2Builder.SyncProperties, vEvent: VEvent, mainVEvent: VEvent, entity: Entity) {
        val endDate = vEvent.endDate
        if (endDate == null)
            return

        entity.entityValues.put(Events.DTEND, endDate.date.time)
    }

}