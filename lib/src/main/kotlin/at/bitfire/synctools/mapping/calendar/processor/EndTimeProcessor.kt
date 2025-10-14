/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.AndroidTimeUtils
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.util.TimeZones
import java.time.ZoneId
import java.util.logging.Logger

class EndTimeProcessor: AndroidEventFieldProcessor {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }

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

        if (allDay) {
            // DTEND with VALUE=DATE
            to.dtEnd = DtEnd(Date(tsEnd))

        } else {
            // DTEND with VALUE=DATE-TIME
            val tzId = values.getAsString(Events.EVENT_END_TIMEZONE)
                ?: values.getAsString(Events.EVENT_TIMEZONE)    // if not present, assume same as DTSTART
                ?: AndroidTimeUtils.TZID_UTC    // safe fallback (should never be used because the calendar provider requires EVENT_TIMEZONE)

            /* The resolved timezone may be null if there is no ical4j timezone for tzId, which can happen in rare cases
            (for instance if Android already knows about a new timezone ID or alias that doesn't exist in our
            ical4j version yet).

            In this case, we use the system default timezone ID as fallback and hope that we have a VTIMEZONE for it.
            If we also don't have a VTIMEZONE for the default timezone, we fall back to a UTC DATE-TIME without timezone. */

            val timezone = if (tzId == AndroidTimeUtils.TZID_UTC)
                null    // indicates UTC
            else
                (tzRegistry.getTimeZone(tzId) ?: tzRegistry.getTimeZone(ZoneId.systemDefault().id))

            to.dtEnd = DtEnd(DateTime(tsEnd).also { dateTime ->
                if (timezone == null || TimeZones.isUtc(timezone))
                    dateTime.isUtc = true
                else
                    dateTime.timeZone = timezone
            })
        }
    }

}