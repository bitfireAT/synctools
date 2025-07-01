/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.impl

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import at.bitfire.synctools.storage.calendar.AndroidCalendar

object TestCalendar {

    fun findOrCreate(account: Account, provider: ContentProviderClient): AndroidCalendar {
        val calendars = AndroidCalendar.find(account, provider, null, null)
        return if (calendars.isEmpty()) {
            val values = ContentValues(3)
            values.put(CalendarContract.Calendars.NAME, "TestCalendar")
            values.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "ical4android Test Calendar")
            values.put(CalendarContract.Calendars.ALLOWED_REMINDERS, CalendarContract.Reminders.METHOD_DEFAULT)
            val uri = AndroidCalendar.create(account, provider, values)

            AndroidCalendar(account, provider, ContentUris.parseId(uri))
        } else
            calendars.first()
    }

}