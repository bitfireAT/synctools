/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.toContentValues
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

    val id: Long = values.getAsLong(Calendars._ID)
        ?: throw IllegalArgumentException("${Calendars._ID} must be set")

    val accessLevel: Int
        get() = values.getAsInteger(Calendars.CALENDAR_ACCESS_LEVEL) ?: 0

    val displayName: String?
        get() = values.getAsString(Calendars.CALENDAR_DISPLAY_NAME)

    val name: String?
        get() = values.getAsString(Calendars.NAME)

    val ownerAccount: String?
        get() = values.getAsString(Calendars.OWNER_ACCOUNT)

    val syncId: String?
        get() = values.getAsString(Calendars._SYNC_ID)


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
        val events = LinkedList<AndroidEvent>()
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCalendarId(where, whereArgs)
            client.query(eventsUri, null, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                while (cursor.moveToNext())
                    events += AndroidEvent(this, cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query events", e)
        }
        return events
    }

    fun getEvent(id: Long): AndroidEvent? {
        val values = getEventValues(id) ?: return null
        return AndroidEvent(this, values)
    }

    fun getEventValues(id: Long, projection: Array<String>? = null, where: String? = null, whereArgs: Array<String>? = null): ContentValues? {
        try {
            client.query(eventUri(id), projection, where, whereArgs, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.toContentValues()
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query event", e)
        }
        return null
    }

    /**
     * Iterates events from this calendar.
     *
     * Adds a WHERE clause that restricts the query to [CalendarContract.EventsColumns.CALENDAR_ID] = [id].
     *
     * @param projection    requested fields
     * @param where         selection
     * @param whereArgs     arguments for selection
     *
     * @return event IDs from this calendar which match the selection
     */
    fun iterateEvents(projection: Array<String>, where: String?, whereArgs: Array<String>?, body: (ContentValues) -> Unit) {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCalendarId(where, whereArgs)
            client.query(eventsUri, projection, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    body(cursor.toContentValues())
                }
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't iterate events", e)
        }
    }

    fun updateEvents(values: ContentValues, where: String?, whereArgs: Array<String>?): Int =
        try {
            client.update(eventsUri, values, where, whereArgs)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update events", e)
        }


    // shortcuts to upper level

    fun delete() = provider.deleteCalendar(id)

    fun readSyncState() = provider.readCalendarSyncState(id)
    fun writeSyncState(newState: String?) {
        provider.writeCalendarSyncState(id, newState)
    }


    // helpers

    val account
        get() = provider.account

    val client
        get() = provider.client

    val eventsUri
        get() = Events.CONTENT_URI.asSyncAdapter(account)

    fun eventUri(id: Long) =
        ContentUris.withAppendedId(eventsUri, id)

    private fun whereWithCalendarId(where: String?, whereArgs: Array<String>?): Pair<String, Array<String>> {
        val protectedWhere = "(${where ?: "1"}) AND " + Events.CALENDAR_ID + "=?"
        val protectedWhereArgs = (whereArgs ?: arrayOf()) + id.toString()
        return Pair(protectedWhere, protectedWhereArgs)
    }

}