/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import net.fortuna.ical4j.model.Property
import java.util.logging.Logger

/**
 * Legacy mapper from an [Event] data object to Android content provider data rows
 * (former "build..." methods).
 *
 * Important: To use recurrence exceptions, you MUST set _SYNC_ID and ORIGINAL_SYNC_ID
 * in populateEvent() / buildEvent. Setting _ID and ORIGINAL_ID is not sufficient.
 */
@Deprecated("Use AndroidEventBuilder instead")
class LegacyAndroidEventBuilder2(
    private val calendar: AndroidCalendar,
    private val event: Event,

    // AndroidEvent-level fields
    private val id: Long?,
    private val syncId: String?,
    private val eTag: String?,
    private val scheduleTag: String?,
    private val flags: Int
) {

    private val logger
        get() = Logger.getLogger(javaClass.name)


    fun build() =
        EventAndExceptions(
            main = buildEvent(null),
            exceptions = event.exceptions.map { exception ->
                buildEvent(exception)
            }
        )

    fun buildEvent(recurrence: Event?): Entity {
        val row = TODO()

        /*val entity = Entity(row)
        val from = recurrence ?: event

        for (unknownProperty in event.unknownProperties) {
            val values = buildUnknownProperty(unknownProperty)
            if (values != null)
                entity.addSubValue(ExtendedProperties.CONTENT_URI, values)
        }

        return entity*/
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