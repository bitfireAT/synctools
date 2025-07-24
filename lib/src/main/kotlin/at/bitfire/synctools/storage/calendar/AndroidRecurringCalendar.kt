/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.os.RemoteException
import android.provider.CalendarContract.Events
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.LocalStorageException

/**
 * Decorator for [AndroidCalendar] that adds support for [EventAndExceptions]
 * data objects.
 */
class AndroidRecurringCalendar(
    private val calendar: AndroidCalendar
) {

    /**
     * Adds an event and all its exceptions.
     */
    fun addEventAndExceptions(eventAndExceptions: EventAndExceptions) {
        try {
            val batch = CalendarBatchOperation(calendar.client)
            calendar.addEvent(eventAndExceptions.main, batch)

            /* Add exceptions. We don't have to set ORIGINAL_ID of each exception to the ID of
            the main event because the content provider associates events with their exceptions
            using _SYNC_ID / ORIGINAL_SYNC_ID. */
            for (exception in eventAndExceptions.exceptions)
                calendar.addEvent(exception, batch)

            batch.commit()
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't insert event/exceptions", e)
        }
    }

    /**
     * Adds an event and all its exceptions.
     */
    fun updateEventAndExceptions(id: Long, eventAndExceptions: EventAndExceptions) {
        try {
            val rebuild = calendar.eventUpdateNeedsRebuild(id, eventAndExceptions.main.entityValues) ?: true
            if (rebuild) {
                deleteEventAndExceptions(id)
                addEventAndExceptions(eventAndExceptions)
                return
            }

            // update main event
            val batch = CalendarBatchOperation(calendar.client)
            calendar.updateEvent(id, eventAndExceptions.main, batch)

            // remove and add exceptions again
            batch += CpoBuilder.newDelete(calendar.eventsUri).withSelection("${Events.ORIGINAL_ID}=?", arrayOf(id.toString()))
            for (exception in eventAndExceptions.exceptions)
                calendar.addEvent(exception, batch)

            batch.commit()
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update event/exceptions", e)
        }
    }

    /**
     * Deletes an event and all its potential exceptions.
     *
     * @param id    ID of the event
     */
    fun deleteEventAndExceptions(id: Long) {
        try {
            val batch = CalendarBatchOperation(calendar.client)

            // delete main event
            batch += CpoBuilder.newDelete(calendar.eventUri(id))

            // delete exceptions, too (not automatically done by provider)
            batch += CpoBuilder
                .newDelete(calendar.eventsUri)
                .withSelection("${Events.ORIGINAL_ID}=?", arrayOf(id.toString()))

            batch.commit()
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete event $id", e)
        }
    }

}