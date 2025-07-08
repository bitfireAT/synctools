/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.icalendar.Css3Color
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AndroidCalendarTest {

    @get:Rule
    val permissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    private val testAccount = Account("ical4android.AndroidCalendarTest", CalendarContract.ACCOUNT_TYPE_LOCAL)
    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()

    lateinit var client: ContentProviderClient
    lateinit var provider: AndroidCalendarProvider

    @Before
    fun prepare() {
        client = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!

        // make sure there are no colors for testAccount
        provider = AndroidCalendarProvider(testAccount, client)
        provider.removeColorIndices()
        assertEquals(0, countColors())
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
    fun testProvideCss3Colors() {
        provider.provideCss3ColorIndices()
        assertEquals(Css3Color.entries.size, countColors())
    }

    @Test
    fun testInsertColors_AlreadyThere() {
        provider.provideCss3ColorIndices()
        provider.provideCss3ColorIndices()
        assertEquals(Css3Color.entries.size, countColors())
    }

    @Test
    fun testRemoveCss3Colors() {
        provider.provideCss3ColorIndices()

        // insert an event with that color
        val cal = TestCalendar.findOrCreate(testAccount, client)
        try {
            // add event with color
            AndroidEvent(cal, Event(tzRegistry = tzRegistry).apply {
                dtStart = DtStart("20210314T204200Z")
                dtEnd = DtEnd("20210314T204230Z")
                color = Css3Color.limegreen
                summary = "Test event with color"
            }, "remove-colors").add()

            provider.removeColorIndices()
            assertEquals(0, countColors())
        } finally {
            cal.delete()
        }
    }

    private fun countColors(): Int {
        client.query(provider.colorsUri, null, null, null, null)!!.use { cursor ->
            cursor.moveToNext()
            return cursor.count
        }
    }

}