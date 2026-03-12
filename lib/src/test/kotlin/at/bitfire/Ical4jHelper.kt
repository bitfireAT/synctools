/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire

import net.fortuna.ical4j.model.CalendarDateFormat
import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.model.TimeZone
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.Temporal

fun dateTimeValue(value: String): Temporal {
    return if (value.endsWith("Z")) {
        TemporalAdapter.parse<Temporal>(value, CalendarDateFormat.UTC_DATE_TIME_FORMAT).temporal
    } else {
        TemporalAdapter.parse<Temporal>(value, CalendarDateFormat.FLOATING_DATE_TIME_FORMAT).temporal
    }
}

fun dateTimeValue(value: String, timeZone: TimeZone): ZonedDateTime {
    val temporal = dateTimeValue(value)
    return if (temporal is LocalDateTime) {
        temporal.atZone(timeZone.toZoneId())
    } else {
        error("Unexpected temporal type: ${temporal::class}")
    }
}