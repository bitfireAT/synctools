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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProviderTest {

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
        calendar.delete()

        calendar = TestCalendar.findOrCreate(testAccount, client)
    }

    @After
    fun tearDown() {
        client.closeCompat()
    }


    // event instances

    @Test
    fun testInsertEvent() {
        val startTime = System.currentTimeMillis()
        val mainId = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events._SYNC_ID to "event1",
            Events.DTSTART to startTime,
            Events.EVENT_TIMEZONE to "Europe/Vienna",
            Events.DURATION to "PT1H",
            Events.TITLE to "Sample Event",
            Events.RRULE to "FREQ=DAILY;COUNT=10"
        )))

        calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.ORIGINAL_ALL_DAY to 0,
            //Events.ORIGINAL_ID to mainId,
            Events.ORIGINAL_SYNC_ID to "event1",
            Events.ORIGINAL_INSTANCE_TIME to startTime + 86400000,
            Events.DTSTART to startTime + 86400000,
            Events.EVENT_TIMEZONE to "Europe/Vienna",
            Events.DTEND to startTime + 86400000 + 3600000,
            Events.TITLE to "EXCEPTION"
        )))

        System.err.println("FOUND EVENTS:")
        calendar.iterateEvents(null, null) { event ->
            System.err.println("- $event")
        }
    }
}
