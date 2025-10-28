/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Event
import at.bitfire.synctools.icalendar.AssociatedEvents
import at.bitfire.synctools.mapping.calendar.builder.AccessLevelBuilder
import at.bitfire.synctools.mapping.calendar.builder.AllDayBuilder
import at.bitfire.synctools.mapping.calendar.builder.AndroidEntityBuilder
import at.bitfire.synctools.mapping.calendar.builder.AttendeesBuilder
import at.bitfire.synctools.mapping.calendar.builder.AvailabilityBuilder
import at.bitfire.synctools.mapping.calendar.builder.CalendarIdBuilder
import at.bitfire.synctools.mapping.calendar.builder.CategoriesBuilder
import at.bitfire.synctools.mapping.calendar.builder.ColorBuilder
import at.bitfire.synctools.mapping.calendar.builder.DescriptionBuilder
import at.bitfire.synctools.mapping.calendar.builder.DirtyAndDeletedBuilder
import at.bitfire.synctools.mapping.calendar.builder.DurationBuilder
import at.bitfire.synctools.mapping.calendar.builder.ETagBuilder
import at.bitfire.synctools.mapping.calendar.builder.EndTimeBuilder
import at.bitfire.synctools.mapping.calendar.builder.LocationBuilder
import at.bitfire.synctools.mapping.calendar.builder.OrganizerBuilder
import at.bitfire.synctools.mapping.calendar.builder.OriginalInstanceTimeBuilder
import at.bitfire.synctools.mapping.calendar.builder.RecurrenceFieldsBuilder
import at.bitfire.synctools.mapping.calendar.builder.RemindersBuilder
import at.bitfire.synctools.mapping.calendar.builder.SequenceBuilder
import at.bitfire.synctools.mapping.calendar.builder.StartTimeBuilder
import at.bitfire.synctools.mapping.calendar.builder.StatusBuilder
import at.bitfire.synctools.mapping.calendar.builder.SyncFlagsBuilder
import at.bitfire.synctools.mapping.calendar.builder.SyncIdBuilder
import at.bitfire.synctools.mapping.calendar.builder.TitleBuilder
import at.bitfire.synctools.mapping.calendar.builder.UidBuilder
import at.bitfire.synctools.mapping.calendar.builder.UnknownPropertiesBuilder
import at.bitfire.synctools.mapping.calendar.builder.UrlBuilder
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import net.fortuna.ical4j.model.component.VEvent

/**
 * Legacy mapper from an [Event] data object to Android content provider data rows
 * (former "build..." methods).
 *
 * Important: To use recurrence exceptions, you MUST set _SYNC_ID and ORIGINAL_SYNC_ID
 * in populateEvent() / buildEvent. Setting _ID and ORIGINAL_ID is not sufficient.
 */
class AndroidEventBuilder(
    calendar: AndroidCalendar,
    private val events: AssociatedEvents,

    // AndroidEvent-level fields
    syncId: String?,
    eTag: String?,
    scheduleTag: String?,
    flags: Int
) {

    private val fieldBuilders: Array<AndroidEntityBuilder> = arrayOf(
        // sync columns (as defined in CalendarContract.EventsColumns)
        SyncIdBuilder(syncId),
        DirtyAndDeletedBuilder(),
        // event columns
        CalendarIdBuilder(calendar.id),
        TitleBuilder(),
        DescriptionBuilder(),
        LocationBuilder(),
        ColorBuilder(calendar),
        StatusBuilder(),
        ETagBuilder(eTag = eTag, scheduleTag = scheduleTag),
        SyncFlagsBuilder(flags),
        SequenceBuilder(),
        StartTimeBuilder(),
        EndTimeBuilder(),
        DurationBuilder(),
        AllDayBuilder(),
        AccessLevelBuilder(),
        AvailabilityBuilder(),
        RecurrenceFieldsBuilder(),
        OriginalInstanceTimeBuilder(),
        OrganizerBuilder(calendar.ownerAccount),
        UidBuilder(),
        // sub-rows (alphabetically, by class name)
        AttendeesBuilder(calendar),
        CategoriesBuilder(),
        RemindersBuilder(),
        UnknownPropertiesBuilder(),
        UrlBuilder()
    )

    fun build(): EventAndExceptions {
        val mainVEvent = events.main!!  // TODO: create main VEvent if missing
        return EventAndExceptions(
            main = buildEvent(from = mainVEvent, main = mainVEvent),
            exceptions = events.exceptions.map { exception ->
                buildEvent(from = exception, main = mainVEvent)
            }
        )
    }

    fun buildEvent(from: VEvent, main: VEvent): Entity {
        val entity = Entity(ContentValues())
        for (builder in fieldBuilders)
            builder.build(from = from, main = main, to = entity)
        return entity
    }

}