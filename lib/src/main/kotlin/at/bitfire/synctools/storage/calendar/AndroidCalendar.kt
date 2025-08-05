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
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.EventsEntity
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.toContentValues
import java.util.LinkedList

/**
 * Represents a locally stored calendar, containing [AndroidEvent2] objects.  Communicates with
 * the Android Contacts Provider which uses an SQLite database to store the events.
 *
 * Methods that use [ContentValues] operate directly on rows of the [Events] table.
 * Methods that use [Entity] operate on [EventsEntity] URIs to access the [Events] rows together with
 * associated data rows (reminders, attendees etc.)
 *
 * @param client    calendar provider
 * @param values    content values as read from the calendar provider; [android.provider.BaseColumns._ID] must be set
 *
 * @throws IllegalArgumentException when [Calendars._ID] is not set
 */
class AndroidCalendar(
    internal val provider: AndroidCalendarProvider,
    private val values: ContentValues
) {

    /** see [Calendars._ID] */
    val id: Long = values.getAsLong(Calendars._ID)
        ?: throw IllegalArgumentException("Calendars._ID must be available")


    // data fields

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
     * Inserts an event to the calendar provider.
     *
     * @param entity    event to insert
     *
     * @return ID of the new event
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun addEvent(entity: Entity): Long {
        try {
            val batch = CalendarBatchOperation(client)
            addEvent(entity, batch)
            batch.commit()

            val uri = batch.getResult(0)?.uri ?: throw LocalStorageException("Content provider returned null on insert")
            return ContentUris.parseId(uri)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't insert event", e)
        }
    }

    internal fun addEvent(entity: Entity, batch: CalendarBatchOperation) {
        // insert main row
        batch += CpoBuilder.newInsert(eventsUri).withValues(entity.entityValues)

        // insert data rows (with reference to main row ID)
        for (row in entity.subValues)
            batch += CpoBuilder.newInsert(row.uri.asSyncAdapter(account))
                .withValues(row.values)
                .withValueBackReference(AndroidEvent2.DATA_ROW_EVENT_ID, /* result of first operation with index = */ 0)
    }

    /**
     * Gets the first event from this calendar that matches the given query.
     *
     * Adds a WHERE clause that restricts the query to [CalendarContract.EventsColumns.CALENDAR_ID] = [id].
     *
     * @param where     selection
     * @param whereArgs arguments for selection
     * @param sortOrder sort oder
     *
     * @return first event from this calendar that matches the selection, or `null` if none found
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findEvent(where: String?, whereArgs: Array<String>?, sortOrder: String? = null): AndroidEvent2? {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCalendarId(where, whereArgs)
            client.query(eventEntitiesUri, null, protectedWhere, protectedWhereArgs, sortOrder)?.use { cursor ->
                val iter = EventsEntity.newEntityIterator(cursor, client)
                if (iter.hasNext())
                    return AndroidEvent2(this, iter.next())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query events", e)
        }
        return null
    }

    /**
     * Queries events from this calendar.
     *
     * Adds a WHERE clause that restricts the query to [CalendarContract.EventsColumns.CALENDAR_ID] = [id].
     *
     * @param where     selection
     * @param whereArgs arguments for selection
     *
     * @return events from this calendar which match the selection
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findEvents(where: String?, whereArgs: Array<String>?) =
        findEventEntities(where, whereArgs).map { entity ->
            AndroidEvent2(this, entity)
        }

    /**
     * Queries events from this calendar.
     *
     * Adds a WHERE clause that restricts the query to [CalendarContract.EventsColumns.CALENDAR_ID] = [id].
     *
     * @param where     selection
     * @param whereArgs arguments for selection
     *
     * @return events from this calendar which match the selection
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findEventEntities(where: String?, whereArgs: Array<String>?): List<Entity> {
        val entities = LinkedList<Entity>()
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCalendarId(where, whereArgs)
            client.query(eventEntitiesUri, null, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                for (entity in EventsEntity.newEntityIterator(cursor, client))
                    entities += entity
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query events", e)
        }
        return entities
    }

    /**
     * Gets the first event row that matches the given query.
     *
     * @return first event row that matches [where]/[whereArgs] (or `null` if not found)
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun findEventRow(projection: Array<String>?, where: String?, whereArgs: Array<String>?): ContentValues? {
        try {
            client.query(eventsUri, projection, where, whereArgs, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.toContentValues()
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query event rows", e)
        }
        return null
    }

    /**
     * Gets a specific event, identified by its ID, from this calendar.
     *
     * @param id    event ID
     *
     * @return event (or `null` if not found)
     */
    fun getEvent(id: Long): AndroidEvent2? {
        val values = getEventEntity(id) ?: return null
        return AndroidEvent2(this, values)
    }

    fun getEventEntity(id: Long, where: String? = null, whereArgs: Array<String>? = null): Entity? {
        try {
            client.query(eventEntityUri(id), null, where, whereArgs, null)?.use { cursor ->
                val iterator = EventsEntity.newEntityIterator(cursor, client)
                if (iterator.hasNext())
                    return iterator.next()
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query event entity", e)
        }
        return null
    }

    /**
     * Gets the event row of a specific event, identified by its ID, from this calendar.
     *
     * @param id    event ID
     *
     * @return event row (or `null` if not found)
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun getEventRow(id: Long, projection: Array<String>? = null, where: String? = null, whereArgs: Array<String>? = null): ContentValues? {
        try {
            client.query(eventUri(id), projection, where, whereArgs, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.toContentValues()
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query event row", e)
        }
        return null
    }

    /**
     * Iterates event rows from this calendar.
     *
     * Adds a WHERE clause that restricts the query to [CalendarContract.EventsColumns.CALENDAR_ID] = [id].
     *
     * @param projection    requested fields
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param body          callback that is called for each main row
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun iterateEventRows(projection: Array<String>?, where: String?, whereArgs: Array<String>?, body: (ContentValues) -> Unit) {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCalendarId(where, whereArgs)
            client.query(eventsUri, projection, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    body(cursor.toContentValues())
                }
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't iterate event rows", e)
        }
    }

    /**
     * Iterates event entities from this calendar.
     *
     * Adds a WHERE clause that restricts the query to [CalendarContract.EventsColumns.CALENDAR_ID] = [id].
     *
     * @param where         selection
     * @param whereArgs     arguments for selection
     * @param body          callback that is called for each entity
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun iterateEvents(where: String?, whereArgs: Array<String>?, body: (Entity) -> Unit) {
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithCalendarId(where, whereArgs)
            client.query(eventEntitiesUri, null, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                val iterator = EventsEntity.newEntityIterator(cursor, client)
                for (entity in iterator)
                    body(entity)
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't iterate events", e)
        }
    }

    /**
     * Updates a specific event's main row with the given values. Doesn't influence data rows.
     *
     * This method always uses the update method of the content provider and does not
     * re-create rows, as it is required for some operations (see [updateEvent] and [eventUpdateNeedsRebuild]
     * for more information).
     *
     * @param id        event ID
     * @param values    new values
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateEventRow(id: Long, values: ContentValues) {
        try {
            client.update(eventUri(id), values, null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update event row $id", e)
        }
    }

    fun updateEvent(id: Long, entity: Entity): Long {
        try {
            val rebuild = eventUpdateNeedsRebuild(id, entity.entityValues) ?: true
            if (rebuild) {
                deleteEvent(id)
                return addEvent(entity)
            }

            // remove existing data rows which are created by us (don't touch 3rd-party calendar apps rows)
            val batch = CalendarBatchOperation(client)
            updateEvent(id, entity, batch)
            batch.commit()

            return id
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update event $id", e)
        }
    }

    internal fun updateEvent(id: Long, entity: Entity, batch: CalendarBatchOperation) {
        deleteDataRows(id, batch)

        // update main row
        batch += CpoBuilder.newUpdate(eventUri(id))
            .withValues(ContentValues(entity.entityValues).apply {
                remove(Events._ID)  // don't update ID
            })

        // insert data rows (with reference to main row ID)
        for (row in entity.subValues)
            batch += CpoBuilder.newInsert(row.uri.asSyncAdapter(account))
                .withValues(ContentValues(row.values).apply {
                    put(AndroidEvent2.DATA_ROW_EVENT_ID, id)      // always keep reference to main row ID
                })
    }

    /**
     * Deletes data rows from events, but only those with a known CONTENT_URI that we are also able to
     * build. This should prevent accidental deletion of unknown data rows like they may be used by calendar
     * apps to for instance tag events in the UI.
     */
    private fun deleteDataRows(eventId: Long, batch: CalendarBatchOperation) {
        batch += CpoBuilder
            .newDelete(Reminders.CONTENT_URI.asSyncAdapter(account))
            .withSelection("${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()))
        batch += CpoBuilder
            .newDelete(Attendees.CONTENT_URI.asSyncAdapter(account))
            .withSelection("${Attendees.EVENT_ID}=?", arrayOf(eventId.toString()))
        batch += CpoBuilder
            .newDelete(ExtendedProperties.CONTENT_URI.asSyncAdapter(account))
            .withSelection(
                "${ExtendedProperties.EVENT_ID}=? AND ${ExtendedProperties.NAME} IN (?,?,?,?)",
                arrayOf(
                    eventId.toString(),
                    AndroidEvent2.EXTNAME_CATEGORIES,
                    AndroidEvent2.EXTNAME_ICAL_UID,       // UID is stored in UID_2445, don't leave iCalUid rows in events that we have written
                    AndroidEvent2.EXTNAME_URL,
                    UnknownProperty.CONTENT_ITEM_TYPE
                )
            )
    }

    /**
     * There is a bug in the calendar provider that prevent events from being updated from a non-null STATUS value
     * to STATUS=null (see AndroidCalendarProviderBehaviorTest.testUpdateEventStatusToNull).
     *
     * In that case we can't update the event, so we completely re-create it.
     *
     * @param id            event of existing ID
     * @param newValues     new values that the event shall be updated to
     *
     * @return whether the event can't be updated/needs to be re-created; or `null` if existing values couldn't be determined
     */
    internal fun eventUpdateNeedsRebuild(id: Long, newValues: ContentValues): Boolean? {
        val existingValues = getEventRow(id, arrayOf(Events.STATUS)) ?: return null
        return existingValues.getAsInteger(Events.STATUS) != null && newValues.getAsInteger(Events.STATUS) == null
    }

    /**
     * Updates event rows in this calendar.
     *
     * @param values        values to update
     * @param where         selection
     * @param whereArgs     arguments for selection
     *
     * @return number of updated rows
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateEventRows(values: ContentValues, where: String?, whereArgs: Array<String>?): Int =
        try {
            client.update(eventsUri, values, where, whereArgs)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update events", e)
        }

    /**
     * Deletes an event row.
     *
     * The content provider automatically deletes associated data rows, but doesn't touch exceptions.
     *
     * @param id    ID of the event
     */
    fun deleteEvent(id: Long) {
        try {
            client.delete(eventUri(id), null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete event $id", e)
        }
    }



    // event instances (these methods operate directly with event IDs and without the events
    // themselves and thus belong to the calendar class)

    /**
     * Finds the amount of instances this event has. Exceptions generate their own instances and are
     * not taken into account by this method.
     *
     * Use [numInstances] to find the total number of instances (including exceptions) of this event.
     *
     * @return number of event instances (not counting instances generated by exceptions); *null* if
     * the number can't be determined or if the event has no last date (recurring event without last instance)
     *
     * @throws LocalStorageException when the content provider returns an error
     */
    fun numDirectInstances(eventId: Long): Int? {
        // query event to get first and last instance
        var first: Long? = null
        var last: Long? = null
        getEventRow(eventId, arrayOf(Events.DTSTART, Events.LAST_DATE))?.let { values ->
            first = values.getAsLong(Events.DTSTART)
            last = values.getAsLong(Events.LAST_DATE)
        }
        // if this event doesn't have a last occurrence, it's endless and always has instances
        if (first == null || last == null)
            return null

        /* We can't use Long.MIN_VALUE and Long.MAX_VALUE because Android generates the instances
         on the fly and it doesn't accept those values. So we use the first/last actual occurrence
         of the event (as calculated by Android). */
        val instancesUri = Instances.CONTENT_URI.asSyncAdapter(account)
            .buildUpon()
            .appendPath(first.toString())       // begin timestamp
            .appendPath(last.toString())        // end timestamp
            .build()

        var numInstances: Int? = null
        try {
            client.query(
                instancesUri, arrayOf(/* we're only interested in the number of results */),
                "${Instances.EVENT_ID}=?", arrayOf(eventId.toString()),
                null
            )?.use { cursor ->
                numInstances = cursor.count
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query number of instances for event $eventId", e)
        }
        return numInstances
    }

    fun numInstances(eventId: Long): Int? {
        val numDirectInstances = numDirectInstances(eventId) ?: return null

        // add instances generated by exceptions
        var numExInstances = 0
        iterateEventRows(
            arrayOf(Events._ID),
            "${Events.ORIGINAL_ID}=?", arrayOf(eventId.toString())
        ) { exception ->
            val exceptionId = exception.getAsLong(Events._ID)
            // an exception can have 0 instances (if cancelled) or 1 instance (but it can't be recurring)
            numExInstances += numDirectInstances(exceptionId) ?: 0
        }

        return numDirectInstances + numExInstances
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

    val eventEntitiesUri
        get() = EventsEntity.CONTENT_URI.asSyncAdapter(account)

    fun eventEntityUri(id: Long) =
        ContentUris.withAppendedId(eventEntitiesUri, id)

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