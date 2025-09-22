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
import java.net.URI
import java.net.URISyntaxException

class UrlProcessor: AndroidEventFieldProcessor {

    override fun process(from: Entity, main: Entity, to: Event) {
        val extended = from.subValues.filter { it.uri == ExtendedProperties.CONTENT_URI }.map { it.values }
        val urlRow = extended.firstOrNull { it.getAsString(ExtendedProperties.NAME) == AndroidEvent2.EXTNAME_URL }
        val url = urlRow?.getAsString(ExtendedProperties.VALUE)
        if (url != null)
            to.url = try {
                URI(url)
            } catch (_: URISyntaxException) {
                null
            }
    }

}