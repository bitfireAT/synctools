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
import android.content.Entity
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.test.InitCalendarProviderRule
import at.bitfire.synctools.test.assertContentValuesEqual
import at.bitfire.synctools.test.assertEntitiesEqual
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AndroidCalendarTest {

    @get:Rule
    val initCalendarProviderRule = InitCalendarProviderRule.initialize()

    private val now = System.currentTimeMillis()
    private val testAccount = Account(javaClass.name, CalendarContract.ACCOUNT_TYPE_LOCAL)

    lateinit var client: ContentProviderClient
    lateinit var provider: AndroidCalendarProvider

    lateinit var calendar: AndroidCalendar

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        client = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!

        // make sure there are no colors for testAccount
        provider = AndroidCalendarProvider(testAccount, client)
        calendar = TestCalendar.findOrCreate(testAccount, client)
    }

    @After
    fun tearDown() {
        calendar.delete()
        client.closeCompat()
    }


    // CRUD AndroidEvent

    @Test
    fun testAddEvent_and_GetEvent() {
        val entity = Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event"
        )).apply {
            addSubValue(Reminders.CONTENT_URI, contentValuesOf(
                Reminders.MINUTES to 123
            ))
        }
        val id = calendar.addEvent(entity)

        // verify that event has been inserted
        val result = calendar.getEvent(id)!!
        assertEntitiesEqual(entity, result, onlyFieldsInExpected = true)
    }

    @Test
    fun testAddEvent_toBatch_AsSecondOperation() {
        val batch = CalendarBatchOperation(client)

        // first operation: no-op
        batch += BatchOperation.CpoBuilder
            .newUpdate(calendar.eventsUri)
            .withValue(Events._SYNC_ID, "won't happen")
            .withSelection("${Events._SYNC_ID}=?", arrayOf("testAddEvent_toBatch_AsSecondOperation"))

        // second operation (event row index > 0)
        val entity = Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event"
        )).apply {
            addSubValue(Reminders.CONTENT_URI, contentValuesOf(
                Reminders.MINUTES to 123
            ))
        }
        val idx = batch.nextBackrefIdx()
        calendar.addEvent(entity, batch)

        batch.commit()
        val id = ContentUris.parseId(batch.getResult(idx)!!.uri!!)

        // verify that event has been inserted
        val result = calendar.getEvent(id)!!
        assertEntitiesEqual(entity, result, onlyFieldsInExpected = true)
    }

    @Test
    fun testFindEvent() {
        // no result
        assertNull(calendar.findEvent("${Events.DTSTART}=?", arrayOf(now.toString())))

        // insert event
        val entity = Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event"
        ))
        calendar.addEvent(entity)

        // not it finds a result
        val result = calendar.findEvents("${Events.DTSTART}=?", arrayOf(now.toString()))
        assertEquals(1, result.size)
        assertEntitiesEqual(entity, result.first(), onlyFieldsInExpected = true)
    }

    @Test
    fun testFindEvents() {
        calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event"
        )))
        val id2 = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now + 3600000,
            Events.DTEND to now + 3600000*2,
            Events.TITLE to "Some Other Event 1"
        )))
        val id3 = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now + 3600000,
            Events.DTEND to now + 3600000*2,
            Events.TITLE to "Some Other Event 2"
        )))
        val result = calendar.findEvents("${Events.DTSTART}=?", arrayOf((now + 3600000).toString()))
        assertEquals(2, result.size)
        assertEquals(
            setOf(id2, id3),
            result.map { it.entityValues.getAsLong(Events._ID) }.toSet()
        )
        assertEquals(
            setOf("Some Other Event 1", "Some Other Event 2"),
            result.map { it.entityValues.getAsString(Events.TITLE) }.toSet()
        )
    }

    @Test
    fun testFindEventRow() {
        calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event"
        )))
        val result = calendar.findEventRow(arrayOf(Events.TITLE), "${Events.DTSTART}=?", arrayOf(now.toString()))
        assertContentValuesEqual(
            contentValuesOf(Events.TITLE to "Some Event"),
            result!!
        )
    }

    @Test
    fun testFindEventRow_NotExisting() {
        assertNull(calendar.findEventRow(arrayOf(Events.TITLE), "${Events.DTSTART}=?", arrayOf(now.toString())))
    }

    // getEvent and getEventEntity are implicitly tested by testAddEvent_and_GetEvent

    @Test
    fun testGetEventRow() {
        val values = contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event"
        )
        val id = calendar.addEvent(Entity(values))

        val result = calendar.getEventRow(id, arrayOf(
            Events.CALENDAR_ID, Events.DTSTART, Events.DTEND, Events.TITLE
        ))!!
        assertContentValuesEqual(values, result)
    }

    @Test
    fun testIterateEventRows() {
        val id1 = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event 1"
        )))
        val id2 = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event 2"
        )))

        val result = mutableListOf<ContentValues>()
        calendar.iterateEventRows(arrayOf(Events._ID, Events.TITLE), null, null) { row ->
            result += row
        }
        assertEquals(
            setOf(id1, id2),
            result.map { it.getAsLong(Events._ID) }.toSet()
        )
        assertEquals(
            setOf("Some Event 1", "Some Event 2"),
            result.map { it.getAsString(Events.TITLE) }.toSet()
        )
    }

    @Test
    fun testIterateEvents() {
        val id1 = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event 1"
        )))
        val id2 = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event 2"
        )))

        val result = mutableListOf<Entity>()
        calendar.iterateEvents(null, null) { entity ->
            result += entity
        }
        assertEquals(
            setOf(id1, id2),
            result.map { it.entityValues.getAsLong(Events._ID) }.toSet()
        )
        assertEquals(
            setOf("Some Event 1", "Some Event 2"),
            result.map { it.entityValues.getAsString(Events.TITLE) }.toSet()
        )
    }

    @Test
    fun testGetStatusUpdateWorkaround_NoStatusUpdate() {
        assertEquals(
            AndroidCalendar.StatusUpdateWorkaround.NO_WORKAROUND,
            calendar.getStatusUpdateWorkaround(0, ContentValues())
        )
    }

    @Test
    fun testGetStatusUpdateWorkaround_UpdateStatusToNonNull() {
        assertEquals(
            AndroidCalendar.StatusUpdateWorkaround.NO_WORKAROUND,
            calendar.getStatusUpdateWorkaround(0, contentValuesOf(Events.STATUS to Events.STATUS_TENTATIVE))
        )
    }

    @Test
    fun testGetStatusUpdateWorkaround_UpdateStatusFromNullToNull() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Event without status",
            Events.STATUS to null
        )))
        assertEquals(
            AndroidCalendar.StatusUpdateWorkaround.DONT_UPDATE_STATUS,
            calendar.getStatusUpdateWorkaround(id, contentValuesOf(Events.STATUS to null))
        )
    }

    @Test
    fun testGetStatusUpdateWorkaround_UpdateStatusFromNonNullToNull() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Event without status",
            Events.STATUS to Events.STATUS_TENTATIVE
        )))
        assertEquals(
            AndroidCalendar.StatusUpdateWorkaround.REBUILD_EVENT,
            calendar.getStatusUpdateWorkaround(id, contentValuesOf(Events.STATUS to null))
        )
    }

    @Test
    fun testUpdateEventRow() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event 1"
        )))

        calendar.updateEventRow(id, contentValuesOf(Events.TITLE to "New Title"))

        assertEquals("New Title", calendar.getEvent(id)!!.entityValues.getAsString(Events.TITLE))
    }

    @Test
    fun testUpdateEventRowBatch() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event 1"
        )))

        val batch = CalendarBatchOperation(calendar.client)
        calendar.updateEventRow(id, contentValuesOf(Events.TITLE to "New Title"), batch)
        batch.commit()

        assertEquals("New Title", calendar.getEvent(id)!!.entityValues.getAsString(Events.TITLE))
    }

    @Test
    fun testUpdateEvent_NoRebuild() {
        val entity = Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event",
            //Events.STATUS to null
        )).apply {
            addSubValue(Reminders.CONTENT_URI, contentValuesOf(
                Reminders.MINUTES to 123
            ))
        }
        val id = calendar.addEvent(entity)

        // update with modified title
        entity.entityValues.put(Events.TITLE, "New Title")
        assertEquals(id, calendar.updateEvent(id, entity))

        val result = calendar.getEvent(id)!!
        assertEntitiesEqual(entity, result, onlyFieldsInExpected = true)
    }

    @Test
    fun testUpdateEvent_Rebuild() {
        val entity = Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event 1",
            Events.STATUS to Events.STATUS_CONFIRMED
        ))
        val id = calendar.addEvent(entity)

        entity.entityValues.put(Events.TITLE, "New Title")
        entity.entityValues.putNull(Events.STATUS)          // triggers re-build
        val newId = calendar.updateEvent(id, entity)
        assertNotEquals(newId, id)

        // old event is deleted
        assertNull(calendar.getEvent(id))

        // new event doesn't have status
        val newEvent = calendar.getEvent(newId)!!
        assertNull(newEvent.entityValues.getAsInteger(Events.STATUS))   // verify that it's null
        val expected = Entity(ContentValues(entity.entityValues).apply {
            remove(Events.STATUS)       // is null in provider and thus not returned by getEvent
        })
        assertEntitiesEqual(expected, newEvent, onlyFieldsInExpected = true)
    }

    @Test
    fun testUpdateEventRows() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event 1"
        )))

        calendar.updateEventRows(
            contentValuesOf(Events.TITLE to "New Title"),
            "${Events.DTSTART}=?",
            arrayOf(now.toString())
        )

        assertEquals("New Title", calendar.getEvent(id)!!.entityValues.getAsString(Events.TITLE))
    }

    @Test
    fun testDeleteEventAndExceptions() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Some Event 1"
        )))

        calendar.deleteEvent(id)

        assertNull(calendar.getEvent(id))
    }


    // event instances (we always test numDirectInstances + numInstances together)

    @Test
    fun testNumInstances_SingleInstance() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DTEND to now + 3600000,
            Events.TITLE to "Event with 1 instance"
        )))
        assertEquals(1, calendar.numDirectInstances(id))
        assertEquals(1, calendar.numInstances(id))
    }

    @Test
    fun testNumInstances_Recurring() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DURATION to "PT1H",
            Events.TITLE to "Event with 5 instances",
            Events.RRULE to "FREQ=DAILY;COUNT=5"
        )))
        assertEquals(5, calendar.numDirectInstances(id))
        assertEquals(5, calendar.numInstances(id))
    }

    @Test
    fun testNumInstances_Recurring_Endless() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to now,
            Events.DURATION to "PT1H",
            Events.TITLE to "Event without end",
            Events.RRULE to "FREQ=DAILY"
        )))
        assertNull(calendar.numDirectInstances(id))
        assertNull(calendar.numInstances(id))
    }

    @Test
    fun testNumInstances_Recurring_LateEnd() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to 1642640523000,
            Events.DURATION to "PT1H",
            Events.TITLE to "Event until 2074",
            Events.RRULE to "FREQ=YEARLY;UNTIL=20740119T010203Z"
        )))

        if (AndroidCalendarProvider.supportsYear2074) {
            assertEquals(52, calendar.numDirectInstances(id))
            assertEquals(52, calendar.numInstances(id))
        } else {
            assertNull(calendar.numDirectInstances(id))
            assertNull(calendar.numInstances(id))
        }
    }

    @Test
    fun testNumInstances_Recurring_Until() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to 1642640523000,
            Events.DURATION to "PT1H",
            Events.TITLE to "Event with 2 years",
            Events.RRULE to "FREQ=DAILY;UNTIL=20240120T010203Z"
        )))
        assertEquals(
            if (AndroidCalendarProvider.instancesIncludeUntil)
                365 * 2 + 1 // Android ≥9: includes UNTIL (correct)
            else
                365 * 2,    // Android <9: does not include UNTIL (incorrect!)
            calendar.numDirectInstances(id)
        )
    }

    @Test
    fun testNumInstances_RecurringWithExdate() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to 1642640523000,
            Events.DURATION to "PT1H",
            Events.TITLE to "Event with 5 instances, one of them excluded",
            Events.RRULE to "FREQ=DAILY;COUNT=5",
            Events.EXDATE to "20220121T010203Z"
        )))
        assertEquals(4, calendar.numDirectInstances(id))
        assertEquals(4, calendar.numInstances(id))
    }

    @Test
    fun testNumInstances_RecurringWithExceptions_MatchingOrigInstanceTime() {
        val syncId = "recurring-with-exceptions"
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events._SYNC_ID to syncId,
            Events.DTSTART to 1642640523000,
            Events.DURATION to "PT1H",
            Events.TITLE to "Event with 5 instances, two of them are exceptions",
            Events.RRULE to "FREQ=DAILY;COUNT=5"
        )))
        calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.ORIGINAL_SYNC_ID to syncId,
            Events.ORIGINAL_INSTANCE_TIME to 1642640523000 + 2*86400000,
            Events.DTSTART to 1642640523000 + 2*86400000 + 3600000, // one hour later
            Events.DTEND to 1642640523000 + 2*86400000 + 2*3600000,
            Events.TITLE to "Exception on 3rd day"
        )))
        calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.ORIGINAL_SYNC_ID to syncId,
            Events.ORIGINAL_INSTANCE_TIME to 1642640523000 + 4*86400000,
            Events.DTSTART to 1642640523000 + 4*86400000 + 3600000, // one hour later
            Events.DTEND to 1642640523000 + 4*86400000 + 2*3600000,
            Events.TITLE to "Exception on 5th day",
            Events.STATUS to Events.STATUS_CANCELED
        )))
        assertEquals(5 - 2, calendar.numDirectInstances(id))
        assertEquals(5 - /* one cancelled */ 1, calendar.numInstances(id))
    }

    @Test
    fun testNumInstances_RecurringWithExceptions_NotMatchingOrigInstanceTime() {
        val syncId = "recurring-with-exceptions"
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events._SYNC_ID to syncId,
            Events.DTSTART to 1642640523000,
            Events.DURATION to "PT1H",
            Events.TITLE to "Event with 5 instances, two of them are exceptions",
            Events.RRULE to "FREQ=DAILY;COUNT=5"
        )))
        calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.ORIGINAL_SYNC_ID to syncId,
            Events.ORIGINAL_INSTANCE_TIME to 1642640523000 + 2*86400000,
            Events.DTSTART to 1642640523000 + 2*86400000 + 3600000, // one hour later
            Events.DURATION to "PT1H",
            Events.TITLE to "Exception on 3rd day"
        )))
        calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.ORIGINAL_SYNC_ID to syncId,
            Events.ORIGINAL_INSTANCE_TIME to 1642640523000 + 4*86400000 + 100,  // doesn't match original instance time!
            Events.DTSTART to 1642640523000 + 4*86400000 + 3600000, // one hour later
            Events.DURATION to "PT1H",
            Events.TITLE to "Exception on 5th day (wrong instance time)"
        )))
        assertEquals(5 - 1, calendar.numDirectInstances(id))
        assertEquals(5 + /* one extra outside the recurrence */ 1, calendar.numInstances(id))
    }

    @Test
    fun testDeleteDirtyEventsWithoutInstances_NoInstances() {
        // create recurring event with only deleted/cancelled instances
        val now = System.currentTimeMillis()
        val recurringCalendar = AndroidRecurringCalendar(calendar)
        val id = recurringCalendar.addEventAndExceptions(EventAndExceptions(
            main = Entity(contentValuesOf(
                Events._SYNC_ID to "event-without-instances",
                Events.CALENDAR_ID to calendar.id,
                Events.ALL_DAY to 0,
                Events.DTSTART to now,
                Events.DURATION to "PT1H",
                Events.RRULE to "FREQ=DAILY;COUNT=3",
                Events.DIRTY to 1
            )),
            exceptions = listOf(
                Entity(contentValuesOf(     // first instance: cancelled
                    Events.CALENDAR_ID to calendar.id,
                    Events.ORIGINAL_INSTANCE_TIME to now,
                    Events.ORIGINAL_ALL_DAY to 0,
                    Events.DTSTART to now,
                    Events.DTEND to now + 3600000,
                    Events.STATUS to Events.STATUS_CANCELED
                )),
                Entity(contentValuesOf(     // second instance: cancelled
                    Events.CALENDAR_ID to calendar.id,
                    Events.ORIGINAL_INSTANCE_TIME to now + 86400000,
                    Events.ORIGINAL_ALL_DAY to 0,
                    Events.DTSTART to now + 86400000,
                    Events.DTEND to now + 86400000 + 3600000,
                    Events.STATUS to Events.STATUS_CANCELED
                )),
                Entity(contentValuesOf(     // third and last instance: cancelled
                    Events.CALENDAR_ID to calendar.id,
                    Events.ORIGINAL_INSTANCE_TIME to now + 2*86400000,
                    Events.ORIGINAL_ALL_DAY to 0,
                    Events.DTSTART to now + 2*86400000,
                    Events.DTEND to now + 2*86400000 + 3600000,
                    Events.STATUS to Events.STATUS_CANCELED
                ))
            )
        ))
        assertEquals(0, calendar.numInstances(id))

        // this method should mark the event as deleted
        calendar.deleteDirtyEventsWithoutInstances()

        // verify that event is now marked as deleted
        val result = calendar.getEventRow(id)!!
        assertEquals(1, result.getAsInteger(Events.DELETED))
    }

    @Test
    fun testDeleteDirtyEventsWithoutInstances_OneInstanceRemaining() {
        // create recurring event with only deleted/cancelled instances
        val syncId = "event-with-instances"
        val recurringCalendar = AndroidRecurringCalendar(calendar)
        val id = recurringCalendar.addEventAndExceptions(EventAndExceptions(
            main = Entity(contentValuesOf(
                Events.CALENDAR_ID to calendar.id,
                Events._SYNC_ID to syncId,
                Events.DTSTART to 1642640523000,
                Events.DURATION to "PT1H",
                Events.TITLE to "Event with 2 instances, one of them cancelled",
                Events.RRULE to "FREQ=DAILY;COUNT=2",
                Events.DIRTY to 1
            )),
            exceptions = listOf(
                Entity(contentValuesOf(     // first instance: cancelled
                    Events.CALENDAR_ID to calendar.id,
                    Events.ORIGINAL_SYNC_ID to syncId,
                    Events.ORIGINAL_INSTANCE_TIME to 1642640523000,
                    Events.DTSTART to 1642640523000 + 86400000,
                    Events.DTEND to 1642640523000 + 86400000 + 3600000,
                    Events.STATUS to Events.STATUS_CANCELED
                ))
                // however second instance is NOT cancelled
            )
        ))
        assertEquals(1, calendar.numInstances(id))

        // this method should mark the event as deleted
        calendar.deleteDirtyEventsWithoutInstances()

        // verify that event is still marked as dirty, but not as deleted
        val result = calendar.getEventRow(id)!!
        assertEquals(1, result.getAsInteger(Events.DIRTY))
        assertEquals(0, result.getAsInteger(Events.DELETED))
    }

}