/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Entity
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.EventsEntity
import android.provider.CalendarContract.Instances
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.mapping.calendar.LegacyAndroidEventBuilder
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.toContentValues
import java.util.LinkedList

/**
 * Represents a locally stored calendar, containing [AndroidEvent]s (whose data objects are [at.bitfire.ical4android.Event]s).
 *
 * Manages locally stored events (and other data like instances of events) in the Android calendar provider.
 *
 * @param client    calendar provider
 * @param values    content values as read from the calendar provider; [Calendars._ID] must be set
 *
 * @throws IllegalArgumentException when [Calendars._ID] is not set
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

    fun createEventFromDataObject(event: Event): Long {
        val batch = CalendarBatchOperation(client)

        val builder = LegacyAndroidEventBuilder(this, event)
        val idxEvent = builder.addOrUpdateRows(event, batch) ?: throw AssertionError("Expected Events._ID backref")
        batch.commit()

        val resultUri = batch.getResult(idxEvent)?.uri
            ?: throw LocalStorageException("Empty result from content provider when adding event")

        return ContentUris.parseId(resultUri)
    }

    fun createAndGetEventFromDataObject(event: Event): AndroidEvent {
        val id = createEventFromDataObject(event)
        return getEvent(id) ?: throw LocalStorageException("Created event not available")
    }

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
            iterateEventEntities(null, protectedWhere, protectedWhereArgs) { event ->
                events += AndroidEvent(this, event)
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
        val values = getEventEntity(id) ?: return null
        return AndroidEvent(this, values)
    }

    /**
     * Gets the event row + data rows of a specific event, identified by its ID, from this calendar.
     *
     * @param id    event ID
     * @return event row + data rows (or `null` if not found)
     */
    fun getEventEntity(id: Long, projection: Array<String>? = null, where: String? = null, whereArgs: Array<String>? = null): Entity? {
        try {
            client.query(eventUri(id), projection, where, whereArgs, null)?.use { cursor ->
                val entityIterator = EventsEntity.newEntityIterator(cursor, client)
                try {
                    if (entityIterator.hasNext())
                        return entityIterator.next()
                } finally {
                    entityIterator.close()
                }
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query event entity", e)
        }
        return null
    }

    /**
     * Gets the main event row of a specific event, identified by its ID, from this calendar.
     *
     * @param id    event ID
     * @return event row (or `null` if not found)
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
     * Iterates event entites from this calendar.
     *
     * Adds a WHERE clause that restricts the query to [CalendarContract.EventsColumns.CALENDAR_ID] = [id].
     *
     * @param projection    requested fields
     * @param where         selection
     * @param whereArgs     arguments for selection
     */
    fun iterateEventEntities(projection: Array<String>?, where: String?, whereArgs: Array<String>?, body: (Entity) -> Unit) {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCalendarId(where, whereArgs)
            client.query(eventsUri, projection, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                val entityIterator = EventsEntity.newEntityIterator(cursor, client)
                try {
                    while (entityIterator.hasNext())
                        body(entityIterator.next())
                } finally {
                    entityIterator.close()
                }
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't iterate events", e)
        }
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

    /**
     * Updates events in this calendar.
     *
     * @param values        values to update
     * @param where         selection
     * @param whereArgs     arguments for selection
     *
     * @return number of updated rows
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateEvents(values: ContentValues, where: String?, whereArgs: Array<String>?): Int =
        try {
            client.update(eventsUri, values, where, whereArgs)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update events", e)
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


    // event instances

    /**
     * Finds the amount of direct instances this event has (without exceptions); used by [numInstances]
     * to find the number of instances of exceptions.
     *
     * The number of returned instances may vary with the Android version.
     *
     * @return number of direct event instances (not counting instances of exceptions); *null* if
     * the number can't be determined or if the event has no last date (recurring event without last instance)
     */
    fun numDirectInstances(eventId: Long): Int? {
        // query event to get first and last instance
        var first: Long? = null
        var last: Long? = null
        getEventValues(eventId, arrayOf(Events.DTSTART, Events.LAST_DATE))?.let { event ->
            first = event.getAsLong(Events.DTSTART)
            last = event.getAsLong(Events.LAST_DATE)
        }
        // if this event doesn't have a last occurrence, it's endless and always has instances
        if (first == null || last == null)
            return null

        /* Query instances of the event within a given start and end. We can't use Long.MIN_VALUE and
         Long.MAX_VALUE because the calendar provider generates the instances on the fly and doesn't accept those
         values. So we use the first/last actual occurrence of the event (as calculated by the provider). */
        val instancesUri = Instances.CONTENT_URI.asSyncAdapter(account).buildUpon()
            .appendPath(first.toString())       // begin timestamp
            .appendPath(last.toString())        // end timestamp
            .build()

        var numInstances = 0
        client.query(
            instancesUri, null,
            "${Instances.EVENT_ID}=?", arrayOf(eventId.toString()),
            null
        )?.use { cursor ->
            numInstances += cursor.count
        }
        return numInstances
    }

    /**
     * Finds the total number of instances this event has (including instances of exceptions).
     *
     * The number of returned instances may vary with the Android version.
     *
     * @return number of direct event instances (not counting instances of exceptions); *null* if
     * the number can't be determined or if the event has no last date (recurring event without last instance)
     */
    fun numInstances(eventId: Long): Int? {
        // num instances of the main event
        var numInstances: Int? = numDirectInstances(eventId) ?: return null

        // add the number of instances of every main event's exception
        iterateEvents(arrayOf(Events._ID), "${Events.ORIGINAL_ID}=?", arrayOf(eventId.toString())) { event ->
            val exceptionEventId = event.getAsLong(Events._ID)
            val exceptionInstances = numDirectInstances(exceptionEventId)

            numInstances = if (exceptionInstances == null)
                null    // number of instances of exception can't be determined; so the total number of instances is also unclear
            else
                numInstances?.plus(exceptionInstances)
        }
        return numInstances
    }


    // helpers

    val account
        get() = provider.account

    val client
        get() = provider.client

    val eventEntitiesUri
        get() = EventsEntity.CONTENT_URI.asSyncAdapter(account)

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