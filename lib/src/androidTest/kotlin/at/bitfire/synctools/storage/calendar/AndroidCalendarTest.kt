/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.os.Build
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.LegacyAndroidCalendar
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AndroidCalendarTest {

    @get:Rule
    val permissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    private val testAccount = Account(javaClass.name, CalendarContract.ACCOUNT_TYPE_LOCAL)

    lateinit var client: ContentProviderClient
    lateinit var provider: AndroidCalendarProvider

    lateinit var calendar: AndroidCalendar

    @Before
    fun prepare() {
        client = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!

        // make sure there are no colors for testAccount
        provider = AndroidCalendarProvider(testAccount, client)
        calendar = TestCalendar.findOrCreate(testAccount, client)
    }

    @After
    fun tearDown() {
        calendar.delete()
        client.closeCompat()
    }


    // event instances

    @Test
    fun testNumDirectInstances_SingleInstance() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 1 instance"
        }
        val uri = LegacyAndroidCalendar(calendar).add(event)

        assertEquals(1, calendar.numDirectInstances(ContentUris.parseId(uri)))
    }

    @Test
    fun testNumDirectInstances_Recurring() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 5 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=5"))
        }
        val uri = LegacyAndroidCalendar(calendar).add(event)

        assertEquals(5, calendar.numDirectInstances(ContentUris.parseId(uri)))
    }

    @Test
    fun testNumDirectInstances_Recurring_Endless() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event without end"
            rRules.add(RRule("FREQ=DAILY"))
        }
        val uri = LegacyAndroidCalendar(calendar).add(event)

        assertNull(calendar.numDirectInstances(ContentUris.parseId(uri)))
    }

    @Test
    fun testNumDirectInstances_Recurring_LateEnd() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 53 years"
            rRules.add(RRule("FREQ=YEARLY;UNTIL=20740119T010203Z"))     // year 2074 is not supported by Android <11 Calendar Storage
        }
        val uri = LegacyAndroidCalendar(calendar).add(event)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            assertEquals(52, calendar.numDirectInstances(ContentUris.parseId(uri)))
        else
            assertNull(calendar.numDirectInstances(ContentUris.parseId(uri)))
    }

    @Test
    fun testNumDirectInstances_Recurring_ManyInstances() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 2 years"
            rRules.add(RRule("FREQ=DAILY;UNTIL=20240120T010203Z"))
        }
        val uri = LegacyAndroidCalendar(calendar).add(event)
        val number = calendar.numDirectInstances(ContentUris.parseId(uri))

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
        val uri = LegacyAndroidCalendar(calendar).add(event)

        assertEquals(4, calendar.numDirectInstances(ContentUris.parseId(uri)))
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
        val uri = LegacyAndroidCalendar(calendar).add(event, syncId = "filename.ics")

        assertEquals(5 - 2, calendar.numDirectInstances(ContentUris.parseId(uri)))
    }


    @Test
    fun testNumInstances_SingleInstance() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 1 instance"
        }
        val uri = LegacyAndroidCalendar(calendar).add(event)

        assertEquals(1, calendar.numInstances(ContentUris.parseId(uri)))
    }

    @Test
    fun testNumInstances_Recurring() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 5 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=5"))
        }
        val uri = LegacyAndroidCalendar(calendar).add(event)

        assertEquals(5, calendar.numInstances(ContentUris.parseId(uri)))
    }

    @Test
    fun testNumInstances_Recurring_Endless() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with infinite instances"
            rRules.add(RRule("FREQ=YEARLY"))
        }
        val uri = LegacyAndroidCalendar(calendar).add(event)

        assertNull(calendar.numInstances(ContentUris.parseId(uri)))
    }

    @Test
    fun testNumInstances_Recurring_LateEnd() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event over 22 years"
            rRules.add(RRule("FREQ=YEARLY;UNTIL=20740119T010203Z"))     // year 2074 not supported by Android <11 Calendar Storage
        }
        val uri = LegacyAndroidCalendar(calendar).add(event)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            assertEquals(52, calendar.numInstances(ContentUris.parseId(uri)))
        else
            assertNull(calendar.numInstances(ContentUris.parseId(uri)))
    }

    @Test
    fun testNumInstances_Recurring_ManyInstances() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event over two years"
            rRules.add(RRule("FREQ=DAILY;UNTIL=20240120T010203Z"))
        }
        val uri = LegacyAndroidCalendar(calendar).add(event)

        assertEquals(
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                365 * 2       // Android <9: does not include UNTIL (incorrect!)
            else
                365 * 2 + 1,  // Android ≥9: includes UNTIL (correct)
            calendar.numInstances(ContentUris.parseId(uri))
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
        val uri = LegacyAndroidCalendar(calendar).add(event, syncId = "filename.ics")

        calendar.getEvent(ContentUris.parseId(uri))!!

        assertEquals(6, calendar.numInstances(ContentUris.parseId(uri)))
    }

}