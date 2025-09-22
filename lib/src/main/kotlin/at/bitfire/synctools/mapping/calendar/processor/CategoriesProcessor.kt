/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import at.bitfire.ical4android.Event
import at.bitfire.synctools.storage.calendar.AndroidEvent2

class CategoriesProcessor: AndroidEventFieldProcessor {

    override fun process(from: Entity, main: Entity, to: Event) {
        val extended = from.subValues.filter { it.uri == ExtendedProperties.CONTENT_URI }.map { it.values }
        val categories = extended.firstOrNull { it.getAsString(ExtendedProperties.NAME) == AndroidEvent2.EXTNAME_CATEGORIES }
        val listValue = categories?.getAsString(ExtendedProperties.VALUE)
        if (listValue != null)
            to.categories += listValue.split(AndroidEvent2.CATEGORIES_SEPARATOR)
    }

}