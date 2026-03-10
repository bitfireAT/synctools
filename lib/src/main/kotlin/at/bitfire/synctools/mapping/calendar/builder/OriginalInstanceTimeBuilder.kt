/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.requireZoneId
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalTime
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jZonedDateTime
import at.bitfire.synctools.icalendar.recurrenceId
import at.bitfire.synctools.icalendar.requireDtStart
import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.model.component.VEvent
import java.time.ZonedDateTime
import java.time.temporal.Temporal

class OriginalInstanceTimeBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues
        if (from !== main) {
            // only for exceptions
            val originalDtStart = main.requireDtStart<Temporal>()
            values.put(Events.ORIGINAL_ALL_DAY, if (DateUtils.isDate(originalDtStart)) 1 else 0)

            val recurrenceDate = from.recurrenceId?.date
            val originalDate = originalDtStart.date
            var originalInstanceTime: ZonedDateTime? = recurrenceDate?.toIcal4jZonedDateTime()

            // rewrite recurrenceDate, if necessary
            if (
                recurrenceDate != null && TemporalAdapter.isDateTimePrecision(recurrenceDate) &&
                originalDate != null && !TemporalAdapter.isDateTimePrecision(originalDate)
            ) {
                // rewrite RECURRENCE-ID;VALUE=DATE-TIME to VALUE=DATE for all-day events
                val localDate = recurrenceDate.toLocalDate()
                originalInstanceTime = localDate.toIcal4jZonedDateTime()

            } else if (
                recurrenceDate != null && !TemporalAdapter.isDateTimePrecision(recurrenceDate) &&
                originalDate != null && TemporalAdapter.isDateTimePrecision(originalDate)
            ) {
                // rewrite RECURRENCE-ID;VALUE=DATE to VALUE=DATE-TIME for non-all-day-events
                val localDate = recurrenceDate.toLocalDate()
                // guess time and time zone from DTSTART
                originalInstanceTime = ZonedDateTime.of(
                    localDate,
                    originalDate.toLocalTime(),
                    originalDate.requireZoneId()
                )
            }
            values.put(Events.ORIGINAL_INSTANCE_TIME, originalInstanceTime?.toInstant()?.toEpochMilli())

        } else {
            // main event
            values.putNull(Events.ORIGINAL_ALL_DAY)
            values.putNull(Events.ORIGINAL_INSTANCE_TIME)
        }
    }
}