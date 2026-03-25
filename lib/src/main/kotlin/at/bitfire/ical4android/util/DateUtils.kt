/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.util

import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.model.property.DateProperty
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal

/**
 * Date/time utilities
 *
 * Before this object is accessed the first time, the accessing thread's contextClassLoader
 * must be set to an Android Context.classLoader!
 */
object DateUtils {

    // time zones

    /**
     * Determines whether a given date represents a DATE value.
     * @param date date property to check
     * @return *true* if the date is a DATE value; *false* otherwise (for instance, when the argument is a DATE-TIME value or null)
     */
    fun isDate(date: DateProperty<*>?): Boolean =
        date != null && !TemporalAdapter.isDateTimePrecision(date.date)

    /**
     * Determines whether a given date represents a DATE-TIME value.
     * @param date date property to check
     * @return *true* if the date is a DATE-TIME value; *false* otherwise (for instance, when the argument is a DATE value or null)
     */
    fun isDateTime(date: DateProperty<*>?): Boolean =
        date != null && TemporalAdapter.isDateTimePrecision(date.date)

    /**
     * Determines whether a given [Temporal] represents a DATE value.
     */
    fun isDate(date: Temporal?): Boolean =
        date != null && !TemporalAdapter.isDateTimePrecision(date)

    /**
     * Determines whether a given [Temporal] represents a DATE-TIME value.
     */
    fun isDateTime(date: Temporal?): Boolean =
        date != null && TemporalAdapter.isDateTimePrecision(date)

    /**
     * Converts the given [Instant] by truncating it to days, and converting into [LocalDate] by its
     * epoch timestamp.
     */
    fun Instant.toLocalDate(): LocalDate {
        val epochSeconds = truncatedTo(ChronoUnit.DAYS).epochSecond
        return LocalDate.ofEpochDay(epochSeconds / (24 * 60 * 60 /*seconds in a day*/))
    }

    /**
     * Converts the given generic [Temporal] into milliseconds since epoch.
     * @param fallbackTimezone Any specific timezone to use as fallback if there's not enough
     * information on the [Temporal] type (local types). Defaults to UTC.
     * @throws IllegalArgumentException if the [Temporal] is from an unknown time, which also doesn't
     * support [ChronoField.INSTANT_SECONDS]
     */
    fun Temporal.toEpochMilli(fallbackTimezone: ZoneId? = null): Long {
        // If the temporal supports instant seconds, we can compute epoch millis directly from them
        if (isSupported(ChronoField.INSTANT_SECONDS)) {
            val seconds = getLong(ChronoField.INSTANT_SECONDS)
            val nanos = get(ChronoField.NANO_OF_SECOND)
            // Convert seconds and nanos to millis
            return (seconds * 1000) + (nanos / 1_000_000)
        }

        return when (this) {
            is Instant -> this.toEpochMilli()
            is ZonedDateTime -> this.toInstant().toEpochMilli()
            is OffsetDateTime -> this.toInstant().toEpochMilli()
            is LocalDate -> this.atStartOfDay(fallbackTimezone ?: ZoneOffset.UTC).toInstant().toEpochMilli()
            is LocalDateTime -> this.atZone(fallbackTimezone ?: ZoneOffset.UTC).toInstant().toEpochMilli()
            else -> throw IllegalArgumentException("${this::class.java.simpleName} cannot be converted to epoch millis.")
        }
    }

}