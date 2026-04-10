/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.accounts.Account
import android.content.ContentProviderClient
import android.provider.CalendarContract
import androidx.core.content.contentValuesOf
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.logging.Logger

object TestCalendar {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    fun create(account: Account, client: ContentProviderClient, withColors: Boolean = false): AndroidCalendar {
        val provider = AndroidCalendarProvider(account, client)

        // we use colors for testing
        if (withColors)
            provider.provideCss3ColorIndices()
        else
            provider.removeColorIndices()

        return provider.createAndGetCalendar(
            contentValuesOf(
                CalendarContract.Calendars.NAME to UUID.randomUUID().toString(),
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME to "ical4android Test Calendar",
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL to CalendarContract.Calendars.CAL_ACCESS_ROOT,
                CalendarContract.Calendars.CALENDAR_TIME_ZONE to "Europe/Vienna",
                CalendarContract.Calendars.SYNC_EVENTS to 1      // required for numInstances!
            )
        ).also { calendar ->
            logger.fine("Created test calendar: #${calendar.id}")
        }
    }

    /** Returns `Instant.now`, but aligned to second resolution because
     * the calendar provider of Android <= 11 doesn't handle milliseconds well.
     *
     * It fails to match exceptions with their original event.
     *
     * Use this value for instead of `Instant.now()` or `System.currentTimeMillis()`
     * when testing the calendar provider!
     */
    fun nowAlignedToSecond(): Instant =
        Instant.now().truncatedTo(ChronoUnit.SECONDS)

}