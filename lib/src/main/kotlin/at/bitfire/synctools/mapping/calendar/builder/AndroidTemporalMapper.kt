/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.util.TimeZones
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.Temporal

object AndroidTemporalMapper {

    private const val TZID_UTC = "UTC"

    /**
     * Converts this [Temporal] to the timestamp that should be used when writing an event to the
     * Android calendar provider.
     */
    fun Temporal.toTimestamp(): Long {
        val epochSeconds = when (this) {
            is LocalDate -> atStartOfDay().atZone(TimeZones.getDateTimeZone().toZoneId()).toEpochSecond()
            is LocalDateTime -> atZone(TimeZones.getDefault().toZoneId()).toEpochSecond()
            is OffsetDateTime -> toEpochSecond()
            is ZonedDateTime -> toEpochSecond()
            is Instant -> epochSecond
            else -> error("Unsupported Temporal type: ${this::class.qualifiedName}")
        }

        return epochSeconds * 1000L
    }

    /**
     * Returns the timezone ID that should be used when writing an event to the Android calendar provider.
     *
     * Note: For date-times with a given time zone, it needs to be a system time zone. Call
     * [at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate] on dates coming from
     * ical4j before calling this function.
     *
     * @return - "UTC" for dates and UTC date-times
     *         - the specified time zone ID for date-times with given time zone
     *         - the currently set default time zone ID for floating date-times
     */
    fun Temporal.androidTimezoneId(): String {
        return if (TemporalAdapter.isDateTimePrecision(this)) {
            if (TemporalAdapter.isUtc(this)) {
                TZID_UTC
            } else if (TemporalAdapter.isFloating(this)) {
                ZoneId.systemDefault().id
            } else {
                require(this is ZonedDateTime) { "Non-floating date-time must be a ZonedDateTime" }

                val timezoneId = this.zone.id
                require(!timezoneId.startsWith("ical4j")) {
                    "ical4j ZoneIds are not supported. Call DatePropertyTzMapper.normalizedDate() " +
                        "before passing a date to this function."
                }

                timezoneId
            }
        } else {
            // For all-day events EventsColumns.EVENT_TIMEZONE must be "UTC".
            TZID_UTC
        }
    }

}