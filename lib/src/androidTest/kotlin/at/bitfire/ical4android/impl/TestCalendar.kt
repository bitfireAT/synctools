/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.impl

import android.accounts.Account
import android.content.ContentProviderClient
import android.provider.CalendarContract.Calendars
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
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

        return provider.createAndGetCalendar(contentValuesOf(
            Calendars.NAME to UUID.randomUUID().toString(),
            Calendars.CALENDAR_DISPLAY_NAME to "ical4android Test Calendar",
            Calendars.CALENDAR_ACCESS_LEVEL to Calendars.CAL_ACCESS_ROOT,
            Calendars.CALENDAR_TIME_ZONE to "Europe/Vienna",
            Calendars.SYNC_EVENTS to 1      // required for numInstances!
        )).also { calendar ->
            logger.fine("Created test calendar: #${calendar.id}")
        }
    }

}