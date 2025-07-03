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
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.icalendar.Css3Color
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AndroidCalendarProviderTest {

    @get:Rule
    val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    val testAccount = Account(javaClass.name, CalendarContract.ACCOUNT_TYPE_LOCAL)

    lateinit var client: ContentProviderClient
    lateinit var provider: AndroidCalendarProvider

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
        val calendar = TestCalendar.findOrCreate(testAccount, client)
        try {
            // add event with color
            calendar.createEventFromDataObject(
                event = Event().apply {
                    dtStart = DtStart("20210314T204200Z")
                    dtEnd = DtEnd("20210314T204230Z")
                    color = Css3Color.limegreen
                    summary = "Test event with color"
                },
                syncId = "remove-colors"
            )

            provider.removeColorIndices()
            assertEquals(0, countColors())
        } finally {
            calendar.delete()
        }
    }

    private fun countColors(): Int {
        client.query(provider.colorsUri, null, null, null, null)!!.use { cursor ->
            cursor.moveToNext()
            return cursor.count
        }
    }

}