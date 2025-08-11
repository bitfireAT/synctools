/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import at.bitfire.synctools.icalendar.isAllDay
import at.bitfire.synctools.icalendar.isRecurring
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VEvent
import java.time.Duration
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

        val duration: net.fortuna.ical4j.model.property.Duration? = from.duration

        val calculatedDuration: TemporalAmount = duration?.duration
            ?: calculateFromDtEnd(
                dtStartDate = from.startDate?.date,
                dtEndDate = from.endDate?.date
            )
            ?: defaultDuration(DateUtils.isDate(from.startDate))

        to.entityValues.put(Events.DURATION, calculatedDuration.toString())
        return true
    }

    fun calculateFromDtEnd(dtStartDate: Date?, dtEndDate: Date?): TemporalAmount? {
        if (dtStartDate != null && dtEndDate != null)
            try {
                val dtStartAllDay = dtStartDate.isAllDay()
                if (dtStartAllDay) {
                    val start = dtStartDate.toLocalDate()
                    val end = dtEndDate.toLocalDate()
                    return Duration.ofDays(start.until(end, ChronoUnit.DAYS))

                } else if (dtStartDate is DateTime && dtEndDate is DateTime) {
                    val start = dtStartDate.toZonedDateTime()
                    val end = dtEndDate.toZonedDateTime()
                    return Duration.ofSeconds(start.until(end, ChronoUnit.SECONDS))
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't calculate DURATION from DTEND ($dtEndDate) - DTSTART ($dtStartDate)", e)
            }

        return null
    }

    fun defaultDuration(allDay: Boolean): Duration =
        if (allDay)
            Duration.ofDays(1)
        else
            Duration.ofDays(0)

}