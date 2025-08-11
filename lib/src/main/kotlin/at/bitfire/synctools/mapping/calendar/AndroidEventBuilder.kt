/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.AssociatedEvents
import at.bitfire.synctools.mapping.calendar.builder.AccessLevelBuilder
import at.bitfire.synctools.mapping.calendar.builder.AllDayBuilder
import at.bitfire.synctools.mapping.calendar.builder.AndroidEventFieldBuilder
import at.bitfire.synctools.mapping.calendar.builder.AttendeesBuilder
import at.bitfire.synctools.mapping.calendar.builder.AvailabilityBuilder
import at.bitfire.synctools.mapping.calendar.builder.CategoriesBuilder
import at.bitfire.synctools.mapping.calendar.builder.ColorBuilder
import at.bitfire.synctools.mapping.calendar.builder.DescriptionBuilder
import at.bitfire.synctools.mapping.calendar.builder.DtEndBuilder
import at.bitfire.synctools.mapping.calendar.builder.DtStartBuilder
import at.bitfire.synctools.mapping.calendar.builder.DurationBuilder
import at.bitfire.synctools.mapping.calendar.builder.EventEndTimeZoneBuilder
import at.bitfire.synctools.mapping.calendar.builder.EventLocationBuilder
import at.bitfire.synctools.mapping.calendar.builder.EventTimeZoneBuilder
import at.bitfire.synctools.mapping.calendar.builder.ExRuleBuilder
import at.bitfire.synctools.mapping.calendar.builder.HasAttendeeDataBuilder
import at.bitfire.synctools.mapping.calendar.builder.OrganizerBuilder
import at.bitfire.synctools.mapping.calendar.builder.OriginalReferenceBuilder
import at.bitfire.synctools.mapping.calendar.builder.RDateBuilder
import at.bitfire.synctools.mapping.calendar.builder.RRuleBuilder
import at.bitfire.synctools.mapping.calendar.builder.SequenceBuilder
import at.bitfire.synctools.mapping.calendar.builder.StatusBuilder
import at.bitfire.synctools.mapping.calendar.builder.SyncObjectBuilder
import at.bitfire.synctools.mapping.calendar.builder.TitleBuilder
import at.bitfire.synctools.mapping.calendar.builder.UidBuilder
import at.bitfire.synctools.mapping.calendar.builder.UnknownPropertiesBuilder
import at.bitfire.synctools.mapping.calendar.builder.UrlBuilder
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

    fun build(): EventAndExceptions? {
        val mainEvent = associatedEvents.main ?: fakeMainEvent()
        return EventAndExceptions(
            main = buildEvent(from = mainEvent, main = mainEvent) ?: return null,
            exceptions = associatedEvents.exceptions.mapNotNull { exception ->
                buildEvent(from = exception, main = mainEvent)
            }
        )
    }

    private fun buildEvent(from: VEvent, main: VEvent): Entity? {
        val result = Entity(ContentValues())
        for (builder in getBuilders())
            if (!builder.build(from = from, main = main, to = result)) {
                // discard entity when builder returns null
                return null
            }
        return result
    }

    private fun fakeMainEvent(): VEvent = TODO()

    private fun getBuilders(): List<AndroidEventFieldBuilder> = listOf(
        AccessLevelBuilder(),
        AllDayBuilder(),
        AttendeesBuilder(),
        AvailabilityBuilder(),
        CategoriesBuilder(),
        ColorBuilder(),
        DescriptionBuilder(),
        DtEndBuilder(),
        DtStartBuilder(),
        DurationBuilder(),
        EventEndTimeZoneBuilder(),
        EventLocationBuilder(),
        EventTimeZoneBuilder(),
        OriginalReferenceBuilder(syncId = syncId),
        ExRuleBuilder(),
        HasAttendeeDataBuilder(),
        OrganizerBuilder(),
        RDateBuilder(),
        RRuleBuilder(),
        SequenceBuilder(),
        StatusBuilder(),
        SyncObjectBuilder(
            calendarId = calendarId,
            syncId = syncId,
            eTag = eTag,
            scheduleTag = scheduleTag,
            flags = flags
        ),
        TitleBuilder(),
        UidBuilder(),
        UnknownPropertiesBuilder(),
        UrlBuilder()
    )

}