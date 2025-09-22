/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.UnknownProperty
import net.fortuna.ical4j.model.Property
import java.util.logging.Logger

class UnknownPropertiesBuilder: AndroidEntityBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: Event, main: Event, to: Entity) {
        for (property in from.unknownProperties) {
            val values = buildUnknownProperty(property)
            if (values != null)
                to.addSubValue(ExtendedProperties.CONTENT_URI, values)
        }
    }

    private fun buildUnknownProperty(property: Property): ContentValues? {
        if (property.value == null) {
            logger.warning("Ignoring unknown property with null value")
            return null
        }
        if (property.value.length > UnknownProperty.MAX_UNKNOWN_PROPERTY_SIZE) {
            logger.warning("Ignoring unknown property with ${property.value.length} octets (too long)")
            return null
        }

        return contentValuesOf(
            ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
            ExtendedProperties.VALUE to UnknownProperty.toJsonString(property)
        )
    }

}