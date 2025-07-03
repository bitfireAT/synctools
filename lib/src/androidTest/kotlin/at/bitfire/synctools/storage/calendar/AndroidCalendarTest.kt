/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.os.Build
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AndroidCalendarTest {

    @get:Rule
    val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    lateinit var client: ContentProviderClient
    lateinit var provider: AndroidCalendarProvider

    private val testAccount = Account(javaClass.name, CalendarContract.ACCOUNT_TYPE_LOCAL)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        client = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!
        provider = AndroidCalendarProvider(testAccount, client)
    }

    @After
    fun tearDown() {
        client.closeCompat()
    }


    @Test
    fun testCreateAndGetCalendar() {
        // create calendar
        val calendar = provider.createAndGetCalendar(
            contentValuesOf(
                Calendars.NAME to "TestCalendar",
                Calendars.CALENDAR_DISPLAY_NAME to "ical4android Test Calendar",
                Calendars.VISIBLE to 0,
                Calendars.SYNC_EVENTS to 0
            )
        )

        // delete calendar
        assertEquals(1, calendar.delete())
    }


    @Test
    fun testNumInstances_SingleInstance() {
        val calendar = provider.createAndGetCalendar(contentValuesOf())
        try {
            val eventId = calendar.createEventFromDataObject(Event().apply {
                dtStart = DtStart("20220120T010203Z")
                summary = "Event with 1 instance"
            })
            assertEquals(1, calendar.numInstances(eventId))
        } finally {
            calendar.delete()
        }
    }

    @Test
    fun testNumInstances_Recurring() {
        val calendar = provider.createAndGetCalendar(contentValuesOf())
        try {
            val eventId = calendar.createEventFromDataObject(Event().apply {
                dtStart = DtStart("20220120T010203Z")
                summary = "Event with 5 instances"
                rRules.add(RRule("FREQ=DAILY;COUNT=5"))
            })
            assertEquals(5, calendar.numInstances(eventId))
        } finally {
            calendar.delete()
        }
    }

    @Test
    fun testNumInstances_Recurring_Endless() {
        val calendar = provider.createAndGetCalendar(contentValuesOf())
        try {
            val eventId = calendar.createEventFromDataObject(Event().apply {
                dtStart = DtStart("20220120T010203Z")
                summary = "Event with infinite instances"
                rRules.add(RRule("FREQ=YEARLY"))
            })
            assertNull(calendar.numInstances(eventId))
        } finally {
            calendar.delete()
        }
    }

    @Test
    fun testNumInstances_Recurring_LateEnd() {
        val calendar = provider.createAndGetCalendar(contentValuesOf())
        try {
            val eventId = calendar.createEventFromDataObject(Event().apply {
                dtStart = DtStart("20220120T010203Z")
                summary = "Event over 22 years"
                rRules.add(RRule("FREQ=YEARLY;UNTIL=20740119T010203Z"))     // year 2074 not supported by Android <11 Calendar Storage
            })

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                assertEquals(52, calendar.numInstances(eventId))
            else
                assertNull(calendar.numInstances(eventId))
        } finally {
            calendar.delete()
        }
    }

    @Test
    fun testNumInstances_Recurring_ManyInstances() {
        val calendar = provider.createAndGetCalendar(contentValuesOf())
        try {
            val eventId = calendar.createEventFromDataObject(Event().apply {
                dtStart = DtStart("20220120T010203Z")
                summary = "Event over two years"
                rRules.add(RRule("FREQ=DAILY;UNTIL=20240120T010203Z"))
            })

            assertEquals(
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q)
                    365 * 2       // Android <10: does not include UNTIL (incorrect!)
                else
                    365 * 2 + 1,  // Android ≥10: includes UNTIL (correct)
                calendar.numInstances(eventId)
            )
        } finally {
            calendar.delete()
        }
    }

    @Test
    fun testNumInstances_RecurringWithExceptions() {
        val calendar = provider.createAndGetCalendar(contentValuesOf())
        try {
            val eventId = calendar.createEventFromDataObject(
                event = Event().apply {
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
                },
                syncId = "filename.ics"
            )

            // explicitly read from calendar provider
            calendar.getEvent(eventId)

            assertEquals(6, calendar.numInstances(eventId))
        } finally {
            calendar.delete()
        }
    }

}