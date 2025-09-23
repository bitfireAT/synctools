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
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import at.bitfire.synctools.exception.InvalidLocalResourceException
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.util.TimeZones
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.logging.Logger

class TimeFieldsProcessor: AndroidEventFieldProcessor {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }

    override fun process(from: Entity, main: Entity, to: Event) {
        val values = from.entityValues
        val allDay = (values.getAsInteger(Events.ALL_DAY) ?: 0) != 0
        val tsStart = values.getAsLong(Events.DTSTART) ?: throw InvalidLocalResourceException("Found event without DTSTART")

        var tsEnd = values.getAsLong(Events.DTEND)
        var duration =   // only use DURATION of DTEND is not defined
            if (tsEnd == null)
                values.getAsString(Events.DURATION)?.let { AndroidTimeUtils.parseDuration(it) }
            else
                null

        if (allDay) {
            to.dtStart = DtStart(Date(tsStart))

            // Android events MUST have duration or dtend [https://developer.android.com/reference/android/provider/CalendarContract.Events#operations].
            // Assume 1 day if missing (should never occur, but occurs).
            if (tsEnd == null && duration == null)
                duration = Duration.ofDays(1)

            if (duration != null) {
                // Some servers have problems with DURATION, so we always generate DTEND.
                val startDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(tsStart), ZoneOffset.UTC).toLocalDate()
                if (duration is Duration)
                    duration = Period.ofDays(duration.toDays().toInt())
                tsEnd = (startDate + duration).toEpochDay() * TimeApiExtensions.MILLIS_PER_DAY
                duration = null
            }

            if (tsEnd != null) {
                when {
                    tsEnd < tsStart ->
                        logger.warning("dtEnd $tsEnd (allDay) < dtStart $tsStart (allDay), ignoring")

                    tsEnd == tsStart ->
                        logger.fine("dtEnd $tsEnd (allDay) = dtStart, won't generate DTEND property")

                    else /* tsEnd > tsStart */ ->
                        to.dtEnd = DtEnd(Date(tsEnd))
                }
            }

        } else /* !allDay */ {
            // use DATE-TIME values

            // check time zone ID (calendar apps may insert no or an invalid ID)
            val startTzId = DateUtils.findAndroidTimezoneID(values.getAsString(Events.EVENT_TIMEZONE))
            val startTz = tzRegistry.getTimeZone(startTzId)
            val dtStartDateTime = DateTime(tsStart).apply {
                if (startTz != null) {  // null if there was not ical4j time zone for startTzId, which should not happen, but technically may happen
                    if (TimeZones.isUtc(startTz))
                        isUtc = true
                    else
                        timeZone = startTz
                }
            }
            to.dtStart = DtStart(dtStartDateTime)

            // Android events MUST have duration or dtend [https://developer.android.com/reference/android/provider/CalendarContract.Events#operations].
            // Assume 1 hour if missing (should never occur, but occurs).
            if (tsEnd == null && duration == null)
                duration = Duration.ofHours(1)

            if (duration != null) {
                // Some servers have problems with DURATION, so we always generate DTEND.
                val zonedStart = dtStartDateTime.toZonedDateTime()
                tsEnd = (zonedStart + duration).toInstant().toEpochMilli()
                duration = null
            }

            if (tsEnd != null) {
                if (tsEnd < tsStart)
                    logger.warning("dtEnd $tsEnd < dtStart $tsStart, ignoring")
                /*else if (tsEnd == tsStart)    // iCloud sends 404 when it receives an iCalendar with DTSTART but without DTEND
                    logger.fine("dtEnd $tsEnd == dtStart, won't generate DTEND property")*/
                else /* tsEnd > tsStart */ {
                    val endTz = values.getAsString(Events.EVENT_END_TIMEZONE)?.let { tzId ->
                        tzRegistry.getTimeZone(tzId)
                    } ?: startTz
                    to.dtEnd = DtEnd(DateTime(tsEnd).apply {
                        if (endTz != null) {
                            if (TimeZones.isUtc(endTz))
                                isUtc = true
                            else
                                timeZone = endTz
                        }
                    })
                }
            }

        }
    }

}