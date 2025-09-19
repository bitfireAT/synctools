/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import at.bitfire.ical4android.Event
import at.bitfire.synctools.storage.calendar.AndroidEvent2

class UidProcessor: AndroidEventFieldProcessor {

    override fun process(from: Entity, to: Event) {
        // take from event row or Google Calendar extended property
        to.uid = from.entityValues.getAsString(Events.UID_2445) ?:
            uidFromExtendedProperties(from.subValues)
    }

    private fun uidFromExtendedProperties(rows: List<Entity.NamedContentValues>): String? {
        val uidRow = rows.firstOrNull {
            it.uri == ExtendedProperties.CONTENT_URI &&
            it.values.getAsString(ExtendedProperties.NAME) == AndroidEvent2.EXTNAME_ICAL_UID
        }

        return uidRow?.values?.getAsString(ExtendedProperties.VALUE)
    }

}