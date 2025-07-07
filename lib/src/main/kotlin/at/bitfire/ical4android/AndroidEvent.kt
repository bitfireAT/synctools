/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.EntityIterator
import android.net.Uri
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.EventsEntity
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Reminders
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.AndroidEvent.Companion.CATEGORIES_SEPARATOR
import at.bitfire.ical4android.AndroidEvent.Companion.numInstances
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.mapping.calendar.AndroidEventBuilder
import at.bitfire.synctools.mapping.calendar.AndroidEventProcessor
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.CalendarBatchOperation
import net.fortuna.ical4j.model.property.Clazz
import java.io.FileNotFoundException
import java.util.logging.Logger

/**
 * Stores and retrieves VEVENT iCalendar objects (represented as [Event]s) to/from the
 * Android Calendar provider.
 *
 * Extend this class to process specific fields of the event.
 *
 * Important: To use recurrence exceptions, you MUST set _SYNC_ID and ORIGINAL_SYNC_ID
 * in populateEvent() / buildEvent. Setting _ID and ORIGINAL_ID is not sufficient.
 */
class AndroidEvent(
    val calendar: AndroidCalendar
) {

    private val logger: Logger
        get() = Logger.getLogger(javaClass.name)

    var id: Long? = null
        private set

    var syncId: String? = null

    var eTag: String? = null
    var scheduleTag: String? = null
    var flags: Int = 0

    /**
     * Creates a new object from an event which already exists in the calendar storage.
     *
     * @param values database row with all columns, as returned by the calendar provider
     */
    constructor(calendar: AndroidCalendar, values: ContentValues) : this(calendar) {
        this.id = values.getAsLong(Events._ID)
        this.syncId = values.getAsString(Events._SYNC_ID)
        this.eTag = values.getAsString(COLUMN_ETAG)
        this.scheduleTag = values.getAsString(COLUMN_SCHEDULE_TAG)
        this.flags = values.getAsInteger(COLUMN_FLAGS) ?: 0
    }

    /**
     * Creates a new object from an event which doesn't exist in the calendar storage yet.
     *
     * @param event event that can be saved into the calendar storage
     */
    constructor(
        calendar: AndroidCalendar,
        event: Event,
        syncId: String?,
        eTag: String? = null,
        scheduleTag: String? = null,
        flags: Int = 0
    ) : this(calendar) {
        this.event = event
        this.syncId = syncId
        this.eTag = eTag
        this.scheduleTag = scheduleTag
        this.flags = flags
    }

    private var _event: Event? = null

    /**
     * Returns the full event data, either from [event] or, if [event] is null, by reading event
     * number [id] from the Android calendar storage
     * @throws IllegalArgumentException if event has not been saved yet
     * @throws FileNotFoundException if there's no event with [id] in the calendar storage
     * @throws RemoteException on calendar provider errors
     */
    var event: Event?
        private set(value) {
            _event = value
        }
        get() {
            if (_event != null)
                return _event
            val id = requireNotNull(id)

            var iterEvents: EntityIterator? = null
            try {
                iterEvents = EventsEntity.newEntityIterator(
                        calendar.client.query(
                                ContentUris.withAppendedId(EventsEntity.CONTENT_URI, id).asSyncAdapter(calendar.account),
                                null, null, null, null),
                        calendar.client
                )

                if (iterEvents.hasNext()) {
                    val entity = iterEvents.next()
                    return Event().also { newEvent ->
                        val processor = AndroidEventProcessor(calendar, id, entity)
                        processor.populate(to = newEvent)

                        _event = newEvent
                    }
                }
            } catch (e: Exception) {
                /* Populating event has been interrupted by an exception, so we reset the event to
                avoid an inconsistent state. This also ensures that the exception will be thrown
                again on the next get() call. */
                _event = null
                throw e
            } finally {
                iterEvents?.close()
            }

            throw FileNotFoundException("Couldn't find event $id")
        }


    /**
     * Saves the unsaved [event] into the calendar storage.
     *
     * @return content URI of the created event
     *
     * @throws LocalStorageException when the calendar provider doesn't return a result row
     * @throws RemoteException on calendar provider errors
     */
    fun add(): Uri {
        val batch = CalendarBatchOperation(calendar.client)

        val requiredEvent = requireNotNull(event)
        val builder = AndroidEventBuilder(calendar, requiredEvent, id, syncId, eTag, scheduleTag, flags)
        val idxEvent = builder.addOrUpdateRows(requiredEvent, batch) ?: throw AssertionError("Expected Events._ID backref")
        batch.commit()

        val resultUri = batch.getResult(idxEvent)?.uri
            ?: throw LocalStorageException("Empty result from content provider when adding event")
        id = ContentUris.parseId(resultUri)
        return resultUri
    }

    /**
     * Updates an already existing event in the calendar storage with the values
     * from the instance.
     * @throws LocalStorageException when the calendar provider doesn't return a result row
     * @throws RemoteException on calendar provider errors
     */
    fun update(event: Event): Uri {
        this.event = event
        val existingId = requireNotNull(id)

        // There are cases where the event cannot be updated, but must be completely re-created.
        // Case 1: Events.STATUS shall be updated from a non-null value (like STATUS_CONFIRMED) to null.
        var rebuild = false
        if (event.status == null)
            calendar.client.query(eventSyncURI(), arrayOf(Events.STATUS), null, null, null)?.use { cursor ->
                if (cursor.moveToNext()) {
                    val statusIndex = cursor.getColumnIndexOrThrow(Events.STATUS)
                    if (!cursor.isNull(statusIndex))
                        rebuild = true
                }
            }

        if (rebuild) {  // delete whole event and insert updated event
            delete()
            return add()

        } else {        // update event
            // remove associated rows which are added later again
            val batch = CalendarBatchOperation(calendar.client)
            deleteExceptions(batch)
            batch += CpoBuilder
                .newDelete(Reminders.CONTENT_URI.asSyncAdapter(calendar.account))
                .withSelection("${Reminders.EVENT_ID}=?", arrayOf(existingId.toString()))
            batch += CpoBuilder
                .newDelete(Attendees.CONTENT_URI.asSyncAdapter(calendar.account))
                .withSelection("${Attendees.EVENT_ID}=?", arrayOf(existingId.toString()))
            batch += CpoBuilder
                .newDelete(ExtendedProperties.CONTENT_URI.asSyncAdapter(calendar.account))
                .withSelection(
                    "${ExtendedProperties.EVENT_ID}=? AND ${ExtendedProperties.NAME} IN (?,?,?,?)",
                    arrayOf(
                        existingId.toString(),
                        EXTNAME_CATEGORIES,
                        EXTNAME_ICAL_UID,       // UID is stored in UID_2445, don't leave iCalUid rows in events that we have written
                        EXTNAME_URL,
                        UnknownProperty.CONTENT_ITEM_TYPE
                    )
                )

            val builder = AndroidEventBuilder(calendar, event, id, syncId, eTag, scheduleTag, flags)
            builder.addOrUpdateRows(event, batch)
            batch.commit()

            return ContentUris.withAppendedId(Events.CONTENT_URI, existingId)
        }
    }

    fun update(values: ContentValues) {
        calendar.client.update(eventSyncURI(), values, null, null)
    }

    /**
     * Deletes an existing event from the calendar storage.
     *
     * @return number of affected rows
     *
     * @throws RemoteException on calendar provider errors
     */
    fun delete(): Int {
        val batch = CalendarBatchOperation(calendar.client)

        // remove exceptions of event, too (CalendarProvider doesn't do this)
        deleteExceptions(batch)

        // remove event and unset known id
        batch += CpoBuilder.newDelete(eventSyncURI())
        id = null

        return batch.commit()
    }

    private fun deleteExceptions(batch: CalendarBatchOperation) {
        val existingId = requireNotNull(id)
        batch += CpoBuilder
            .newDelete(Events.CONTENT_URI.asSyncAdapter(calendar.account))
            .withSelection("${Events.ORIGINAL_ID}=?", arrayOf(existingId.toString()))
    }


    private fun useRetainedClassification() {
        val event = requireNotNull(event)

        var retainedClazz: Clazz? = null
        val it = event.unknownProperties.iterator()
        while (it.hasNext()) {
            val prop = it.next()
            if (prop is Clazz) {
                retainedClazz = prop
                it.remove()
            }
        }

        if (event.classification == null)
            // no classification, use retained one if possible
            event.classification = retainedClazz
    }


    private fun eventSyncURI(): Uri {
        val id = requireNotNull(id)
        return ContentUris.withAppendedId(Events.CONTENT_URI, id).asSyncAdapter(calendar.account)
    }

    override fun toString(): String = "AndroidEvent(calendar=$calendar, id=$id, event=$_event)"


    companion object {

        const val MUTATORS_SEPARATOR = ','

        /**
         * Custom sync column to store the last known ETag of an event.
         */
        const val COLUMN_ETAG = Events.SYNC_DATA1

        /**
         * Custom sync column to store sync flags of an event.
         */
        const val COLUMN_FLAGS = Events.SYNC_DATA2

        /**
         * Custom sync column to store the SEQUENCE of an event.
         */
        const val COLUMN_SEQUENCE = Events.SYNC_DATA3

        /**
         * Custom sync column to store the Schedule-Tag of an event.
         */
        const val COLUMN_SCHEDULE_TAG = Events.SYNC_DATA4

        /**
         * VEVENT CATEGORIES are stored as an extended property with this [ExtendedProperties.NAME].
         *
         * The [ExtendedProperties.VALUE] format is the same as used by the AOSP Exchange ActiveSync adapter:
         * the category values are stored as list, separated by [CATEGORIES_SEPARATOR]. (If a category
         * value contains [CATEGORIES_SEPARATOR], [CATEGORIES_SEPARATOR] will be dropped.)
         *
         * Example: `Cat1\Cat2`
         */
        const val EXTNAME_CATEGORIES = "categories"
        const val CATEGORIES_SEPARATOR = '\\'

        /**
         * Google Calendar uses an extended property called `iCalUid` for storing the event's UID, instead of the
         * standard [Events.UID_2445].
         *
         * @see <a href="https://github.com/bitfireAT/ical4android/issues/125">GitHub Issue</a>
         */
        const val EXTNAME_ICAL_UID = "iCalUid"

        /**
         * VEVENT URL is stored as an extended property with this [ExtendedProperties.NAME].
         * The URL is directly put into [ExtendedProperties.VALUE].
         */
        const val EXTNAME_URL = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.ical4android.url"


        // helpers

        /**
         * Marks the event as deleted
         * @param eventID
         */
        fun markAsDeleted(provider: ContentProviderClient, account: Account, eventID: Long) {
            provider.update(
                ContentUris.withAppendedId(
                    Events.CONTENT_URI,
                    eventID
                ).asSyncAdapter(account),
                contentValuesOf(Events.DELETED to 1),
                null, null
            )
        }

        /**
         * Finds the amount of direct instances this event has (without exceptions); used by [numInstances]
         * to find the number of instances of exceptions.
         *
         * The number of returned instances may vary with the Android version.
         *
         * @return number of direct event instances (not counting instances of exceptions); *null* if
         * the number can't be determined or if the event has no last date (recurring event without last instance)
         */
        fun numDirectInstances(provider: ContentProviderClient, account: Account, eventID: Long): Int? {
            // query event to get first and last instance
            var first: Long? = null
            var last: Long? = null
            provider.query(
                ContentUris.withAppendedId(
                    Events.CONTENT_URI,
                    eventID
                ),
                arrayOf(Events.DTSTART, Events.LAST_DATE), null, null, null
            )?.use { cursor ->
                cursor.moveToNext()
                if (!cursor.isNull(0))
                    first = cursor.getLong(0)
                if (!cursor.isNull(1))
                    last = cursor.getLong(1)
            }
            // if this event doesn't have a last occurence, it's endless and always has instances
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
            provider.query(
                instancesUri, null,
                "${CalendarContract.Instances.EVENT_ID}=?", arrayOf(eventID.toString()),
                null
            )?.use { cursor ->
                numInstances += cursor.count
            }
            return numInstances
        }

        /**
         * Finds the total number of instances this event has (including instances of exceptions)
         *
         * The number of returned instances may vary with the Android version.
         *
         * @return number of direct event instances (not counting instances of exceptions); *null* if
         * the number can't be determined or if the event has no last date (recurring event without last instance)
         */
        fun numInstances(provider: ContentProviderClient, account: Account, eventID: Long): Int? {
            // num instances of the main event
            var numInstances = numDirectInstances(provider, account, eventID) ?: return null

            // add the number of instances of every main event's exception
            provider.query(
                Events.CONTENT_URI,
                arrayOf(Events._ID),
                "${Events.ORIGINAL_ID}=?", // get exception events of the main event
                arrayOf("$eventID"), null
            )?.use { exceptionsEventCursor ->
                while (exceptionsEventCursor.moveToNext()) {
                    val exceptionEventID = exceptionsEventCursor.getLong(0)
                    val exceptionInstances = numDirectInstances(provider, account, exceptionEventID)

                    if (exceptionInstances == null)
                    // number of instances of exception can't be determined; so the total number of instances is also unclear
                        return null

                    numInstances += exceptionInstances
                }
            }
            return numInstances
        }

    }

}