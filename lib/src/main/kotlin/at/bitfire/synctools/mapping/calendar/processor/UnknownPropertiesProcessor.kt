/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import at.bitfire.ical4android.UnknownProperty
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import org.json.JSONException
import java.util.logging.Level
import java.util.logging.Logger

class UnknownPropertiesProcessor: AndroidEventFieldProcessor {

    private val logger: Logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val extended = from.subValues.filter { it.uri == ExtendedProperties.CONTENT_URI }.map { it.values }
        val unknownProperties = extended.filter { it.getAsString(ExtendedProperties.NAME) == UnknownProperty.CONTENT_ITEM_TYPE }
        val jsonProperties = unknownProperties.mapNotNull { it.getAsString(ExtendedProperties.VALUE) }
        for (json in jsonProperties)
            try {
                val prop = UnknownProperty.fromJsonString(json)
                if (!EXCLUDED.contains(prop.name))
                    to.properties += prop
            } catch (e: JSONException) {
                logger.log(Level.WARNING, "Couldn't parse unknown properties", e)
            }
    }


    companion object {

        /**
         * These properties are not restored into the VEvent. Usually they're used by other processors instead.
         *
         * In the future, this shouldn't be necessary anymore because when other builders/processors store data,
         * they shouldn't use an unknown property, but instead define their own extended property.
         */
        val EXCLUDED = arrayOf(Property.CLASS)

    }

}