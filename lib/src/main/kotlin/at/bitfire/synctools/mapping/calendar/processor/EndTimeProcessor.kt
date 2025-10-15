/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.property.DtEnd
import java.util.logging.Logger

class EndTimeProcessor(
    private val tzRegistry: TimeZoneRegistry
): AndroidEventFieldProcessor {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: Event) {
        val values = from.entityValues
        val allDay = (values.getAsInteger(Events.ALL_DAY) ?: 0) != 0

        // Skip if DTEND is not set – then usually DURATION is set; however it's also OK to have neither DTEND nor DURATION in a VEVENT.
        val tsEnd = values.getAsLong(Events.DTEND) ?: return

        // Also skip if DTEND is not after DTSTART (not allowed in iCalendar)
        val tsStart = values.getAsLong(Events.DTSTART) ?: return
        if (tsEnd <= tsStart) {
            logger.warning("Ignoring DTEND=$tsEnd that is not after DTSTART=$tsStart")
            return
        }

        // DATE or DATE-TIME according to allDay
        val end = AndroidTimeField(
            timestamp = tsEnd,
            timeZone = values.getAsString(Events.EVENT_END_TIMEZONE)
                ?: values.getAsString(Events.EVENT_TIMEZONE),   // if end timezone is not present, assume same as for start
            allDay = allDay,
            tzRegistry = tzRegistry
        ).asIcal4jDate()

        to.dtEnd = DtEnd(end)
    }

}