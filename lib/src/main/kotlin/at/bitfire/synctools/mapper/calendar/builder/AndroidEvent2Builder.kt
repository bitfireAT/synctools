/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapper.calendar.builder

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.mapper.calendar.AssociatedVEvents
import at.bitfire.synctools.mapper.calendar.propertyListOf
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Summary

/**
 * Converts VEVENTs from an calendar object resource (RFC 4791 Section 4.1) to an
 * Android calendar provider representation ("Android event").
 *
 * @param syncProperties    sync properties that are not contained in the iCalendar but shall be present in the Android event.

 */
class AndroidEvent2Builder(
    private val syncProperties: SyncProperties,
    private val vEvents: AssociatedVEvents
) {

    /* TBD Features:

    - uid, recurrence-id, sequence
    - start time generator
    - end time (/ duration) generator
    - summary, location, URL, description
    - color, image, conference
    - categories
    - recurrence rules (RRULE, RDATE, EXRULE, EXDATE)
    - organizer, attendees
    - status, transparency, classification
    - alarms
    - attachments
    - unknown properties
     */

    /**
     * Builds the Android event from the [syncProperties], the [mainVEvent] and the [exceptions].
     *
     * @throws IllegalArgumentException     if [mainVEvent] and/or [exceptions] UIDs don't match
     */
    fun build(): AndroidEvent2 {
        val mainVEvent = vEvents.mainVEvent ?: fakeMainEvent()

        return AndroidEvent2(
            mainEvent = buildEvent(mainVEvent, mainVEvent).also { entity ->
                // apply sync properties like file name, flags, dirty/deleted
                SyncPropertiesBuilder.intoEntity(syncProperties, entity)
            },
            exceptions = vEvents.exceptions.map { exception ->
                buildEvent(exception, mainVEvent)
            }
        )
    }

    /**
     * Generate fake main event from the list of exceptions.
     */
    private fun fakeMainEvent(): VEvent {
        return VEvent(propertyListOf(
            vEvents.exceptions.first().uid,
            Summary("(unknown recurring event)")
            // DTSTART = DTSTART of first exception
            // RDATE = DTSTARTs of all exceptions
            // etc.
        ))
    }

    private fun buildEvent(vEvent: VEvent, mainVEvent: VEvent): Entity {
        val entity = Entity(ContentValues())
        for (feature in features)
            feature.intoEntity(syncProperties, vEvent, mainVEvent, entity)
        return entity
    }


    data class SyncProperties(
        val calendarId: Long,
        val fileName: String,
        val dirty: Boolean = false,
        val deleted: Boolean = false,
        val flags: Int = 0
    )


    companion object {

        val features = arrayOf(
            DescriptionBuilder,
            DtEndBuilder,
            DtStartBuilder,
            EventTimeZoneBuilder,
            OriginalInstanceTimeBuilder,
            OriginalSyncIdBuilder,
            SummaryBuilder,
            // special case: SyncPropertiesBuilder is explicitly called by build()
            UidBuilder
        )

    }

}