/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.content.Entity
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import java.util.logging.Logger

/**
 * Handles SEQUENCE updates of events and exceptions.
 */
class SequenceUpdater(
    private val calendar: AndroidCalendar
) {

    private val logger: Logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Increases the event's SEQUENCE, if necessary.
     *
     * @param main  event to be checked (**will be modified** when SEQUENCE needs to be increased)
     *
     * @return updated sequence (or *null* if sequence was not increased/modified)
     */
    @VisibleForTesting
    internal fun increaseSequence(main: Entity): Int? {
        val mainValues = main.entityValues
        val currentSeq = mainValues.getAsInteger(EventsContract.COLUMN_SEQUENCE)

        if (currentSeq == null) {
            /* First upload, request to set to 0 in calendar provider after upload.
            We can let it empty in the Entity because then no SEQUENCE property will be generated,
            which is equal to SEQUENCE:0. */
            return 0
        }

        val groupScheduled = main.subValues.any { it.uri == CalendarContract.Attendees.CONTENT_URI }
        if (groupScheduled) {
            /* Note: Events.IS_ORGANIZER is defined in CalendarDatabaseHelper.java as
            COALESCE(Events.IS_ORGANIZER, Events.ORGANIZER = Calendars.OWNER_ACCOUNT), so it's non-null when it's
            - either explicitly set for an event,
            - or the event's ORGANIZER is the same as the calendar's OWNER_ACCOUNT. */
            val weAreOrganizer = when (mainValues.getAsInteger(Events.IS_ORGANIZER)) {
                null, 0 -> false
                /* explicitly set to non-zero, or 1 by provider calculation */ else -> true
            }

            return if (weAreOrganizer) {
                /* Upload of a group-scheduled event and we are the organizer, so we increase the SEQUENCE.
                We also have to store it into the Entity so that the new value will be mapped. */
                (currentSeq + 1).also { newSeq ->
                    mainValues.put(EventsContract.COLUMN_SEQUENCE, newSeq)
                }
            } else
            /* Upload of a group-scheduled event and we are not the organizer, so we don't increase the SEQUENCE. */
                null

        } else /* not group-scheduled */  {
            return if (currentSeq == 0) {
                /* The event was uploaded once and has SEQUENCE of 0 (which is mapped to an empty SEQUENCE property).
                We don't need to increase the SEQUENCE because the event is not group-scheduled. */
                null
            } else {
                /* Upload of a non-group-scheduled event where a SEQUENCE > 0 is present. Increase by one after upload.
                We also have to store it into the Entity so that the new value will be mapped. */
                (currentSeq + 1).also { newSeq ->
                    mainValues.put(EventsContract.COLUMN_SEQUENCE, newSeq)
                }
            }
        }
    }

    /**
     * Iterates through all exceptions in [calendar] that are marked as deleted.
     * For every found exception:
     *
     * - the SEQUENCE field of the main event is increased by one,
     * - the main event is marked as dirty (so that it will be synced),
     * - and then the exception is actually deleted (so that it won't show up anymore during sync).
     */
    fun processDeletedExceptions() {
        val batch = CalendarBatchOperation(calendar.client)

        // iterate through deleted exceptions
        calendar.iterateEventRows(
            arrayOf(Events._ID, Events.ORIGINAL_ID),
            "${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NOT NULL", null
        ) { values ->
            val exceptionId = values.getAsLong(Events._ID)          // can't be null (by definition)
            val mainId = values.getAsLong(Events.ORIGINAL_ID)       // can't be null (by query)
            logger.fine("Found deleted exception #$exceptionId, removing it and marking original event #$mainId as dirty")

            // main event: get current sequence
            val mainValues = calendar.getEventRow(mainId, arrayOf(EventsContract.COLUMN_SEQUENCE))
            val mainSeq = mainValues?.getAsInteger(EventsContract.COLUMN_SEQUENCE) ?: 0

            // increase sequence and mark as dirty
            calendar.updateEventRow(mainId, contentValuesOf(
                EventsContract.COLUMN_SEQUENCE to mainSeq + 1,
                Events.DIRTY to 1
            ), batch)

            // actually remove deleted exception
            calendar.deleteEvent(exceptionId, batch)
        }

        batch.commit()
   }

    /**
     * Iterates through all exceptions in [calendar] that are marked as dirty
     * and not marked as deleted.
     *
     * For every found exception:
     *
     * - the SEQUENCE field of the exception is increased by one,
     * - the exception is marked as not dirty anymore,
     * - but the main event is marked as dirty (so that it will be synced).
     */
    fun processDirtyExceptions() {
        val batch = CalendarBatchOperation(calendar.client)

        // iterate through dirty exceptions
        calendar.iterateEventRows(
            arrayOf(Events._ID, Events.ORIGINAL_ID, EventsContract.COLUMN_SEQUENCE),
            "${Events.DIRTY} AND NOT ${Events.DELETED} AND ${Events.ORIGINAL_ID} IS NOT NULL", null
        ) { values ->
            val exceptionId = values.getAsLong(Events._ID)          // can't be null (by definition)
            val mainId = values.getAsLong(Events.ORIGINAL_ID)       // can't be null (by query)
            val exceptionSeq = values.getAsInteger(EventsContract.COLUMN_SEQUENCE) ?: 0
            logger.fine("Found dirty exception $exceptionId, increasing SEQUENCE and marking main event $mainId as dirty")

            // mark main event as dirty
            calendar.updateEventRow(mainId, contentValuesOf(
                Events.DIRTY to 1
            ), batch)

            // increase exception SEQUENCE and set DIRTY to 0
            calendar.updateEventRow(exceptionId, contentValuesOf(
                EventsContract.COLUMN_SEQUENCE to exceptionSeq + 1,
                Events.DIRTY to 0
            ), batch)
        }

        batch.commit()
    }

}