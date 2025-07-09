/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.impl

import android.accounts.Account
import android.content.ContentProviderClient
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Reminders
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import java.util.UUID

object TestCalendar {

    fun findOrCreate(account: Account, client: ContentProviderClient): AndroidCalendar {
        val provider = AndroidCalendarProvider(account, client)

        // we use colors for testing
        provider.provideCss3ColorIndices()

        return provider.findFirstCalendar( null, null)
            ?: provider.createAndGetCalendar(contentValuesOf(
                Calendars.NAME to UUID.randomUUID().toString(),
                Calendars.CALENDAR_DISPLAY_NAME to "ical4android Test Calendar",
                Calendars.ALLOWED_REMINDERS to Reminders.METHOD_DEFAULT)
            )
    }

}