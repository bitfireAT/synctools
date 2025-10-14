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
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.util.TimeZones
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.logging.Logger

class DurationProcessor: AndroidEventFieldProcessor {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: Event) {
        val values = from.entityValues
        val allDay = (values.getAsInteger(Events.ALL_DAY) ?: 0) != 0

        /* Skip if:
        - DTEND is set – we don't need to process DURATION anymore.
        - DURATION is not set – then usually DTEND is set; however it's also OK to have neither DTEND nor DURATION in a VEVENT.
        */
        if (values.getAsLong(Events.DTEND) != null)
            return
        val durStr = values.getAsString(Events.DURATION) ?: return
        val duration = AndroidTimeUtils.parseDuration(durStr)

        /* Some problems have problems with DURATION. For maximum compatibility, we always generate DTEND instead of DURATION.
        We have to calculate DTEND from DTSTART and its timezone plus DURATION. */

        if (allDay) {
            val tsStart = values.getAsLong(Events.DTSTART)
            val start = Instant.ofEpochMilli(tsStart)
            val end = start + duration

            val endDate = end.atOffset(ZoneOffset.UTC).toLocalDate()
            to.dtEnd = DtEnd(endDate.toIcal4jDate())

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