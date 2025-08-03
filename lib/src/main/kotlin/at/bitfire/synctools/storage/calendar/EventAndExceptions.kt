/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.content.Entity
import android.provider.CalendarContract.Events

/**
 * Represents a set of local events (like [at.bitfire.synctools.storage.calendar.AndroidEvent2] values)
 * that are stored together in one iCalendar object on the server. It consists of
 *
 * - a main component (like a main event),
 * - optional exceptions of this main component (exception instances).
 *
 * Constraints:
 *
 * - If [exceptions] are present, [main] must have a [Events._SYNC_ID] so that
 *   the calendar provider can associate the event with its exceptions.
 * - All [exceptions] (if any) must have an [Events.ORIGINAL_SYNC_ID] that matches the [Events._SYNC_ID] of [main].
 * - All [exceptions] (if any) must have an [Events.ORIGINAL_ALL_DAY] that matches the [Events.ALL_DAY] of [main].
 * - Exceptions must not be recurring, i.e. they must not have [Events.RRULE], [Events.RDATE], [Events.EXRULE] or [Events.EXDATE].
 *
 * @throws IllegalArgumentException if above constraints are not met
 */
data class EventAndExceptions(
    val main: Entity,
    val exceptions: List<Entity>
) {

    init {
        if (exceptions.isNotEmpty()) {
            val mainSyncId = main.entityValues.getAsString(Events._SYNC_ID)
            if (mainSyncId == null)
                throw IllegalArgumentException("_SYNC_ID must be set when exceptions are present")

            val mainAllDay = main.entityValues.getAsInteger(Events.ALL_DAY) ?: 0
            for (exception in exceptions) {
                if (exception.entityValues.getAsString(Events.ORIGINAL_SYNC_ID) != mainSyncId)
                    throw IllegalArgumentException("ORIGINAL_SYNC_ID of exceptions must match _SYNC_ID of main event")

                if ((exception.entityValues.getAsInteger(Events.ORIGINAL_ALL_DAY) ?: 0) != mainAllDay)
                    throw IllegalArgumentException("ORIGINAL_ALL_DAY of exceptions must match ALL_DAY of main event")

                for (field in arrayOf(Events.RRULE, Events.RDATE, Events.EXRULE, Events.EXDATE))
                    if (exception.entityValues.get(field) != null)
                        throw IllegalArgumentException("Exceptions must not be recurring")
            }
        }
    }

}