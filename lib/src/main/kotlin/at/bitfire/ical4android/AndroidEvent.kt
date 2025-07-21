/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.RemoteException
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Reminders
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.mapping.calendar.LegacyAndroidEventBuilder
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import at.bitfire.synctools.storage.calendar.CalendarBatchOperation
import com.google.common.base.MoreObjects

/**
 * Stores and retrieves VEVENT iCalendar objects (represented as [Event]s) to/from the
 * Android Calendar provider.
 *
 * Extend this class to process specific fields of the event.
 *
 * Important: To use recurrence exceptions, you MUST set _SYNC_ID and ORIGINAL_SYNC_ID
 * in populateEvent() / buildEvent. Setting _ID and ORIGINAL_ID is not sufficient.
 *
 * @param calendar  calendar this event is assigned to
 * @param values    content values of this event row, as read from the [Events] table
 */
@Deprecated("Use AndroidEvent2 instead")
class AndroidEvent(
    val calendar: AndroidCalendar,
    values: ContentValues
) {

    val id: Long = values.getAsLong(Events._ID)

    var syncId: String? = values.getAsString(Events._SYNC_ID)
    var eTag: String? = values.getAsString(AndroidEvent2.COLUMN_ETAG)
    var scheduleTag: String? = values.getAsString(AndroidEvent2.COLUMN_SCHEDULE_TAG)
    var flags: Int = values.getAsInteger(AndroidEvent2.COLUMN_FLAGS) ?: 0

    /**
     * Updates an already existing event in the calendar storage with the values
     * from the instance.
     *
     * **Does not update the [event] of this object (only calendar storage)!**
     *
     * @throws LocalStorageException when the calendar provider doesn't return a result row
     * @throws RemoteException on calendar provider errors
     */
    fun update(event: Event): Uri {
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
            return LegacyAndroidCalendar(calendar).add(event, syncId, eTag, scheduleTag, flags)

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
                        AndroidEvent2.EXTNAME_CATEGORIES,
                        AndroidEvent2.EXTNAME_ICAL_UID,       // UID is stored in UID_2445, don't leave iCalUid rows in events that we have written
                        AndroidEvent2.EXTNAME_URL,
                        UnknownProperty.CONTENT_ITEM_TYPE
                    )
                )

            val builder = LegacyAndroidEventBuilder(calendar, event, id, syncId, eTag, scheduleTag, flags)
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

        return batch.commit()
    }

    private fun deleteExceptions(batch: CalendarBatchOperation) {
        val existingId = requireNotNull(id)
        batch += CpoBuilder
            .newDelete(Events.CONTENT_URI.asSyncAdapter(calendar.account))
            .withSelection("${Events.ORIGINAL_ID}=?", arrayOf(existingId.toString()))
    }
    
    private fun eventSyncURI(): Uri {
        val id = requireNotNull(id)
        return ContentUris.withAppendedId(Events.CONTENT_URI, id).asSyncAdapter(calendar.account)
    }

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("calendar", calendar)
            .add("id", id)
            .add("eTag", eTag)
            .add("scheduleTag", scheduleTag)
            .add("flags", flags)
            .toString()
    }

}