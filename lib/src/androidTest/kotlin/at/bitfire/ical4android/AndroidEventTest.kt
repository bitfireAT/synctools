/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.os.Build
import android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL
import android.provider.CalendarContract.AUTHORITY
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.test.InitCalendarProviderRule
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Status
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import java.net.URI
import java.time.Duration

class AndroidEventTest {

    @get:Rule
    val initCalendarProviderRule: TestRule = InitCalendarProviderRule.initialize()

    private val testAccount = Account(javaClass.name, ACCOUNT_TYPE_LOCAL)

    private lateinit var calendar: AndroidCalendar
    lateinit var client: ContentProviderClient

    @Before
    fun prepare() {
        val context = getInstrumentation().targetContext
        client = context.contentResolver.acquireContentProviderClient(AUTHORITY)!!

        calendar = TestCalendar.findOrCreate(testAccount, client)
    }

    @After
    fun shutdown() {
        calendar.delete()
        client.closeCompat()
    }


    @Test
    fun testConstructor_ContentValues() {
        val e = AndroidEvent(
            calendar, contentValuesOf(
                Events._ID to 123,
                Events._SYNC_ID to "some-ical.ics",
                AndroidEvent.COLUMN_ETAG to "some-etag",
                AndroidEvent.COLUMN_SCHEDULE_TAG to "some-schedule-tag",
                AndroidEvent.COLUMN_FLAGS to 45
            )
        )
        assertEquals(123L, e.id)
        assertEquals("some-ical.ics", e.syncId)
        assertEquals("some-etag", e.eTag)
        assertEquals("some-schedule-tag", e.scheduleTag)
        assertEquals(45, e.flags)
    }


    @Test
    fun testUpdateEvent() {
        // add test event without reminder
        val event = Event()
        event.uid = "sample1@testAddEvent"
        event.summary = "Sample event"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")
        event.organizer = Organizer(URI("mailto:organizer@example.com"))
        val uri = AndroidEvent(calendar, event, "update-event").add()

        // update test event in calendar
        val testEvent = calendar.getEvent(ContentUris.parseId(uri))!!
        val event2 = testEvent.event!!
        event2.summary = "Updated event"
        // add data rows
        event2.alarms += VAlarm(Duration.parse("-P1DT2H3M4S"))
        event2.attendees += Attendee(URI("mailto:user@example.com"))
        val uri2 = testEvent.update(event2)

        // event should have been updated
        assertEquals(ContentUris.parseId(uri), ContentUris.parseId(uri2))

        // read again and verify result
        val updatedEvent = calendar.getEvent(ContentUris.parseId(uri2))!!
        try {
            val event3 = updatedEvent.event!!
            assertEquals(event2.summary, event3.summary)
            assertEquals(1, event3.alarms.size)
            assertEquals(1, event3.attendees.size)
        } finally {
            updatedEvent.delete()
        }
    }

    @Test
    fun testUpdateEvent_ResetColor() {
        // add event with color
        val event = Event().apply {
            uid = "sample1@testAddEvent"
            dtStart = DtStart(DateTime())
            color = Css3Color.silver
        }
        val uri = AndroidEvent(calendar, event, "reset-color").add()
        val id = ContentUris.parseId(uri)

        // verify that it has color
        val beforeUpdate = calendar.getEvent(id)!!
        assertNotNull(beforeUpdate.event?.color)

        // update: reset color
        event.color = null
        beforeUpdate.update(event)

        // verify that it doesn't have color anymore
        val afterUpdate = calendar.getEvent(id)!!
        assertNull(afterUpdate.event!!.color)
    }

    @Test
    fun testUpdateEvent_UpdateStatusFromNull() {
        val event = Event()
        event.uid = "sample1@testAddEvent"
        event.summary = "Sample event with STATUS"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")
        val uri = AndroidEvent(calendar, event, "update-status-from-null").add()

        // update test event in calendar
        val testEvent = calendar.getEvent(ContentUris.parseId(uri))!!
        val event2 = testEvent.event!!
        event2.summary = "Sample event without STATUS"
        event2.status = Status.VEVENT_CONFIRMED
        val uri2 = testEvent.update(event2)

        // event should have been updated
        assertEquals(ContentUris.parseId(uri), ContentUris.parseId(uri2))

        // read again and verify result
        val updatedEvent = calendar.getEvent(ContentUris.parseId(uri2))!!
        try {
            val event3 = updatedEvent.event!!
            assertEquals(Status.VEVENT_CONFIRMED, event3.status)
        } finally {
            updatedEvent.delete()
        }
    }

    @Test
    fun testUpdateEvent_UpdateStatusToNull() {
        val event = Event()
        event.uid = "sample1@testAddEvent"
        event.summary = "Sample event with STATUS"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")
        event.status = Status.VEVENT_CONFIRMED
        val uri = AndroidEvent(calendar, event, "update-status-to-null").add()

        // update test event in calendar
        val testEvent = calendar.getEvent(ContentUris.parseId(uri))!!
        val event2 = testEvent.event!!
        event2.summary = "Sample event without STATUS"
        event2.status = null
        val uri2 = testEvent.update(event2)

        // event should have been deleted and inserted again
        assertNotEquals(ContentUris.parseId(uri), ContentUris.parseId(uri2))

        // read again and verify result
        val updatedEvent = calendar.getEvent(ContentUris.parseId(uri2))!!
        try {
            val event3 = updatedEvent.event!!
            assertNull(event3.status)
        } finally {
            updatedEvent.delete()
        }
    }



