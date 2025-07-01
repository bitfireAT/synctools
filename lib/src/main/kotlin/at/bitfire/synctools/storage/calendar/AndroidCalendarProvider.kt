/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Colors
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.storage.LocalStorageException
import toContentValues
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Manages locally stored calendars (represented by [AndroidCalendar]) in the
 * Android calendar provider.
 */
class AndroidCalendarProvider(
    val account: Account,
    internal val client: ContentProviderClient
) {

    private val logger = Logger.getLogger(javaClass.name)


    // AndroidCalendar CRUD

    fun createCalendar(values: ContentValues): Long {
        logger.log(Level.FINE, "Creating local calendar", values)

        values.put(Calendars.ACCOUNT_NAME, account.name)
        values.put(Calendars.ACCOUNT_TYPE, account.type)

        val uri =
            try {
                client.insert(calendarsUri, values)
            } catch (e: RemoteException) {
                throw LocalStorageException("Couldn't create calendar", e)
            }
        if (uri == null)
            throw LocalStorageException("Couldn't create calendar")
        return ContentUris.parseId(uri)
    }

    fun createAndGetCalendar(values: ContentValues): AndroidCalendar {
        val id = createCalendar(values)
        return getCalendar(id) ?: throw LocalStorageException("Couldn't query calendar that was just created")
    }

    fun findCalendars(where: String? = null, whereArgs: Array<String>? = null, sortOrder: String? = null): List<AndroidCalendar> {
        val result = LinkedList<AndroidCalendar>()
        try {
            client.query(calendarsUri, null, where, whereArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext())
                    result += AndroidCalendar(this, cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query calendars", e)
        }
        return result
    }

    fun findFirstCalendar(where: String?, whereArgs: Array<String>?, sortOrder: String? = null): AndroidCalendar? {
        try {
            client.query(calendarsUri, null, where, whereArgs, sortOrder)?.use { cursor ->
                if (cursor.moveToNext())
                    return AndroidCalendar(this, cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query calendars", e)
        }
        return null
    }

    fun getCalendar(id: Long): AndroidCalendar? {
        try {
            client.query(calendarUri(id), null, null, null, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return AndroidCalendar(this, cursor.toContentValues())
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query calendar", e)
        }
        return null
    }

    fun updateCalendar(id: Long, values: ContentValues, where: String? = null, whereArgs: Array<String>? = null): Int {
        logger.log(Level.FINE, "Updating local calendar #$id", values)
        try {
            return client.update(calendarUri(id), values, where, whereArgs)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't update calendar", e)
        }
    }

    fun deleteCalendar(id: Long): Int {
        logger.fine("Deleting local calendar #$id")
        try {
            return client.delete(calendarUri(id), null, null)
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't delete calendar", e)
        }
    }


    // other methods: sync state, event colors

    fun readCalendarSyncState(id: Long): String? =
        try {
            client.query(calendarUri(id), arrayOf(COLUMN_CALENDAR_SYNC_STATE), null, null, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.getString(0)
                else
                    null
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't query calendar sync state", e)
        }

    fun writeCalendarSyncState(id: Long, state: String?) {
        updateCalendar(id, contentValuesOf(COLUMN_CALENDAR_SYNC_STATE to state))
    }

    fun provideCss3Colors() {
        client.query(colorsUri, arrayOf(Colors.COLOR_KEY), null, null, null)?.use { cursor ->
            if (cursor.count == Css3Color.entries.size)
                // colors already inserted and up to date
                return
        }

        logger.fine("Inserting CSS3 colors to account $account")
        try {
            client.bulkInsert(
                colorsUri,
                Css3Color.entries.map { color ->
                    contentValuesOf(
                        Colors.ACCOUNT_NAME to account.name,
                        Colors.ACCOUNT_TYPE to account.type,
                        Colors.COLOR_TYPE to Colors.TYPE_EVENT,
                        Colors.COLOR_KEY to color.name,
                        Colors.COLOR to color.argb
                    )
                }.toTypedArray()
            )
        } catch(e: RemoteException) {
            throw LocalStorageException("Couldn't insert CSS3 colors", e)
        }
    }

    fun removeCss3Colors() {
        logger.fine("Removing CSS3 colors from account $account")

        // unassign colors from events
        /* ANDROID STRANGENESS:
           1) updating Events.CONTENT_URI affects events of all accounts, not just the selected one
           2) account_type and account_name can't be specified in selection (causes SQLiteException)
           WORKAROUND: unassign event colors for each calendar
        */
        try {
            client.query(calendarsUri, arrayOf(Calendars._ID), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val calendarId = cursor.getLong(0)

                    val values = ContentValues(1)
                    values.putNull(CalendarContract.Events.EVENT_COLOR_KEY)
                    client.update(
                        CalendarContract.Events.CONTENT_URI.asSyncAdapter(account), values,
                        "${CalendarContract.Events.EVENT_COLOR_KEY} IS NOT NULL AND ${CalendarContract.Events.CALENDAR_ID}=?", arrayOf(calendarId.toString())
                    )
                }
            }
        } catch (e: RemoteException) {
            throw LocalStorageException("Couldn't unassign event colors", e)
        }

        // remove entries from color table
        client.delete(colorsUri, null, null)
    }


    // helpers

    val calendarsUri
        get() = Calendars.CONTENT_URI.asSyncAdapter(account)

    fun calendarUri(id: Long) =
        ContentUris.withAppendedId(calendarsUri, id)

    private val colorsUri
        get() = Colors.CONTENT_URI.asSyncAdapter(account)


    companion object {

        /**
         * Column to store per-calendar sync state as a [String]. Not to be confused
         * with the account-wide [CalendarContract.SyncState].
         */
        const val COLUMN_CALENDAR_SYNC_STATE = Calendars.CAL_SYNC1

    }

}