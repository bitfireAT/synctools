/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.content.ContentValues
import android.provider.CalendarContract
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import toContentValues
import java.util.LinkedList

/**
 * Represents a locally stored calendar, containing [at.bitfire.ical4android.AndroidEvent]s (whose data objects are [at.bitfire.ical4android.Event]s).
 * Communicates with the Android Contacts Provider which uses an SQLite
 * database to store the events.
 *
 * @param client  calendar provider
 * @param values    content values as read from the calendar provider; [android.provider.BaseColumns._ID] must be set
 *
 * @throws IllegalArgumentException when [android.provider.BaseColumns._ID] is not set
 */
class AndroidCalendar(
    val provider: AndroidCalendarProvider,
    val values: ContentValues
) {

    val id: Long = values.getAsLong(CalendarContract.Calendars._ID)
        ?: throw IllegalArgumentException("${CalendarContract.Calendars._ID} must be set")

    /*val name: String?
        get() = values.getAsString(Calendars.NAME)*/


    // CRUD AndroidEvent

    /**
     * Queries events from this calendar.
     *
     * Adds a WHERE clause that restricts the query to [CalendarContract.EventsColumns.CALENDAR_ID] = [id].
     *
     * @param where selection
     * @param whereArgs arguments for selection
     *
     * @return events from this calendar which match the selection
     */
    fun findEvents(where: String?, whereArgs: Array<String>?): List<AndroidEvent> {
        val whereWithId = "(${where ?: "1"}) AND " + CalendarContract.Events.CALENDAR_ID + "=?"
        val whereArgsWithId = (whereArgs ?: arrayOf()) + id.toString()

        val events = LinkedList<AndroidEvent>()
        client.query(eventsUri, null, whereWithId, whereArgsWithId, null)?.use { cursor ->
            while (cursor.moveToNext())
                events += AndroidEvent(this, cursor.toContentValues())
        }
        return events
    }


    // shortcuts to upper level

    fun delete() = provider.deleteCalendar(id)


    // helpers

    val account
        get() = provider.account

    val client
        get() = provider.client

    val eventsUri
        get() = CalendarContract.Events.CONTENT_URI.asSyncAdapter(account)

}