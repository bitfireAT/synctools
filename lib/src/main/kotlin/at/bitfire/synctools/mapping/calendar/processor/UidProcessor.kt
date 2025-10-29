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
        // Always take the UID from the main event because exceptions must have the same UID anyway.
        val uid = uidFromEntity(main)
        if (uid != null)
            to.properties += Uid(uid)
    }


    companion object {

        /**
         * Gets the UID from:
         *
         * - main event row (because exceptions must have the same UID anyway) or
         * - main event's Google Calendar extended property.
         *
         * @param entity    Android event to extract the UID from
         *
         * @return UID (or *null* if [entity] doesn't have an UID)
         */
        fun uidFromEntity(entity: Entity): String? =
            entity.entityValues.getAsString(Events.UID_2445)
                ?: uidFromExtendedProperties(entity.subValues)

        private fun uidFromExtendedProperties(rows: List<Entity.NamedContentValues>): String? {
            val uidRow = rows.firstOrNull {
                it.uri == ExtendedProperties.CONTENT_URI &&
                it.values.getAsString(ExtendedProperties.NAME) == EventsContract.EXTNAME_ICAL_UID
            }
            return uidRow?.values?.getAsString(ExtendedProperties.VALUE)
        }

    }

}