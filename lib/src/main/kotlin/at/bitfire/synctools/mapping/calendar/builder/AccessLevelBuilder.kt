/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.UnknownProperty
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Clazz

class AccessLevelBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        val classification: Clazz? = from.classification

        /*
        If a classification that is not PUBLIC or PRIVATE is present, we retain it in an extended property because
        many calendar apps only process PUBLIC and PRIVATE and reset other values when editing the event.

        We can later use the value from the extended property value when ACCESS_LEVEL is Events.ACCESS_DEFAULT.
        */
        val retain: Boolean

        val accessLevel: Int? = when (classification) {
            Clazz.PUBLIC -> {
                retain = false
                Events.ACCESS_PUBLIC
            }

            Clazz.CONFIDENTIAL -> {
                retain = true
                Events.ACCESS_CONFIDENTIAL
            }

            Clazz.PRIVATE -> {
                retain = false
                Events.ACCESS_PRIVATE
            }

            null /* no CLASSIFICATION */ -> {
                retain = false
                Events.ACCESS_DEFAULT
            }

            else -> {
                retain = true
                // treat unknown values as PRIVATE as required in RFC 5545 3.8.1.3
                Events.ACCESS_PRIVATE
            }
        }

        to.entityValues.put(Events.ACCESS_LEVEL, accessLevel)

        // For now, we store the classification as an UnknownProperty.
        // Later, we should create a separate extended property for that.
        if (retain && classification != null)
            to.addSubValue(
                ExtendedProperties.CONTENT_URI,
                contentValuesOf(
                    ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
                    ExtendedProperties.VALUE to UnknownProperty.toJsonString(classification)
                )
            )

        return true
    }

}