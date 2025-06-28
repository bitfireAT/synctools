/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapper.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.AndroidEvent

object SyncPropertiesBuilder {

    fun intoEntity(properties: AndroidEvent2Builder.SyncProperties, entity: Entity) {
        entity.entityValues.put(Events.CALENDAR_ID, properties.calendarId)
        entity.entityValues.put(Events._SYNC_ID, properties.fileName)
        entity.entityValues.put(Events.DIRTY, if (properties.dirty) 1 else 0)
        entity.entityValues.put(Events.DELETED, if (properties.deleted) 1 else 0)
        entity.entityValues.put(AndroidEvent.COLUMN_FLAGS, properties.flags)
    }

}