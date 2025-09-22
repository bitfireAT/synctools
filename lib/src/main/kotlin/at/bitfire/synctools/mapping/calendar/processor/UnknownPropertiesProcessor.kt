/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.UnknownProperty
import org.json.JSONException
import java.util.logging.Level
import java.util.logging.Logger

class UnknownPropertiesProcessor: AndroidEventFieldProcessor {

    val logger: Logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: Event) {
        val extended = from.subValues.filter { it.uri == ExtendedProperties.CONTENT_URI }.map { it.values }
        val unknownProperties = extended.filter { it.getAsString(ExtendedProperties.NAME) == UnknownProperty.CONTENT_ITEM_TYPE }
        val jsonProperties = unknownProperties.mapNotNull { it.getAsString(ExtendedProperties.VALUE) }
        for (json in jsonProperties)
            try {
                to.unknownProperties += UnknownProperty.fromJsonString(json)
            } catch (e: JSONException) {
                logger.log(Level.WARNING, "Couldn't parse unknown properties", e)
            }
    }

}