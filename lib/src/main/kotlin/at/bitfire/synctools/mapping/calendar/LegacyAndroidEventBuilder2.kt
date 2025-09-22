/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Event
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
import at.bitfire.synctools.mapping.calendar.builder.ETagBuilder
import at.bitfire.synctools.mapping.calendar.builder.LocationBuilder
import at.bitfire.synctools.mapping.calendar.builder.OrganizerBuilder
import at.bitfire.synctools.mapping.calendar.builder.OriginalInstanceTimeBuilder
import at.bitfire.synctools.mapping.calendar.builder.RecurrenceFieldsBuilder
import at.bitfire.synctools.mapping.calendar.builder.RemindersBuilder
import at.bitfire.synctools.mapping.calendar.builder.SequenceBuilder
import at.bitfire.synctools.mapping.calendar.builder.StatusBuilder
import at.bitfire.synctools.mapping.calendar.builder.SyncFlagsBuilder
import at.bitfire.synctools.mapping.calendar.builder.SyncIdBuilder
import at.bitfire.synctools.mapping.calendar.builder.TimeFieldsBuilder
import at.bitfire.synctools.mapping.calendar.builder.TitleBuilder
import at.bitfire.synctools.mapping.calendar.builder.UidBuilder
import at.bitfire.synctools.mapping.calendar.builder.UnknownPropertiesBuilder
import at.bitfire.synctools.mapping.calendar.builder.UrlBuilder
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.EventAndExceptions

/**
 * Legacy mapper from an [Event] data object to Android content provider data rows
 * (former "build..." methods).
 *
 * Important: To use recurrence exceptions, you MUST set _SYNC_ID and ORIGINAL_SYNC_ID
 * in populateEvent() / buildEvent. Setting _ID and ORIGINAL_ID is not sufficient.
 *
 * Note: "Legacy" will be removed from the class name as soon as the [Event] dependency is
 * replaced by [at.bitfire.synctools.icalendar.AssociatedEvents].
 */
class LegacyAndroidEventBuilder2(
    calendar: AndroidCalendar,
    private val event: Event,

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
        TimeFieldsBuilder(),
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

    fun build() =
        EventAndExceptions(
            main = buildEvent(null),
            exceptions = event.exceptions.map { exception ->
                buildEvent(exception)
            }
        )

    fun buildEvent(recurrence: Event?): Entity {
        val entity = Entity(ContentValues())
        for (builder in fieldBuilders)
            builder.build(from = recurrence ?: event, main = event, to = entity)
        return entity
    }

}