/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import at.bitfire.ical4android.UnknownProperty
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Clazz
import org.json.JSONException

class AccessLevelProcessor: AndroidEventFieldProcessor {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val values = from.entityValues

        // take classification from main row
        val classification = when (values.getAsInteger(Events.ACCESS_LEVEL)) {
            Events.ACCESS_PUBLIC ->
                Clazz.PUBLIC

            Events.ACCESS_PRIVATE ->
                Clazz.PRIVATE

            Events.ACCESS_CONFIDENTIAL ->
                Clazz.CONFIDENTIAL

            else /* Events.ACCESS_DEFAULT */ ->
                retainedClassification(from)
        }
        to.properties += classification
    }

    private fun retainedClassification(from: Entity): Clazz? {
        val extendedProperties = from.subValues.filter { it.uri == ExtendedProperties.CONTENT_URI }.map { it.values }
        val unknownProperties = extendedProperties.filter { it.getAsString(ExtendedProperties.NAME) == UnknownProperty.CONTENT_ITEM_TYPE }
        val retainedClassification: Clazz? = unknownProperties.firstNotNullOfOrNull {
            val json = it.getAsString(ExtendedProperties.VALUE)
            val prop = try {
                UnknownProperty.fromJsonString(json)
            } catch (_: JSONException) {
                // not parseable
                null
            }
            prop as? Clazz
        }

        return retainedClassification
    }

}