/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import at.bitfire.synctools.storage.calendar.AndroidEvent2.Companion.CATEGORIES_SEPARATOR

/**
 * Stores and retrieves events to/from the Android calendar provider.
 *
 * An event in the context of this class one row in the [Events] table,
 * plus associated data rows (like alarms and reminders).
 *
 * Exceptions (of recurring events) have their own entries in the [Events] table and thus
 * are separate [AndroidEvent2]s.
 *
 * @param calendar  calendar where the event is stored
 * @param values    entity with all columns, as returned by the calendar provider; [Events._ID] must be set to a non-null value
 *
 * @throws IllegalArgumentException when [Events._ID] is not set
 */
class AndroidEvent2(
    val calendar: AndroidCalendar,
    private val values: Entity
) {

    private val mainValues
        get() = values.entityValues

    /** see [Events._ID] */
    val id: Long = mainValues.getAsLong(Events._ID)
        ?: throw IllegalArgumentException("Events._ID must be available")


    // sync fields

    /** see [Events._SYNC_ID] */
    val syncId: String?
        get() = mainValues.getAsString(Events._SYNC_ID)

    val eTag: String?
        get() = mainValues.getAsString(COLUMN_ETAG)

    val scheduleTag: String?
        get() = mainValues.getAsString(COLUMN_SCHEDULE_TAG)

    val flags: Int
        get() = mainValues.getAsInteger(COLUMN_FLAGS) ?: 0


    // data fields

    /** see [Events.DTSTART] */
    val dtStart: Long?
        get() = mainValues.getAsLong(Events.DTSTART)

    /** see [Events.DTEND] */
    val dtEnd: Long?
        get() = mainValues.getAsLong(Events.DTEND)

    /** see [Events.STATUS] */
    val status: Int?
        get() = mainValues.getAsInteger(Events.STATUS)

    /** see [Events.TITLE] */
    val title: String?
        get() = mainValues.getAsString(Events.TITLE)


    // data rows

    val reminders: List<ContentValues>
        get() = values.subValues.mapNotNull { dataRow ->
            dataRow.values.takeIf { dataRow.uri == Reminders.CONTENT_URI }
        }


    // shortcuts to upper level

    fun update(values: ContentValues) = calendar.updateEventRow(id, values)
    fun update(entity: Entity) = calendar.updateEvent(id, entity)
    fun delete() = calendar.deleteEvent(id)


    // helpers

    override fun toString(): String = "AndroidEvent2(calendar=$calendar, id=$id, values=$values)"


    companion object {

        const val MUTATORS_SEPARATOR = ','

        /**
         * Custom sync column to store the last known ETag of an event.
         */
        const val COLUMN_ETAG = Events.SYNC_DATA1

        /**
         * Custom sync column to store sync flags of an event.
         */
        const val COLUMN_FLAGS = Events.SYNC_DATA2

        /**
         * Custom sync column to store the SEQUENCE of an event.
         */
        const val COLUMN_SEQUENCE = Events.SYNC_DATA3

        /**
         * Custom sync column to store the Schedule-Tag of an event.
         */
        const val COLUMN_SCHEDULE_TAG = Events.SYNC_DATA4

        /**
         * VEVENT CATEGORIES are stored as an extended property with this [ExtendedProperties.NAME].
         *
         * The [ExtendedProperties.VALUE] format is the same as used by the AOSP Exchange ActiveSync adapter:
         * the category values are stored as list, separated by [CATEGORIES_SEPARATOR]. (If a category
         * value contains [CATEGORIES_SEPARATOR], [CATEGORIES_SEPARATOR] will be dropped.)
         *
         * Example: `Cat1\Cat2`
         */
        const val EXTNAME_CATEGORIES = "categories"
        const val CATEGORIES_SEPARATOR = '\\'

        /**
         * Google Calendar uses an extended property called `iCalUid` for storing the event's UID, instead of the
         * standard [Events.UID_2445].
         *
         * @see <a href="https://github.com/bitfireAT/ical4android/issues/125">GitHub Issue</a>
         */
        const val EXTNAME_ICAL_UID = "iCalUid"

        /**
         * VEVENT URL is stored as an extended property with this [ExtendedProperties.NAME].
         * The URL is directly put into [ExtendedProperties.VALUE].
         */
        const val EXTNAME_URL = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.ical4android.url"

    }

}