/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.asLocalDate
import at.bitfire.synctools.icalendar.asZonedDateTime
import at.bitfire.synctools.icalendar.isAllDay
import at.bitfire.synctools.icalendar.isRecurring
import at.bitfire.synctools.mapping.calendar.builder.DefaultValues.defaultDuration
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VEvent
import java.time.Duration
import java.time.Period
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount
import java.util.logging.Level
import java.util.logging.Logger

class DurationBuilder: AndroidEventFieldBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        /*
        Events.DURATION is required by Android if event is recurring:
        https://developer.android.com/reference/android/provider/CalendarContract.Events#operations

        So if we need it (recurring event) and it's not available in the VEvent, we

        1. try to derive it from DTSTART/DTEND,
        2. otherwise use a default duration.
        */

        val recurring = from.isRecurring()
        if (!recurring) {
            // set DURATION to null for non-recurring events
            to.entityValues.putNull(Events.DURATION)
            return true
        }
        // recurring

        // get DURATION from VEvent
        val durationOrNull: TemporalAmount? = from.duration?.duration

        // if there's no VEvent DURATION, try to calculate it from DTSTART/DTEND
        val dtStartDate = from.startDate?.date ?: return false
        val allDay = dtStartDate.isAllDay()

        val dtEndDate = from.endDate?.date
        val calculatedDuration: TemporalAmount? =
            if (dtEndDate != null)
                calculateFromStartAndEnd(
                    dtStartDate = dtStartDate,
                    dtEndDate = dtEndDate
                )
            else
                durationOrNull

        // align DURATION according to DTSTART value type (DATE/DATE-TIME)
        val alignedDuration: TemporalAmount? = if (calculatedDuration != null && allDay && calculatedDuration is Duration) {
            // exact time period (Duration), rewrite to Period (days) whose actual length may vary
            Period.ofDays(calculatedDuration.toDays().toInt())
        } else
            calculatedDuration

        // fall back to default duration
        val duration = calculatedDuration ?: defaultDuration(allDay)

        to.entityValues.put(Events.DURATION, duration.toString())
        return true
    }

    fun calculateFromStartAndEnd(dtStartDate: Date, dtEndDate: Date): TemporalAmount? {
        try {
            if (dtStartDate.isAllDay()) {
                val start = dtStartDate.asLocalDate()
                val end = dtEndDate.asLocalDate()
                // return non-exact period (like P2D) - exact time varies for instance when DST changes
                return start.until(end)

            } else if (dtStartDate is DateTime) {
                val start = dtStartDate.asZonedDateTime()
                val end =
                    if (dtEndDate is DateTime)
                        dtEndDate.asZonedDateTime()
                    else
                        ZonedDateTime.of(
                            dtEndDate.asLocalDate(),    // take date from DTEND
                            start.toLocalTime(),        // take time from DTSTART
                            start.zone                  // take time zone from DTSTART
                        )
                // return exact time period (Duration)
                return Duration.ofSeconds(start.until(end, ChronoUnit.SECONDS))
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't calculate DURATION from DTEND ($dtEndDate) - DTSTART ($dtStartDate)", e)
        }

        return null
    }

}