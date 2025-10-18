/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.synctools.mapping.calendar.AndroidEventProcessor
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidRecurringCalendar

/**
 * Provides legacy features (read/write of legacy [Event]s), based on the new [AndroidCalendar].
 */
@Deprecated("Use AndroidCalendar instead")
class LegacyAndroidCalendar(
    private val calendar: AndroidCalendar
) {

    /**
     * Gets an [Event] data object from an Android event with a specific ID.
     *
     * @param id    event ID
     *
     * @return event data object
     */
    fun getEvent(id: Long): Event? {
        val recurringCalendar = AndroidRecurringCalendar(calendar)
        val eventAndExceptions = recurringCalendar.getById(id) ?: return null
        return Event().also { event ->
            val processor = AndroidEventProcessor(calendar.account.name)
            processor.populate(eventAndExceptions, to = event)
        }

    }

}