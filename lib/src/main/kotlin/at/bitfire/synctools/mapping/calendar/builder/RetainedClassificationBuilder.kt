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
import at.bitfire.ical4android.UnknownProperty
import net.fortuna.ical4j.model.property.Clazz

/**
 * Retains classification other than PUBLIC and PRIVATE as unknown property so that it can
 * be reused when "server default" is selected.
 *
 * Should not be returned as an unknown property in the future, but as a specific separate
 * extended property.
 */
class RetainedClassificationBuilder: AndroidEntityBuilder {

    override fun build(from: Event, main: Event, to: Entity) {
        val classification = from.classification
        if (classification != null && classification != Clazz.PUBLIC && classification != Clazz.PRIVATE)
            to.addSubValue(
                ExtendedProperties.CONTENT_URI,
                contentValuesOf(
                    ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
                    ExtendedProperties.VALUE to UnknownProperty.toJsonString(classification)
                )
            )
    }

}