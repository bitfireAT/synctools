/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import at.bitfire.synctools.icalendar.isRecurring
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VEvent
import java.time.Duration
import java.time.temporal.TemporalAmount
import java.util.logging.Level
import java.util.logging.Logger

class DtEndBuilder: AndroidEventFieldBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        /*
        Events.DTEND is required by Android if event is non-recurring:
        https://developer.android.com/reference/android/provider/CalendarContract.Events#operations

        So if we need it (non-recurring event) and it's not available in the VEvent, we

        1. try to derive it from DTSTART/DURATION,
        2. otherwise use the start time in the default time one plus the default event duration (one hour).
        */

        val recurring = from.isRecurring()
        if (recurring) {
            // set DTEND to null for recurring events
            to.entityValues.putNull(Events.DTEND)
            return true
        }
        // non-recurring

        val dtEndDate = from.endDate?.date

        val calculatedEndDate = dtEndDate ?: calculateFromDuration(
            dtStartDate = from.startDate?.date,
            duration = from.duration?.duration ?: defaultDuration(
                allDay = DateUtils.isDate(from.startDate)
            )
        )

        // last fallback: current time in default time zone
        val endDate = calculatedEndDate ?: DateTime()

        /*
        ical4j uses GMT for the timestamp of all-day events because we have configured it that way
        (net.fortuna.ical4j.timezone.date.floating = false).

        However, this should be verified by a test → DtStartBuilderTest.
        */

        to.entityValues.put(Events.DTEND, endDate.time)
        return true
    }

    fun calculateFromDuration(dtStartDate: Date?, duration: TemporalAmount): Date? {
        if (dtStartDate != null)
            try {
                return if (dtStartDate is DateTime)
                    (dtStartDate.toZonedDateTime() + duration).toIcal4jDateTime()
                else
                    (dtStartDate.toLocalDate() + duration).toIcal4jDate()
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't calculate DTEND from DTSTART ($dtStartDate) + DURATION ($duration)", e)
            }
        return null
    }

    fun defaultDuration(allDay: Boolean): Duration =
        if (allDay)
            Duration.ofDays(1)
        else
            Duration.ofSeconds(0)       // TODO crashes on Android 7? See LegacyAndroidEventBuilder2 note. Check other Duration.ofSeconds() too

}