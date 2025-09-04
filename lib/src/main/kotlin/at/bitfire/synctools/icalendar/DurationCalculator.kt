/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount
import java.util.logging.Logger

/**
 * Calculates
 *
 * - DTEND from DTSTART + DURATION, and
 * - DURATION from DTSTART and DTEND.
 */
object DurationCalculator {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    fun calculateDuration(dtStartDate: Date, dtEndDate: Date): TemporalAmount {
        if (dtStartDate is DateTime) {
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

        } else {
            val start = dtStartDate.asLocalDate()
            val end = dtEndDate.asLocalDate()
            // return non-exact period (like P2D) - exact time varies for instance when DST changes
            return start.until(end)
        }
    }

    fun calculateEndDate(dtStartDate: Date, duration: TemporalAmount): Date =
        if (dtStartDate is DateTime) {
            val end = dtStartDate.asZonedDateTime() + duration
            end.toIcal4jDateTime()
        } else {
            val end = dtStartDate.asLocalDate() + duration
            end.toIcal4jDate()
        }

}