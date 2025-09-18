/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.synctools.storage.calendar.AndroidEvent2

class CategoriesBuilder: AndroidEntityBuilder {

    override fun build(from: Event, main: Event, to: Entity) {
        val categories = from.categories
        if (categories.isNotEmpty()) {
            val rawCategories = categories.joinToString(AndroidEvent2.CATEGORIES_SEPARATOR.toString()) { category ->
                // drop occurrences of CATEGORIES_SEPARATOR in category names
                category.filter { it != AndroidEvent2.CATEGORIES_SEPARATOR }
            }

            to.addSubValue(
                ExtendedProperties.CONTENT_URI,
                contentValuesOf(
                    ExtendedProperties.NAME to AndroidEvent2.EXTNAME_CATEGORIES,
                    ExtendedProperties.VALUE to rawCategories
                )
            )
        }
    }

}