    @Test
    fun testTransaction() {
        val event = Event()
        event.uid = "sample1@testTransaction"
        event.summary = "an event"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")
        for (i in 0 until 20)
            event.attendees += Attendee(URI("mailto:att$i@example.com"))
        val uri = AndroidEvent(calendar, event, "transaction").add()

        val testEvent = calendar.getEvent(ContentUris.parseId(uri))!!
        try {
            assertEquals(20, testEvent.event!!.attendees.size)
        } finally {
            testEvent.delete()
        }
    }


    // companion object

    @Test
    fun testMarkEventAsDeleted() {
        // Create event
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "A fine event"
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        // Delete event
        AndroidEvent.markAsDeleted(client, testAccount, localEvent.id!!)

        // Get the status of whether the event is deleted
        client.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, localEvent.id!!).asSyncAdapter(testAccount),
            arrayOf(Events.DELETED),
            null,
            null, null
        )!!.use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }


    @Test
    fun testNumDirectInstances_SingleInstance() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 1 instance"
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(1, AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumDirectInstances_Recurring() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 5 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=5"))
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(5, AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumDirectInstances_Recurring_Endless() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event without end"
            rRules.add(RRule("FREQ=DAILY"))
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertNull(AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    // flaky, needs InitCalendarProviderRule
    fun testNumDirectInstances_Recurring_LateEnd() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 53 years"
            rRules.add(RRule("FREQ=YEARLY;UNTIL=20740119T010203Z"))     // year 2074 is not supported by Android <11 Calendar Storage
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            assertEquals(52, AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!))
        else
            assertNull(AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumDirectInstances_Recurring_ManyInstances() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 2 years"
            rRules.add(RRule("FREQ=DAILY;UNTIL=20240120T010203Z"))
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()
        val number = AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!)

        // Some android versions (i.e. <=Q and S) return 365*2 instances (wrong, 365*2+1 => correct),
        // but we are satisfied with either result for now
        assertTrue(number == 365 * 2 || number == 365 * 2 + 1)
    }

    @Test
    fun testNumDirectInstances_RecurringWithExdate() {
        val event = Event().apply {
            dtStart = DtStart(Date("20220120T010203Z"))
            summary = "Event with 5 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=5"))
            exDates.add(ExDate(DateList("20220121T010203Z", Value.DATE_TIME)))
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(4, AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumDirectInstances_RecurringWithExceptions() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 5 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=5"))
            exceptions.add(Event().apply {
                recurrenceId = RecurrenceId("20220122T010203Z")
                dtStart = DtStart("20220122T130203Z")
                summary = "Exception on 3rd day"
            })
            exceptions.add(Event().apply {
                recurrenceId = RecurrenceId("20220124T010203Z")
                dtStart = DtStart("20220122T160203Z")
                summary = "Exception on 5th day"
            })
        }
        val localEvent = AndroidEvent(calendar, event, "filename.ics", null, null, 0)
        localEvent.add()

        assertEquals(5 - 2, AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!))
    }


    @Test
    fun testNumInstances_SingleInstance() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 1 instance"
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(1, AndroidEvent.numInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumInstances_Recurring() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 5 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=5"))
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(5, AndroidEvent.numInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumInstances_Recurring_Endless() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with infinite instances"
            rRules.add(RRule("FREQ=YEARLY"))
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertNull(AndroidEvent.numInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumInstances_Recurring_LateEnd() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event over 22 years"
            rRules.add(RRule("FREQ=YEARLY;UNTIL=20740119T010203Z"))     // year 2074 not supported by Android <11 Calendar Storage
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            assertEquals(52, AndroidEvent.numInstances(client, testAccount, localEvent.id!!))
        else
            assertNull(AndroidEvent.numInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumInstances_Recurring_ManyInstances() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event over two years"
            rRules.add(RRule("FREQ=DAILY;UNTIL=20240120T010203Z"))
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                365 * 2       // Android <9: does not include UNTIL (incorrect!)
            else
                365 * 2 + 1,  // Android ≥10: includes UNTIL (correct)
            AndroidEvent.numInstances(client, testAccount, localEvent.id!!)
        )
    }

    @Test
    fun testNumInstances_RecurringWithExceptions() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 6 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=6"))
            exceptions.add(Event().apply {
                recurrenceId = RecurrenceId("20220122T010203Z")
                dtStart = DtStart("20220122T130203Z")
                summary = "Exception on 3rd day"
            })
            exceptions.add(Event().apply {
                recurrenceId = RecurrenceId("20220124T010203Z")
                dtStart = DtStart("20220122T160203Z")
                summary = "Exception on 5th day"
            })
        }
        val localEvent = AndroidEvent(calendar, event, "filename.ics", null, null, 0)
        localEvent.add()

        calendar.getEvent(localEvent.id!!)!!

        assertEquals(6, AndroidEvent.numInstances(client, testAccount, localEvent.id!!))
    }

}