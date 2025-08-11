/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.util.TimeApiExtensions.requireZoneId
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalTime
import at.bitfire.synctools.icalendar.isAllDay
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VEvent
import java.time.ZonedDateTime
import java.util.logging.Logger

class OriginalReferenceBuilder(
    private val syncId: String
): AndroidEventFieldBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        // Skip if this builder isn't building an exception.
        if (from === main) {
            to.entityValues.putNull(Events.ORIGINAL_SYNC_ID)
            to.entityValues.putNull(Events.ORIGINAL_ALL_DAY)
            to.entityValues.putNull(Events.ORIGINAL_INSTANCE_TIME)
            return true
        }

        /*
        Mark resulting entity as invalid if
        - the exception doesn't have a RECURRENCE-ID, or
        - the main event doesn't have a DTSTART.
        */
        val recurrenceDate = from.recurrenceId?.date
        if (recurrenceDate == null) {
            logger.warning("Ignoring exception without RECURRENCE-ID")
            return false
        }

        val mainStartDate = main.startDate?.date
        if (mainStartDate == null) {
            logger.warning("Ignoring exception because main event doesn't have DTSTART")
            return false
        }

        // align RECURRENCE-ID with value type (date/date-time) of main event's DTSTART
        val alignedRecurrenceDate = alignRecurrenceId(
            recurrenceDate = recurrenceDate,
            mainStartDate = mainStartDate
        )

        val values = contentValuesOf(
            // never provide explicit reference to Events._ID (calendar provider doesn't process it correctly)
            Events.ORIGINAL_SYNC_ID to syncId,
            Events.ORIGINAL_ALL_DAY to if (mainStartDate.isAllDay()) 1 else 0,
            Events.ORIGINAL_INSTANCE_TIME to alignedRecurrenceDate.time
        )

        to.entityValues.putAll(values)
        return true
    }

    private fun alignRecurrenceId(recurrenceDate: Date, mainStartDate: Date): Date {
        if (mainStartDate.isAllDay() && !recurrenceDate.isAllDay()) {
            // main event is DATE, but RECURRENCE-ID is DATE-TIME → change RECURRENCE-ID to DATE
            val localDate = recurrenceDate.toLocalDate()
            return Date(localDate.toIcal4jDate())

        } else if (mainStartDate is DateTime && recurrenceDate.isAllDay()) {
            // main event is DATE-TIME, but RECURRENCE-ID is DATE → change RECURRENCE-ID to DATE-TIME
            val localDate = recurrenceDate.toLocalDate()
            // guess time and time zone from DTSTART
            val zonedTime = ZonedDateTime.of(
                localDate,
                mainStartDate.toLocalTime(),
                mainStartDate.requireZoneId()
            )
            return zonedTime.toIcal4jDateTime()
        }

        // no alignment needed
        return recurrenceDate
    }

}