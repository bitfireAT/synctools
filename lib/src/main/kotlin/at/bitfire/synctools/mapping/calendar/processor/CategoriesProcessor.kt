/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import at.bitfire.synctools.storage.calendar.AndroidEvent
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Categories

class CategoriesProcessor: AndroidEventFieldProcessor {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val extended = from.subValues.filter { it.uri == ExtendedProperties.CONTENT_URI }.map { it.values }
        val categories = extended.firstOrNull { it.getAsString(ExtendedProperties.NAME) == AndroidEvent.EXTNAME_CATEGORIES }
        val listValue = categories?.getAsString(ExtendedProperties.VALUE)
        if (listValue != null) {
            to.properties += Categories(TextList(
                listValue.split(AndroidEvent.CATEGORIES_SEPARATOR).toTypedArray()
            ))
        }
    }

}