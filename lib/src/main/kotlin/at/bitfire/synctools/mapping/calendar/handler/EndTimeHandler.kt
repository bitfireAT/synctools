/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.mapping.calendar.MappingUtil
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart

class EndTimeHandler(
    private val tzRegistry: TimeZoneRegistry
) : AndroidEventFieldHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val values = from.entityValues

        // Skip if start is not set (end can not exist without start)
        val tsStart = values.getAsLong(Events.DTSTART) ?: return

        // Get other values
        val tsEnd: Long? = values.getAsLong(Events.DTEND)
        val startTz = values.getAsString(Events.EVENT_TIMEZONE)
        val endTz = values.getAsString(Events.EVENT_END_TIMEZONE)
        val allDay = (values.getAsInteger(Events.ALL_DAY) ?: 0) != 0

        // DTSTART from DATE or DATE-TIME according to allDay
        val dtStart = DtStart(AndroidTimeField(tsStart, startTz, allDay, tzRegistry).asIcal4jDate())

        // Create duration
        val duration = values.getAsString(Events.DURATION)?.let { durStr ->
            AndroidTimeUtils.parseDuration(durStr)
        }

        // Create end if it is set and after start
        val end = tsEnd
            ?.takeIf { it >= tsStart } // End is only useful to us, if after start
            ?.let {
                // DATE or DATE-TIME according to allDay
                AndroidTimeField(
                    timestamp = tsEnd,
                    timeZone = endTz ?: startTz, // if end timezone is not present, assume same as for start
                    allDay = allDay,
                    tzRegistry = tzRegistry
                ).asIcal4jDate()
            }

        // Use the set end if possible, otherwise generate from start + duration if set. Last resort
        // is to use a default value.
        val dtEnd = end?.let { DtEnd(it) }
            ?: MappingUtil.dtEndFromDuration(dtStart, duration)
            ?: MappingUtil.dtEndFromDefault(dtStart) // for compatibility with iCloud. See davx5-ose#1859

        to.properties += dtEnd
    }

}