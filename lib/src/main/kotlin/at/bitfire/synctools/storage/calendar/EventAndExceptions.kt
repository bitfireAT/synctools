/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.mapping.calendar.LegacyAndroidEventBuilder2
import java.util.LinkedList
import java.util.logging.Logger

/**
 * Represents a set of local events (like [at.bitfire.synctools.storage.calendar.AndroidEvent2] values)
 * and associated exception events that are stored together in one iCalendar object on the server. It consists of
 *
 * - a main component (like a main event),
 * - optional exceptions of this main component (exception instances).
 */
data class EventAndExceptions(
    val main: Entity,
    val exceptions: List<Entity>
) {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Merges the current object with another one. Only needed until [LegacyAndroidEventBuilder2] is dropped.
     */
    @Deprecated("Remove when LegacyAndroidEventBuilder2 is dropped")
    fun mergeFrom(other: EventAndExceptions) {
        // merge main entity
        merge(into = main, from = other.main)

        // merge exceptions by ORIGINAL_INSTANCE_TIME
        val otherExceptions = LinkedList(other.exceptions)
        for (exception in exceptions) {
            val ourOriginalInstanceTime = exception.entityValues.getAsLong(Events.ORIGINAL_INSTANCE_TIME)
            val otherException = otherExceptions.firstOrNull {
                it.entityValues.getAsLong(Events.ORIGINAL_INSTANCE_TIME) == ourOriginalInstanceTime
            }
            if (otherException != null) {
                // we found an exception in other with same instance time, merge
                merge(into = exception, from = otherException)

                // remove from otherExceptions so that it won't be found again
                otherExceptions.remove(otherException)
            } else
                logger.warning("Couldn't find other exception for ORIGINAL_INSTANCE_TIME=$ourOriginalInstanceTime")
        }

        if (otherExceptions.isNotEmpty())
            logger.warning("${otherExceptions.size} leftover exception(s) couldn't be merged")
    }

    private fun merge(into: Entity, from: Entity) {
        // merge values
        into.entityValues.putAll(from.entityValues)

        // merge sub-values
        for (sub in from.subValues)
            into.addSubValue(sub.uri, sub.values)
    }

}