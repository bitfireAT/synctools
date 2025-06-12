/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.test.filters.FlakyTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.impl.TestEvent
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.net.URI
import java.util.Arrays
import java.util.Calendar

class BatchOperationTest {

    companion object {

        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )

        lateinit var calendarProvider: ContentProviderClient
        lateinit var contactsProvider: ContentProviderClient

        @BeforeClass
        @JvmStatic
        fun connectProvider() {
            val contentResolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            calendarProvider = contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!
            contactsProvider = contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
        }

        @AfterClass
        @JvmStatic
        fun closeProvider() {
            calendarProvider.closeCompat()
            contactsProvider.closeCompat()
        }

    }

    private val testAccount = Account("ical4android@example.com", CalendarContract.ACCOUNT_TYPE_LOCAL)

    private lateinit var calendarUri: Uri
    private lateinit var calendar: TestCalendar

    @Before
    fun prepare() {
        System.gc()

        // prepare calendar provider tests
        calendar = TestCalendar.findOrCreate(testAccount, calendarProvider)
        assertNotNull(calendar)
        calendarUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendar.id)
    }

    @After
    fun shutdown() {
        calendar.delete()
        System.gc()
    }


    // calendar provider tests

    @Test
    fun testCalendarProvider_OperationsPerYieldPoint_499() {
        val builder = BatchOperation(calendarProvider)

        // 499 operations should throw LocalStorageException exception
        builder.queue.clear()
        repeat(499) { number ->
            builder.enqueue(BatchOperation.CpoBuilder.newInsert(CalendarContract.Events.CONTENT_URI)
                .withValue(CalendarContract.Events.TITLE, "Event $number"))
        }
        builder.commit()
    }

    @Test(expected = LocalStorageException::class)
    fun testCalendarProvider_OperationsPerYieldPoint_500() {
        val builder = BatchOperation(calendarProvider)

        // 500 operations should throw LocalStorageException exception
        builder.queue.clear()
        repeat(500) { number ->
            builder.enqueue(BatchOperation.CpoBuilder.newInsert(CalendarContract.Events.CONTENT_URI)
                .withValue(CalendarContract.Events.TITLE, "Event $number"))
        }
        builder.commit()
    }

    @Test
    fun testCalendarProvider_TransactionSplitting() {
        val event = Event()
        event.uid = "sample1@testLargeTransaction"
        event.summary = "Large event"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")
        for (i in 0 until 2000) //  2000 attendees are enough for a transaction split to happen
            event.attendees += Attendee(URI("mailto:att$i@example.com"))
        val uri = TestEvent(calendar, event).add()
        val testEvent = calendar.findById(ContentUris.parseId(uri))
        try {
            assertEquals(2000, testEvent.event!!.attendees.size)
        } finally {
            testEvent.delete()
        }
    }

    @FlakyTest
    @Test
    fun testCalendarProvider_LargeTransactionSplitting() {
        // with 4000 attendees, this test has been observed to fail on the CI server docker emulator.
        // Too many Binders are sent to SYSTEM (see issue #42). Asking for GC in @Before/@After might help.
        val event = Event()
        event.uid = "sample1@testLargeTransaction"
        event.summary = "Large event"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")
        for (i in 0 until 4000)
            event.attendees += Attendee(URI("mailto:att$i@example.com"))
        val uri = TestEvent(calendar, event).add()
        val testEvent = calendar.findById(ContentUris.parseId(uri))
        try {
            assertEquals(4000, testEvent.event!!.attendees.size)
        } finally {
            testEvent.delete()
        }
    }

    @Test(expected = LocalStorageException::class)
    fun testCalendarProvider_LargeTransactionSingleRow() {
        val event = Event()
        event.uid = "sample1@testLargeTransaction"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")

        // 1 MB SUMMARY ... have fun
        val data = CharArray(1024*1024)
        Arrays.fill(data, 'x')
        event.summary = String(data)

        TestEvent(calendar, event).add()
    }


    // contacts provider tests

    @Test
    fun testContactsProvider_OperationsPerYieldPoint_499() {
        val builder = BatchOperation(contactsProvider)

        // 499 operations should succeed
        builder.queue.clear()
        repeat(499) {
            builder.enqueue(
                BatchOperation.CpoBuilder.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, "com.example")
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, "test")
            )
        }
        builder.commit()
    }

    @Test(expected = LocalStorageException::class)
    fun testContactsProvider_OperationsPerYieldPoint_500() {
        val builder = BatchOperation(contactsProvider)

        // 500 operations should throw LocalStorageException exception
        builder.queue.clear()
        repeat(500) {
            builder.enqueue(BatchOperation.CpoBuilder.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, "com.example")
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, "test"))
        }
        builder.commit()
    }

}