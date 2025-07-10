/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.net.Uri
import android.os.RemoteException
import at.bitfire.synctools.mapping.calendar.LegacyAndroidEventBuilder
import at.bitfire.synctools.mapping.calendar.LegacyAndroidEventProcessor
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.CalendarBatchOperation

/**
 * Provides legacy features (read/write of legacy [Event]s), based on the new [AndroidCalendar].
 */
@Deprecated("Use AndroidCalendar instead")
class LegacyAndroidCalendar(
    private val calendar: AndroidCalendar
) {

    /**
     * Saves a new [event] into the calendar storage.
     *
     * @param event     new, yet unsaved event
     *
     * @return content URI of the created event
     *
     * @throws LocalStorageException when the calendar provider doesn't return a result row
     * @throws RemoteException on calendar provider errors
     */
    fun add(
        event: Event,
        syncId: String? = null,
        eTag: String? = null,
        scheduleTag: String? = null,
        flags: Int = 0
    ): Uri {
        val batch = CalendarBatchOperation(calendar.client)

        val builder = LegacyAndroidEventBuilder(calendar, event, null, syncId, eTag, scheduleTag, flags)
        val idxEvent = builder.addOrUpdateRows(event, batch) ?: throw AssertionError("Expected Events._ID backref")
        batch.commit()

        val resultUri = batch.getResult(idxEvent)?.uri
            ?: throw LocalStorageException("Empty result from content provider when adding event")
        return resultUri
    }

    /**
     * Gets a specific legacy [AndroidEvent], identified by the event ID, from this calendar.
     *
     * @param id    event ID
     * @return event (or `null` if not found)
     */
    fun getAndroidEvent(androidCalendar: AndroidCalendar, id: Long): AndroidEvent? {
        val values = androidCalendar.getEventValues(id, null) ?: return null
        return AndroidEvent(androidCalendar, values)
    }

    /**
     * Gets an [Event] data object from an Android event with a specific ID.
     *
     * @param id    event ID
     *
     * @return event data object
     */
    fun getEvent(id: Long): Event? {
        val entity = calendar.getEventEntity(id) ?: return null
        return Event().also { event ->
            val processor = LegacyAndroidEventProcessor(calendar, id, entity)
            processor.populate(to = event)
        }

    }

}