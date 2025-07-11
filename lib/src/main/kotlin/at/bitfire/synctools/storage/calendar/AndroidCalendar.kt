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

    /** see [Calendars._ID] */
    val id: Long = values.getAsLong(Calendars._ID)
        ?: throw IllegalArgumentException("${Calendars._ID} must be set")

    /** see [Calendars.CALENDAR_ACCESS_LEVEL] */
    val accessLevel: Int
        get() = values.getAsInteger(Calendars.CALENDAR_ACCESS_LEVEL) ?: 0

    /** see [Calendars.CALENDAR_DISPLAY_NAME] */
    val displayName: String?
        get() = values.getAsString(Calendars.CALENDAR_DISPLAY_NAME)

    /** see [Calendars.NAME] */
    val name: String?
        get() = values.getAsString(Calendars.NAME)

    /** see [Calendars.OWNER_ACCOUNT] */
    val ownerAccount: String?
        get() = values.getAsString(Calendars.OWNER_ACCOUNT)

    /** see [Calendars._SYNC_ID] */
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
     * @throws LocalStorageException when the content provider returns an error
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

    /**
     * Gets a specific event, identified by its ID, from this calendar.
     *
     * @param id    event ID
     * @return event (or `null` if not found)
     */
    fun getEvent(id: Long): AndroidEvent? {
        val values = getEventValues(id) ?: return null
        return AndroidEvent(this, values)
    }

    /**
     * Gets the main event row of a specific event, identified by its ID, from this calendar.
     *
     * @param id    event ID
     *
     * @return event row (or `null` if not found)
     * @throws LocalStorageException when the content provider returns an error
     */
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
     * @throws LocalStorageException when the content provider returns an error
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

    /**
     * Updates a specific event's main row with the given values.
     *
     * @param id        event ID
     * @param values    new values
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateEvent(id: Long, values: ContentValues) {
        try {
            client.update(eventUri(id), values, null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update event $id", e)
        }
    }

    /**
     * Updates events in this calendar.
     *
     * @param values        values to update
     * @param where         selection
     * @param whereArgs     arguments for selection
     *
     * @return number of updated rows
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateEvents(values: ContentValues, where: String?, whereArgs: Array<String>?): Int =
        try {
            client.update(eventsUri, values, where, whereArgs)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update events", e)
        }

    // event instances (these methods operate directly with event IDs and without the events themselves and thus belong to the calendar class)

    /**
     * Finds the amount of direct instances this event has (without exceptions); used by [numInstances]
     * to find the number of instances of exceptions.
     *
     * The number of returned instances may vary with the Android version.
     *
     * @return number of direct event instances (not counting instances of exceptions); *null* if
     * the number can't be determined or if the event has no last date (recurring event without last instance)
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun numDirectInstances(eventId: Long): Int? {
        // query event to get first and last instance
        var first: Long? = null
        var last: Long? = null
        client.query(
            eventUri(eventId),
            arrayOf(Events.DTSTART, Events.LAST_DATE), null, null, null
        )?.use { cursor ->
            cursor.moveToNext()
            if (!cursor.isNull(0))
                first = cursor.getLong(0)
            if (!cursor.isNull(1))
                last = cursor.getLong(1)
        }
        // if this event doesn't have a last occurrence, it's endless and always has instances
        if (first == null || last == null)
            return null

        /* We can't use Long.MIN_VALUE and Long.MAX_VALUE because Android generates the instances
         on the fly and it doesn't accept those values. So we use the first/last actual occurence
         of the event (calculated by Android). */
        val instancesUri = CalendarContract.Instances.CONTENT_URI.asSyncAdapter(account)
            .buildUpon()
            .appendPath(first.toString())       // begin timestamp
            .appendPath(last.toString())        // end timestamp
            .build()

        var numInstances = 0
        try {
            client.query(
                instancesUri, null,
                "${CalendarContract.Instances.EVENT_ID}=?", arrayOf(eventId.toString()),
                null
            )?.use { cursor ->
                numInstances += cursor.count
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query number of instances for event $eventId", e)
        }
        return numInstances
    }

    /**
     * Finds the total number of instances this event has (including instances of exceptions)
     *
     * The number of returned instances may vary with the Android version.
     *
     * @return number of event instances (including instances of exceptions); *null* if
     * the number can't be determined or if the event has no last date (recurring event without last instance)
     */
    fun numInstances(eventId: Long): Int? {
        // num instances of the main event
        var numInstances = numDirectInstances(eventId) ?: return null

        // add the number of instances of every main event's exception
        try {
            client.query(
                Events.CONTENT_URI,
                arrayOf(Events._ID),
                "${Events.ORIGINAL_ID}=?", // get exception events of the main event
                arrayOf(eventId.toString()), null
            )?.use { exceptionsEventCursor ->
                while (exceptionsEventCursor.moveToNext()) {
                    val exceptionEventId = exceptionsEventCursor.getLong(0)
                    val exceptionInstances = numDirectInstances(exceptionEventId)

                    if (exceptionInstances == null)
                        return null   // number of instances of exception can't be determined; so the total number of instances is also unclear

                    numInstances += exceptionInstances
                }
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query number of exception instances for event $eventId", e)
        }
        return numInstances
    }


    // shortcuts to upper level

    /** Calls [AndroidCalendarProvider.deleteCalendar] for this calendar. */
    fun delete() = provider.deleteCalendar(id)

    /**
     * Calls [AndroidCalendarProvider.updateCalendar] for this calendar.
     *
     * **Attention**: Does not update this object with the new values!
     * */
    fun update(values: ContentValues) =
        provider.updateCalendar(id, values)

    /** Calls [AndroidCalendarProvider.readCalendarSyncState] for this calendar. */
    fun readSyncState() = provider.readCalendarSyncState(id)

    /** Calls [AndroidCalendarProvider.writeCalendarSyncState] for this calendar. */
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

    /**
     * Restricts a given selection/where clause to this calendar ID.
     *
     * @param where      selection
     * @param whereArgs  arguments for selection
     * @return           restricted selection and arguments
     */
    private fun whereWithCalendarId(where: String?, whereArgs: Array<String>?): Pair<String, Array<String>> {
        val protectedWhere = "(${where ?: "1"}) AND " + Events.CALENDAR_ID + "=?"
        val protectedWhereArgs = (whereArgs ?: arrayOf()) + id.toString()
        return Pair(protectedWhere, protectedWhereArgs)
    }

}