/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.AssociatedEvents
import at.bitfire.synctools.mapping.calendar.builder.ExceptionMainReferenceBuilder
import at.bitfire.synctools.mapping.calendar.builder.SyncObjectBuilder
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import net.fortuna.ical4j.model.component.VEvent

/**
 * Maps
 *
 * - an iCalendar object that contains VEVENTs and exceptions ([AssociatedEvents]) to
 * - Android event data rows (including exceptions → [EventAndExceptions]).
 *
 * The built fields must contain `null` values for empty fields so that they can be used for updates.
 */
class AndroidEventBuilder(
    private val associatedEvents: AssociatedEvents,

    // AndroidEvent-level fields
    private val calendarId: Long,
    private val syncId: String,
    private val eTag: String?,
    private val scheduleTag: String?,
    private val flags: Int
) {

    fun build(): EventAndExceptions {
        val mainEvent = associatedEvents.main ?: fakeMainEvent()
        return EventAndExceptions(
            main = buildEvent(from = mainEvent, main = mainEvent),
            exceptions = associatedEvents.exceptions.map { exception ->
                buildEvent(from = exception, main = mainEvent)
            }
        )
    }

    private fun buildEvent(from: VEvent, main: VEvent): Entity {
        val result = Entity(ContentValues())
        for (builder in getBuilders())
            builder.build(from = from, main = main, to = result)
        return result
    }

    private fun fakeMainEvent(): VEvent = TODO()

    private fun getBuilders() = listOf(
        SyncObjectBuilder(
            calendarId = calendarId,
            syncId = syncId,
            eTag = eTag,
            scheduleTag = scheduleTag,
            flags = flags
        ),
        ExceptionMainReferenceBuilder(syncId = syncId)
    )

}