/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.mapping.calendar.LegacyAndroidEventProcessor
import at.bitfire.synctools.storage.calendar.AndroidEvent.Companion.CATEGORIES_SEPARATOR
import java.util.logging.Logger

/**
 * Represents a locally stored event with an associated [at.bitfire.ical4android.Event] data object.
 *
 * @param calendar      calendar that manages this event
 * @param entity        event row and associated data rows as read from the calendar provider; [Events._ID] must be set
 *
 * @throws IllegalArgumentException when [Events._ID] is not set
 */
class AndroidEvent(
    val calendar: AndroidCalendar,
    val entity: Entity      // TODO removeBlank
) {

    private val mainValues
        get() = entity.entityValues

    private val logger: Logger
        get() = Logger.getLogger(javaClass.name)

    /** see [android.provider.BaseColumns._ID] */
    val id: Long = mainValues.getAsLong(Events._ID)
        ?: throw IllegalArgumentException("${Events._ID} must be set")

    val syncId: String?
        get() = mainValues.getAsString(Events._SYNC_ID)

    val eTag: String?
        get() = mainValues.getAsString(COLUMN_ETAG)

    val scheduleTag: String?
        get() = mainValues.getAsString(COLUMN_SCHEDULE_TAG)

    val flags: Int
        get() = mainValues.getAsInteger(COLUMN_FLAGS) ?: 0

    /**
     * Returns the full event data, either from [event] or, if [event] is null, by reading event
     * number [id] from the Android calendar storage.
     *
     * @throws IllegalArgumentException if event has not been saved yet
     * @throws java.io.FileNotFoundException if there's no event with [id] in the calendar storage
     * @throws android.os.RemoteException on calendar provider errors
     */
    val event: Event by lazy {
        Event().also { newEvent ->
            val processor = LegacyAndroidEventProcessor(calendar, id, entity)
            processor.populate(to = newEvent)
        }
    }


    // shortcuts to upper level

    fun update(values: ContentValues) = calendar.updateEvent(id, values)

    fun delete() = calendar.deleteEvent(id)


    // TODO: override fun toString(): String = "AndroidEvent(calendar=$calendar, id=$id, event=$event)"


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
         * VEVENT CATEGORIES are stored as an extended property with this [CalendarContract.ExtendedProperties.NAME].
         *
         * The [CalendarContract.ExtendedProperties.VALUE] format is the same as used by the AOSP Exchange ActiveSync adapter:
         * the category values are stored as list, separated by [CATEGORIES_SEPARATOR]. (If a category
         * value contains [CATEGORIES_SEPARATOR], [CATEGORIES_SEPARATOR] will be dropped.)
         *
         * Example: `Cat1\Cat2`
         */
        const val EXTNAME_CATEGORIES = "categories"
        const val CATEGORIES_SEPARATOR = '\\'

        /**
         * Google Calendar uses an extended property called `iCalUid` for storing the event's UID, instead of the
         * standard [CalendarContract.EventsColumns.UID_2445].
         *
         * @see <a href="https://github.com/bitfireAT/ical4android/issues/125">GitHub Issue</a>
         */
        const val EXTNAME_ICAL_UID = "iCalUid"

        /**
         * VEVENT URL is stored as an extended property with this [CalendarContract.ExtendedProperties.NAME].
         * The URL is directly put into [CalendarContract.ExtendedProperties.VALUE].
         */
        const val EXTNAME_URL = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.ical4android.url"


        // helpers

        /**
         * Marks the event as deleted
         * @param eventID
         */
        fun markAsDeleted(provider: ContentProviderClient, account: Account, eventID: Long) {
            provider.update(
                ContentUris.withAppendedId(
                    Events.CONTENT_URI,
                    eventID
                ).asSyncAdapter(account),
                contentValuesOf(Events.DELETED to 1),
                null, null
            )
        }

    }

}