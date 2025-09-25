/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.Css3Color
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Color
import java.util.logging.Logger

class ColorProcessor: AndroidEventFieldProcessor {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val values = from.entityValues

        // color can be specified as RGB value and/or as index key (CSS3 color of AndroidCalendar)
        val color: Css3Color? =
            values.getAsString(Events.EVENT_COLOR_KEY)?.let { name ->      // try color key first
                try {
                    Css3Color.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    logger.warning("Ignoring unknown color name \"$name\"")
                    null
                }
            } ?: values.getAsInteger(Events.EVENT_COLOR)?.let { color ->        // otherwise, try to find the color name from the value
                Css3Color.entries.firstOrNull { it.argb == color }
            }

        if (color != null)
            to.properties += Color(null, color.name)
    }

}