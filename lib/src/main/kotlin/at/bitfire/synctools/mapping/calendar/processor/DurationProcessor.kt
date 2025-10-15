/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.property.DtEnd
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneOffset

class DurationProcessor(
    private val tzRegistry: TimeZoneRegistry
): AndroidEventFieldProcessor {

    override fun process(from: Entity, main: Entity, to: Event) {
        val values = from.entityValues

        /* Skip if:
        - DTEND is set – we don't need to process DURATION anymore.
        - DURATION is not set – then usually DTEND is set; however it's also OK to have neither DTEND nor DURATION in a VEVENT. */
        if (values.getAsLong(Events.DTEND) != null)
            return
        val durStr = values.getAsString(Events.DURATION) ?: return
        val duration = AndroidTimeUtils.parseDuration(durStr)

        // Skip in case of zero or negative duration (analogous to DTEND being before DTSTART).
        if ((duration is Duration && (duration.isZero || duration.isNegative)) ||
            (duration is Period && (duration.isZero || duration.isNegative)))
            return

        /* Some servers have problems with DURATION. For maximum compatibility, we always generate DTEND instead of DURATION.
        (After all, the constraint that non-recurring events have a DTEND while recurring events use DURATION is Android-specific.)
        So we have to calculate DTEND from DTSTART and its timezone plus DURATION. */

        val tsStart = values.getAsLong(Events.DTSTART) ?: return
        val allDay = (values.getAsInteger(Events.ALL_DAY) ?: 0) != 0

        if (allDay) {
            val startTimeUTC = Instant.ofEpochMilli(tsStart).atOffset(ZoneOffset.UTC)
            val endDate = (startTimeUTC + duration).toLocalDate()

            // DATE
            to.dtEnd = DtEnd(endDate.toIcal4jDate())

        } else {
            // DATE-TIME
            val startDateTime = AndroidTimeField(
                timestamp = tsStart,
                timeZone = values.getAsString(Events.EVENT_TIMEZONE),
                allDay = false,
                tzRegistry = tzRegistry
            ).asIcal4jDate() as DateTime

            val start = startDateTime.toZonedDateTime()
            val end = start + duration

            to.dtEnd = DtEnd(end.toIcal4jDateTime())
        }
    }

}