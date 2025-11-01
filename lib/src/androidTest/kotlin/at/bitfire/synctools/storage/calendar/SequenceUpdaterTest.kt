/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.test.InitCalendarProviderRule
import at.bitfire.synctools.test.assertEventAndExceptionsEqual
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SequenceUpdaterTest {

    @get:Rule
    val initCalendarProviderRule = InitCalendarProviderRule.initialize()

    private val testAccount = Account(javaClass.name, CalendarContract.ACCOUNT_TYPE_LOCAL)

    lateinit var client: ContentProviderClient
    lateinit var provider: AndroidCalendarProvider

    lateinit var calendar: AndroidCalendar
    lateinit var recurringCalendar: AndroidRecurringCalendar
    lateinit var sequenceUpdater: SequenceUpdater

    @Before
    fun prepare() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        client = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!

        // make sure there are no colors for testAccount
        provider = AndroidCalendarProvider(testAccount, client)
        calendar = TestCalendar.findOrCreate(testAccount, client)
        recurringCalendar = AndroidRecurringCalendar(calendar)

        sequenceUpdater = SequenceUpdater(calendar)
    }

    @After
    fun tearDown() {
        calendar.delete()
        client.closeCompat()
    }

    // increaseSequence

    @Test
    fun testIncreaseSequence_NewEvent() {
        // In case of newly created events, it doesn't matter whether they're group-scheduled or not.
        val main = Entity(ContentValues(
            /* SEQUENCE column has never been set yet and thus is null */
        ))
        val result = sequenceUpdater.increaseSequence(main)

        // SEQUENCE column remains null for mapping ...
        assertNull(main.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE))
        // ... but SEQUENCE shall be set to 0 after upload
        assertEquals(0, result)
    }

    @Test
    fun testIncreaseSequence_GroupScheduledEvent_AsOrganizer() {
        val main = Entity(contentValuesOf(
            EventsContract.COLUMN_SEQUENCE to 1,
            Events.IS_ORGANIZER to 1
        )).apply {
            addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                Attendees.ATTENDEE_EMAIL to "test@example.com"
            ))
        }
        val result = sequenceUpdater.increaseSequence(main)
        assertEquals(2, main.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE))
        assertEquals(2, result)
    }

    @Test
    fun testIncreaseSequence_GroupScheduledEvent_NotAsOrganizer() {
        val main = Entity(contentValuesOf(
            EventsContract.COLUMN_SEQUENCE to 1,
            Events.IS_ORGANIZER to 0
        )).apply {
            addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                Attendees.ATTENDEE_EMAIL to "test@example.com"
            ))
        }
        val result = sequenceUpdater.increaseSequence(main)
        // SEQUENCE column remains 1 for mapping, ...
        assertEquals(1, main.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE))
        // ... but don't increase after upload.
        assertNull(result)
    }

    @Test
    fun testIncreaseSequence_NonGroupScheduledEvent_WithoutSequence() {
        val main = Entity(contentValuesOf(
            EventsContract.COLUMN_SEQUENCE to 0
        ))
        val result = sequenceUpdater.increaseSequence(main)

        // SEQUENCE column remains 0 for mapping (will be mapped to no SEQUENCE property), ...
        assertEquals(0, main.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE))
        // ... but don't increase after upload.
        assertNull(result)
    }

    @Test
    fun testIncreaseSequence_NonGroupScheduledEvent_WithSequence() {
        val main = Entity(contentValuesOf(
            EventsContract.COLUMN_SEQUENCE to 1
        ))
        val result = sequenceUpdater.increaseSequence(main)

        // SEQUENCE column is increased to 2 for mapping, ...
        assertEquals(2, main.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE))
        // ... and increased after upload.
        assertEquals(2, result)
    }

    @Test
    fun testProcessDeletedExceptions() {
        val now = System.currentTimeMillis()
        val mainValues = contentValuesOf(
            Events._SYNC_ID to "testProcessDeletedExceptions",
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DURATION to "PT1H",
            Events.RRULE to "FREQ=DAILY;COUNT=5",
            Events.DIRTY to 0,
            Events.DELETED to 0,
            EventsContract.COLUMN_SEQUENCE to 15
        )
        val exNotDeleted = Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.ORIGINAL_INSTANCE_TIME to now,
            Events.ORIGINAL_ALL_DAY to 0,
            Events.DTSTART to now,
            Events.TITLE to "not marked as deleted",
            Events.DIRTY to 0,
            Events.DELETED to 0
        ))
        val mainId = recurringCalendar.addEventAndExceptions(EventAndExceptions(
            main = Entity(mainValues),
            exceptions = listOf(
                exNotDeleted,
                Entity(contentValuesOf(
                    Events.CALENDAR_ID to calendar.id,
                    Events.ORIGINAL_INSTANCE_TIME to now,
                    Events.ORIGINAL_ALL_DAY to 0,
                    Events.DTSTART to now,
                    Events.DIRTY to 1,
                    Events.DELETED to 1,
                    Events.TITLE to "marked as deleted"
                ))
            )
        ))

        // should update main event and purge the deleted exception
        sequenceUpdater.processDeletedExceptions()

        val result = recurringCalendar.getById(mainId)!!
        assertEventAndExceptionsEqual(EventAndExceptions(
            main = Entity(ContentValues(mainValues).apply {
                put(Events.DIRTY, 1)
                put(EventsContract.COLUMN_SEQUENCE, 16)
            }),
            exceptions = listOf(exNotDeleted)
        ), result, onlyFieldsInExpected = true)
    }

    @Test
    fun testProcessDirtyExceptions() {
        val now = System.currentTimeMillis()
        val mainValues = contentValuesOf(
            Events._SYNC_ID to "testProcessDirtyExceptions",
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DURATION to "PT1H",
            Events.RRULE to "FREQ=DAILY;COUNT=5",
            Events.DIRTY to 0,
            Events.DELETED to 0,
            EventsContract.COLUMN_SEQUENCE to 15
        )
        val exDirtyValues = contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.ORIGINAL_INSTANCE_TIME to now,
            Events.ORIGINAL_ALL_DAY to 0,
            Events.DTSTART to now,
            Events.DIRTY to 1,
            Events.DELETED to 0,
            Events.TITLE to "marked as dirty",
            EventsContract.COLUMN_SEQUENCE to null
        )
        val mainId = recurringCalendar.addEventAndExceptions(EventAndExceptions(
            main = Entity(mainValues),
            exceptions = listOf(Entity(exDirtyValues))
        ))

        // should mark main event as dirty and increase exception SEQUENCE
        sequenceUpdater.processDirtyExceptions()

        val result = recurringCalendar.getById(mainId)!!
        assertEventAndExceptionsEqual(EventAndExceptions(
            main = Entity(ContentValues(mainValues).apply {
                put(Events.DIRTY, 1)
            }),
            exceptions = listOf(Entity(ContentValues(exDirtyValues).apply {
                put(Events.DIRTY, 0)
                put(EventsContract.COLUMN_SEQUENCE, 1)
            }))
        ), result, onlyFieldsInExpected = true)
    }

}