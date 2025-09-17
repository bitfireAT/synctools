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

class UrlBuilder: AndroidEventFieldBuilder {

    override fun build(from: Event, main: Event, to: Entity) {
        val uri = from.url
        if (uri != null)
            to.addSubValue(
                ExtendedProperties.CONTENT_URI,
                contentValuesOf(
                    ExtendedProperties.NAME to AndroidEvent2.EXTNAME_URL,
                    ExtendedProperties.VALUE to uri.toString()
                )
            )
    }

}