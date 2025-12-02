/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import java.util.logging.Logger

class EndTimeHandler(
    private val tzRegistry: TimeZoneRegistry
): AndroidEventFieldHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val values = from.entityValues

        // Skip if start is not set
        val tsStart = values.getAsLong(Events.DTSTART) ?: return

        // Skip if DURATION (non-zero and positive) is set
        // (even if its invalid, since we never want DURATION _and_ DTSTART)
        if (values.getAsString(Events.DURATION) != null)
            return

        // Default to DTSTART value if DTEND is not set and DURATION is not present.
        // This is a workaround for iCloud that is always applied; it seems not to bother other servers.
        // Normally it's OK to have neither DTEND nor DURATION in a VEVENT with DTSTART, but iCloud
        // rejects that with with a 404. See davx5-ose#1859
        var tsEnd = values.getAsLong(Events.DTEND) ?: tsStart

        // Also default to the DTSTART value if DTEND is not after DTSTART (which is not allowed in iCalendar)
        if (tsEnd <= tsStart) {
            logger.warning("DTEND=$tsEnd is not after DTSTART=$tsStart, setting DTEND=$tsStart")
            tsEnd = tsStart
        }

        // DATE or DATE-TIME according to allDay
        val allDay = (values.getAsInteger(Events.ALL_DAY) ?: 0) != 0
        val end = AndroidTimeField(
            timestamp = tsEnd,
            timeZone = values.getAsString(Events.EVENT_END_TIMEZONE)
                ?: values.getAsString(Events.EVENT_TIMEZONE),   // if end timezone is not present, assume same as for start
            allDay = allDay,
            tzRegistry = tzRegistry
        ).asIcal4jDate()

        to.properties += DtEnd(end)
    }

}