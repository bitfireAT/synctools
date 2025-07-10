/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

/**
 * Provides event + exceptions functionality for an [AndroidCalendar].
 *
 * In [AndroidCalendar], every row in the Events table is one entity. A recurring event
 * with an exception is two rows in the Events table and thus represented by two records/entities
 * in [AndroidCalendar] context:  1) the recurring event, and 2) the exception (which references
 * the original event).
 *
 * However usually we need to handle events together with their exceptions. This class provides
 * methods for that. In context of this class, a recurring event with an exception is represented by
 * one [EventAndExceptions] data object.
 */
class AndroidEventStore(
    private val calendar: AndroidCalendar
) {

    // AssociatedRows CRUD

    fun add(events: EventAndExceptions) {
        // insert main event → sync_id
        // insert exceptions with backref original_sync_id
        TODO()
    }

    fun findBySyncId(syncId: String): EventAndExceptions? {
        TODO()
    }

    fun findDirty(): List<EventAndExceptions> {
        TODO("Find DIRTY by ID/ORIGINAL_ID")
    }

    fun findDeleted(): List<EventAndExceptions> {
        TODO("Find DELETED by ID (ORIGINAL_ID=null)")
        // explicitly delete original + synced in DB
    }

    fun setFlagsOfNonDirty() {
        TODO("only for original")
    }


    fun deleteDirtyWithoutInstances() {
        TODO()
    }

    fun numInstances() {
        TODO()
    }

}