/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Entity
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.TimeZone

/**
 * Some integration tests to verify Android Calendar Provider behavior that don't fit anywhere else.
 */
class CalendarProviderBehaviorTest {

    @get:Rule
    val permissonRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val testAccount = Account(javaClass.name, CalendarContract.ACCOUNT_TYPE_LOCAL)

    lateinit var client: ContentProviderClient
    lateinit var provider: AndroidCalendarProvider

    lateinit var calendar: AndroidCalendar

    @Before
    fun prepare() {
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


    @Test
    fun testDuration_PT0S() {
        val now = System.currentTimeMillis()
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.TITLE to "Some Event",
            Events.DTSTART to now,
            Events.EVENT_TIMEZONE to TimeZone.getDefault().id,
            Events.DURATION to "PT0S",
            Events.RRULE to "FREQ=DAILY;COUNT=1"
        )))
        val event = calendar.getEventRow(id)!!

        // verify that provider has not crashed
        assertEquals("PT0S", event.getAsString(Events.DURATION))
    }


    @Test
    fun testStatus_InsertWithoutStatus() {
        val now = System.currentTimeMillis()
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.TITLE to "Some Event",
            Events.DTSTART to now,
            Events.EVENT_TIMEZONE to TimeZone.getDefault().id,
            Events.DTEND to now + 3600000
        )))
        val event = calendar.getEventRow(id)!!

        // verify that provider keeps STATUS=null
        assertNull(event.getAsInteger(Events.STATUS))
    }

    @Test(expected = NullPointerException::class)
    fun testStatus_UpdateStatusToNull_Crashes() {
        val now = System.currentTimeMillis()
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.TITLE to "Some Event",
            Events.DTSTART to now,
            Events.EVENT_TIMEZONE to TimeZone.getDefault().id,
            Events.DTEND to now + 3600000,
            Events.STATUS to Events.STATUS_CONFIRMED
        )))
        val event = calendar.getEventRow(id)!!
        assertEquals(Events.STATUS_CONFIRMED, event.getAsInteger(Events.STATUS))

        // directly update STATUS from non-null to null
        // crashes with NPE in calendar provider
        calendar.client.update(calendar.eventUri(id), contentValuesOf(
            Events.STATUS to null
        ), null, null)
    }

}