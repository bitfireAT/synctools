/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.ical4android.util.TimeApiExtensions.requireZoneId
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

fun Date.isAllDay(): Boolean =
    this !is DateTime

fun Date.alignAllDay(other: Date): Date =
    if (this is DateTime) {
        if (other is DateTime)
            this
        else /* other is DATE */
            this.asLocalDate().toIcal4jDate()

    } else /* this is DATE */ {
        if (other is DateTime) {
            val otherZonedTime = other.asZonedDateTime()
            ZonedDateTime.of(
                this.asLocalDate(),
                otherZonedTime.toLocalTime(),
                otherZonedTime.zone
            ).toIcal4jDateTime()
        } else /* other is DATE */
            this
    }

fun Date.asLocalDate(): LocalDate {
    val utcDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneOffset.UTC)
    return utcDateTime.toLocalDate()
}

fun DateTime.asZonedDateTime(): ZonedDateTime =
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), requireZoneId())
