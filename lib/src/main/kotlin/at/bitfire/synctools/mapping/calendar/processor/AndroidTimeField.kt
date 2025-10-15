/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.util.TimeZones
import java.time.ZoneId

/**
 * Converts timestamps from the [android.provider.CalendarContract.Events.DTSTART] or [android.provider.CalendarContract.Events.DTEND]
 * fields into other representations.
 *
 * @param timestamp     value of the DTSTART/DTEND field (timestamp in milliseconds)
 * @param timeZone      value of the respective timezone field ([android.provider.CalendarContract.Events.EVENT_TIMEZONE] / [android.provider.CalendarContract.Events.EVENT_END_TIMEZONE])
 * @param allDay        whether [android.provider.CalendarContract.Events.ALL_DAY] is non-null and not zero
 */
class AndroidTimeField(
    private val timestamp: Long,
    private val timeZone: String?,
    private val allDay: Boolean,
    private val tzRegistry: TimeZoneRegistry
) {

    /** ID of system default timezone */
    private val defaultTzId by lazy { ZoneId.systemDefault().id }

    /**
     * Converts the given Android date/time into an ical4j date property.
     *
     * @return `Date` in case of an all-day event, `DateTime` in case of a non-all-day event
     */
    fun asIcal4jDate(): Date {
        if (allDay)
            return Date(timestamp)

        // non-all-day
        val tzId = timeZone
            ?: defaultTzId    // safe fallback (should never be used because the calendar provider requires EVENT_TIMEZONE)

        /* The resolved timezone may be null if there is no ical4j timezone for tzId, which can happen in rare cases
        (for instance if Android already knows about a new timezone ID or alias that doesn't exist in our
        ical4j version yet).

        In this case, we use the system default timezone ID as fallback and hope that we have a VTIMEZONE for it.
        If we also don't have a VTIMEZONE for the default timezone, we fall back to a UTC DATE-TIME without timezone. */

        val timezone = if (tzId == AndroidTimeUtils.TZID_UTC || tzId == TimeZones.UTC_ID || tzId == TimeZones.IBM_UTC_ID)
            null    // indicates UTC
        else
            (tzRegistry.getTimeZone(tzId) ?: tzRegistry.getTimeZone(defaultTzId))

        return DateTime(timestamp).also { dateTime ->
            if (timezone == null)
                dateTime.isUtc = true
            else
                dateTime.timeZone = timezone
        }
    }

}