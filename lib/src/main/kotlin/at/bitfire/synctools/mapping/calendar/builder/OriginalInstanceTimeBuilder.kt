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
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalTime
import at.bitfire.synctools.icalendar.requireDtStart
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VEvent
import java.time.ZonedDateTime

class OriginalInstanceTimeBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues
        if (from !== main) {
            // only for exceptions
            val originalDtStart = main.requireDtStart()
            values.put(Events.ORIGINAL_ALL_DAY, if (DateUtils.isDate(originalDtStart)) 1 else 0)

            var recurrenceDate = from.recurrenceId?.date
            val originalDate = originalDtStart.date

            // rewrite recurrenceDate, if necessary
            if (recurrenceDate is DateTime && originalDate != null && originalDate !is DateTime) {
                // rewrite RECURRENCE-ID;VALUE=DATE-TIME to VALUE=DATE for all-day events
                val localDate = recurrenceDate.toLocalDate()
                recurrenceDate = Date(localDate.toIcal4jDate())

            } else if (recurrenceDate != null && recurrenceDate !is DateTime && originalDate is DateTime) {
                // rewrite RECURRENCE-ID;VALUE=DATE to VALUE=DATE-TIME for non-all-day-events
                val localDate = recurrenceDate.toLocalDate()
                // guess time and time zone from DTSTART
                val zonedTime = ZonedDateTime.of(
                    localDate,
                    originalDate.toLocalTime(),
                    originalDate.requireZoneId()
                )
                recurrenceDate = zonedTime.toIcal4jDateTime()
            }
            values.put(Events.ORIGINAL_INSTANCE_TIME, recurrenceDate?.time)

        } else {
            // main event
            values.putNull(Events.ORIGINAL_ALL_DAY)
            values.putNull(Events.ORIGINAL_INSTANCE_TIME)
        }
    }

}