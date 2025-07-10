/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL
import android.provider.CalendarContract.AUTHORITY
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.test.InitCalendarProviderRule
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.Status
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

        calendar = TestCalendar.findOrCreate(testAccount, client, withColors = true)
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
        val uri = LegacyAndroidCalendar(calendar).add(event)

        // update test event in calendar
        val testEvent = calendar.getLegacyEvent(ContentUris.parseId(uri))!!
        val event2 = testEvent.event!!
        event2.summary = "Updated event"
        // add data rows
        event2.alarms += VAlarm(Duration.parse("-P1DT2H3M4S"))
        event2.attendees += Attendee(URI("mailto:user@example.com"))
        val uri2 = testEvent.update(event2)

        // event should have been updated
        assertEquals(ContentUris.parseId(uri), ContentUris.parseId(uri2))

        // read again and verify result
        val updatedEvent = calendar.getLegacyEvent(ContentUris.parseId(uri2))!!
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
        val uri = LegacyAndroidCalendar(calendar).add(event)
        val id = ContentUris.parseId(uri)

        // verify that it has color
        val beforeUpdate = calendar.getLegacyEvent(id)!!
        assertNotNull(beforeUpdate.event?.color)

        // update: reset color
        event.color = null
        beforeUpdate.update(event)

        // verify that it doesn't have color anymore
        val afterUpdate = calendar.getLegacyEvent(id)!!
        assertNull(afterUpdate.event!!.color)
    }

    @Test
    fun testUpdateEvent_UpdateStatusFromNull() {
        val event = Event()
        event.uid = "sample1@testAddEvent"
        event.summary = "Sample event with STATUS"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")
        val uri = LegacyAndroidCalendar(calendar).add(event)

        // update test event in calendar
        val testEvent = calendar.getLegacyEvent(ContentUris.parseId(uri))!!
        val event2 = testEvent.event!!
        event2.summary = "Sample event without STATUS"
        event2.status = Status.VEVENT_CONFIRMED
        val uri2 = testEvent.update(event2)

        // event should have been updated
        assertEquals(ContentUris.parseId(uri), ContentUris.parseId(uri2))

        // read again and verify result
        val updatedEvent = calendar.getLegacyEvent(ContentUris.parseId(uri2))!!
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
        val uri = LegacyAndroidCalendar(calendar).add(event)

        // update test event in calendar
        val testEvent = calendar.getLegacyEvent(ContentUris.parseId(uri))!!
        val event2 = testEvent.event!!
        event2.summary = "Sample event without STATUS"
        event2.status = null
        val uri2 = testEvent.update(event2)

        // event should have been deleted and inserted again
        assertNotEquals(ContentUris.parseId(uri), ContentUris.parseId(uri2))

        // read again and verify result
        val updatedEvent = calendar.getLegacyEvent(ContentUris.parseId(uri2))!!
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
        val uri = LegacyAndroidCalendar(calendar).add(event)

        val testEvent = calendar.getLegacyEvent(ContentUris.parseId(uri))!!
        try {
            assertEquals(20, testEvent.event!!.attendees.size)
        } finally {
            testEvent.delete()
        }
    }

}