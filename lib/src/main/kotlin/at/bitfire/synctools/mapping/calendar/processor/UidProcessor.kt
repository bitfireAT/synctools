/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Uid

class UidProcessor: AndroidEventFieldProcessor {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        // take from event row or Google Calendar extended property
        val uid = from.entityValues.getAsString(Events.UID_2445) ?:
            uidFromExtendedProperties(from.subValues)
        if (uid != null)
            to.properties += Uid(uid)
    }

    private fun uidFromExtendedProperties(rows: List<Entity.NamedContentValues>): String? {
        val uidRow = rows.firstOrNull {
            it.uri == ExtendedProperties.CONTENT_URI &&
            it.values.getAsString(ExtendedProperties.NAME) == EventsContract.EXTNAME_ICAL_UID
        }

        return uidRow?.values?.getAsString(ExtendedProperties.VALUE)
    }

}