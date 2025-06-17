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
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.impl.TestEvent
import at.bitfire.ical4android.impl.TestTaskList
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.techbee.jtx.JtxContract
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import org.dmfs.tasks.contract.TaskContract
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URI
import java.util.Arrays

class BatchOperationTest {

    @get:Rule
    val permissionRule = GrantPermissionOrSkipRule(setOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        *TaskProvider.PERMISSIONS_JTX,
        *TaskProvider.PERMISSIONS_TASKS_ORG,
        *TaskProvider.PERMISSIONS_OPENTASKS
    ))

    lateinit var calendarProvider: ContentProviderClient
    lateinit var contactsProvider: ContentProviderClient
    lateinit var jtxProvider: TaskProvider
    lateinit var tasksOrgProvider: TaskProvider
    lateinit var openTasksProvider: TaskProvider

    private val testAccount = Account("ical4android@example.com", "com.example")

    private lateinit var calendarUri: Uri
    private lateinit var calendar: TestCalendar

    @Before
    fun prepare() {
        val contentResolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        calendarProvider = contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!
        contactsProvider = contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
        val context = InstrumentationRegistry.getInstrumentation().context
        jtxProvider = TaskProvider.acquire(context, TaskProvider.ProviderName.JtxBoard)!!
        tasksOrgProvider = TaskProvider.acquire(context, TaskProvider.ProviderName.TasksOrg)!!
        openTasksProvider = TaskProvider.acquire(context, TaskProvider.ProviderName.OpenTasks)!!

        // prepare calendar provider tests
        calendar = TestCalendar.findOrCreate(testAccount, calendarProvider)
        assertNotNull(calendar)
        calendarUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendar.id)
    }

    @After
    fun shutdown() {
        calendar.delete()

        calendarProvider.closeCompat()
        contactsProvider.closeCompat()
        jtxProvider.close()
        tasksOrgProvider.close()
        openTasksProvider.close()
    }


    // calendar provider tests

    @Test
    fun testCalendarProvider_OperationsPerYieldPoint_9999() {
        val builder = BatchOperation(calendarProvider, null)

        // 9999 operations still don't throw an exception
        builder.queue.clear()
        repeat(9999) { number ->
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

    @Test
    fun testCalendarProvider_LargeTransactionSplitting() {
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
        val builder = BatchOperation(contactsProvider, null)

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
        val builder = BatchOperation(contactsProvider, null)

        // 500 operations should throw LocalStorageException exception
        builder.queue.clear()
        repeat(500) {
            builder.enqueue(BatchOperation.CpoBuilder.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, "com.example")
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, "test"))
        }
        builder.commit()
    }


    // tasks provider tests

    @Test
    fun testJtxTaskProvider_OperationsPerYieldPoint_9999() {
        val syncAdapterUri = JtxContract.JtxCollection.CONTENT_URI.buildUpon()
            .appendQueryParameter("caller_is_syncadapter", "true")
            .appendQueryParameter("account_name", testAccount.name)
            .appendQueryParameter("account_type", testAccount.type)
            .build()
        val builder = BatchOperation(jtxProvider.client, null)

        // 9999+ operations will not throw an exception for jtxBoard
        builder.queue.clear()
        repeat(9999) { number ->
            builder.enqueue(
                BatchOperation.CpoBuilder.newInsert(syncAdapterUri)
                    .withValue(JtxContract.JtxCollection.DESCRIPTION, "Task $number")
            )
        }
        builder.commit()
    }

    @Test
    fun testTasksOrgProvider_OperationsPerYieldPoint_499() {
        val taskList = TestTaskList.create(testAccount, tasksOrgProvider)
        val contentUri = TaskContract.Tasks.getContentUri(TaskProvider.ProviderName.TasksOrg.authority)
        val builder = BatchOperation(tasksOrgProvider.client, null)

        // 499 operations are max for Tasks.org
        builder.queue.clear()
        repeat(499) { number ->
            builder.enqueue(
                BatchOperation.CpoBuilder.newInsert(contentUri)
                    .withValue(TaskContract.Tasks.LIST_ID, taskList.id)
                    .withValue(TaskContract.Tasks.TITLE, "Task $number")
            )
        }
        builder.commit()
        taskList.delete()
    }

    @Test(expected = LocalStorageException::class)
    fun testTasksOrgProvider_OperationsPerYieldPoint_500() {
        val taskList = TestTaskList.create(testAccount, tasksOrgProvider)
        val contentUri = TaskContract.Tasks.getContentUri(TaskProvider.ProviderName.TasksOrg.authority)
        val builder = BatchOperation(tasksOrgProvider.client, null)

        // 499 operations are max for Tasks.org
        builder.queue.clear()
        repeat(500) { number ->
            builder.enqueue(
                BatchOperation.CpoBuilder.newInsert(contentUri)
                    .withValue(TaskContract.Tasks.LIST_ID, taskList.id)
                    .withValue(TaskContract.Tasks.TITLE, "Task $number")
            )
        }
        builder.commit()
        taskList.delete()
    }

    @Test
    fun testOpenTasksProvider_OperationsPerYieldPoint_499() {
        val taskList = TestTaskList.create(testAccount, openTasksProvider)
        val contentUri = TaskContract.Tasks.getContentUri(TaskProvider.ProviderName.OpenTasks.authority)
        val builder = BatchOperation(openTasksProvider.client, null)

        // 499 operations are max for OpenTasks
        builder.queue.clear()
        repeat(499) { number ->
            builder.enqueue(
                BatchOperation.CpoBuilder.newInsert(contentUri)
                    .withValue(TaskContract.Tasks.LIST_ID, taskList.id)
                    .withValue(TaskContract.Tasks.TITLE, "Task $number")
            )
        }
        builder.commit()
        taskList.delete()
    }

    @Test(expected = LocalStorageException::class)
    fun testOpenTasksProvider_OperationsPerYieldPoint_500() {
        val taskList = TestTaskList.create(testAccount, openTasksProvider)
        val contentUri = TaskContract.Tasks.getContentUri(TaskProvider.ProviderName.OpenTasks.authority)
        val builder = BatchOperation(openTasksProvider.client, null)

        // 499 operations are max for OpenTasks
        builder.queue.clear()
        repeat(500) { number ->
            builder.enqueue(
                BatchOperation.CpoBuilder.newInsert(contentUri)
                    .withValue(TaskContract.Tasks.LIST_ID, taskList.id)
                    .withValue(TaskContract.Tasks.TITLE, "Task $number")
            )
        }
        builder.commit()
        taskList.delete()
    }

}