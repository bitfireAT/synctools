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
import at.bitfire.synctools.exception.InvalidLocalResourceException
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.util.TimeZones
import java.time.ZoneId
import java.util.logging.Logger

class StartTimeProcessor: AndroidEventFieldProcessor {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }

    override fun process(from: Entity, main: Entity, to: Event) {
        val values = from.entityValues
        val allDay = (values.getAsInteger(Events.ALL_DAY) ?: 0) != 0

        val tsStart = values.getAsLong(Events.DTSTART) ?: throw InvalidLocalResourceException("Missing DTSTART")
        val tzId = values.getAsString(Events.EVENT_TIMEZONE)
            // safe fallback value (should never be used because the calendar provider requires EVENT_TIMEZONE)
            ?: AndroidTimeUtils.TZID_UTC

        if (allDay) {
            // DTSTART with VALUE=DATE
            to.dtStart = DtStart(Date(tsStart))

        } else {
            // DTSTART with VALUE=DATE-TIME

            /* timezone may be null if there is no ical4j timezone for tzId, which can happen in rare cases
            (for instance if Android already knows about a new timezone ID or alias that doesn't exist in our
            ical4j version yet).

            In this case, we use the system default timezone ID as fallback (and hope that we have a VTIMEZONE for it).
            If we don't have a VTIMEZONE for it, we fall back to a UTC DATE-TIME (no VTIMEZONE needed). */
            val timezone = tzRegistry.getTimeZone(tzId)
                ?: tzRegistry.getTimeZone(ZoneId.systemDefault().id)

            to.dtStart = DtStart(DateTime(tsStart).also { dateTime ->
                if (timezone == null || (tzId == AndroidTimeUtils.TZID_UTC || TimeZones.isUtc(timezone)))
                    dateTime.isUtc = true
                else
                    dateTime.timeZone = timezone
            })
        }
    }

}