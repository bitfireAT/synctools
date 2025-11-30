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
import android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.test.assertContentValuesEqual
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests some Android calendar provider behavior that is not well-documented.
 */
class AndroidCalendarProviderBehaviorTest {

    @get:Rule
    val permissonRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )!!

    private val testAccount = Account(javaClass.name, ACCOUNT_TYPE_LOCAL)

    lateinit var client: ContentProviderClient
    lateinit var provider: AndroidCalendarProvider
    lateinit var calendar: AndroidCalendar

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        client = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!
        provider = AndroidCalendarProvider(testAccount, client)

        calendar = TestCalendar.findOrCreate(testAccount, client)
    }

    @After
    fun tearDown() {
        client.closeCompat()
    }


    /**
     * To make sure that's not a problem to insert an event with DTEND = DTSTART.
     */
    @Test
    fun testInsertEventWithDtEndEqualsDtStart() {
        val values = contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to 1759403653000,    // Thu Oct 02 2025 11:14:13 GMT+0000
            Events.DTEND to 1759403653000,
            Events.TITLE to "Event with DTSTART = DTEND"
        )
        val id = calendar.addEvent(Entity(values))

        // Google Calendar 2025.44.1-827414499-release correctly shows this event [2025/11/29]

        val event2 = calendar.getEventRow(id)
        assertContentValuesEqual(values, event2!!, onlyFieldsInExpected = true)
    }

    /**
     * To make sure that's not a problem to insert an (invalid/useless) RRULE with UNTIL before the event's DTSTART.
     */
    @Test
    fun testInsertEventWithRRuleUntilBeforeDtStart() {
        val values = contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to 1759403653000,    // Thu Oct 02 2025 11:14:13 GMT+0000
            Events.DURATION to "PT1H",
            Events.TITLE to "Event with useless RRULE",
            Events.RRULE to "FREQ=DAILY;UNTIL=20251002T000000Z"
        )
        val id = calendar.addEvent(Entity(values))

        val event2 = calendar.getEventRow(id)
        assertContentValuesEqual(values, event2!!, onlyFieldsInExpected = true)
    }

    /**
     * To verify that it's a problem to insert a recurring all-day event with a duration of zero seconds.
     * See:
     *
     * - https://github.com/bitfireAT/davx5-ose/issues/1823
     * - https://github.com/bitfireAT/synctools/issues/144
     */
    @Test(expected = LocalStorageException::class)
    fun testInsertRecurringAllDayEventWithDurationZeroSeconds() {
        val values = contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.ALL_DAY to 1,
            Events.DTSTART to 1763510400000,    // Wed Nov 19 2025 00:00:00 GMT+0000
            Events.DURATION to "PT0S",
            Events.TITLE to "Recurring all-day event with zero seconds duration",
            Events.RRULE to "FREQ=DAILY;UNTIL=20251122"
        )
        calendar.addEvent(Entity(values))
    }

    /**
     * To make sure that it's not a problem to insert a recurring all-day event with a duration of zero days.
     */
    @Test
    fun testInsertRecurringAllDayEventWithDurationZeroDays() {
        val values = contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.ALL_DAY to 1,
            Events.DTSTART to 1763510400000,    // Wed Nov 19 2025 00:00:00 GMT+0000
            Events.DURATION to "P0D",
            Events.TITLE to "Recurring all-day event with zero seconds duration",
            Events.RRULE to "FREQ=DAILY;UNTIL=20251122"
        )
        val id = calendar.addEvent(Entity(values))

        val event2 = calendar.getEventRow(id)
        assertContentValuesEqual(values, event2!!, onlyFieldsInExpected = true)
    }

    /**
     * To make sure that it's not a problem to insert a recurring event with a duration of zero seconds.
     */
    @Test
    fun testInsertRecurringNonAllDayEventWithDurationZeroSeconds() {
        val values = contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to 1759403653000,    // Thu Oct 02 2025 11:14:13 GMT+0000
            Events.DURATION to "PT0S",
            Events.TITLE to "Recurring non-all-day event with zero seconds duration",
            Events.RRULE to "FREQ=DAILY;UNTIL=20251002T000000Z"
        )
        val id = calendar.addEvent(Entity(values))

        val event2 = calendar.getEventRow(id)
        assertContentValuesEqual(values, event2!!, onlyFieldsInExpected = true)
    }


    /**
     * Reported as https://issuetracker.google.com/issues/446730408.
     */
    @Test(expected = NullPointerException::class)
    fun testUpdateEventStatusFromNonNullToNull() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to System.currentTimeMillis(),
            Events.DTEND to System.currentTimeMillis() + 3600000,
            Events.TITLE to "Some Event (Status tentative)",
            Events.STATUS to Events.STATUS_TENTATIVE
        )))

        calendar.updateEventRow(id, contentValuesOf(
            Events.STATUS to null,      // updating status to null causes NullPointerException
            Events.TITLE to "Some Event (Status null)"
        ))
    }

    @Test
    fun testUpdateEventStatusFromNullToNotPresent() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to System.currentTimeMillis(),
            Events.DTEND to System.currentTimeMillis() + 3600000,
            Events.TITLE to "Some Event (Status tentative)",
            Events.STATUS to null
        )))

        // No problem because STATUS is not explicitly set.
        calendar.updateEventRow(id, contentValuesOf(
            //Events.STATUS to null,
            Events.TITLE to "Some Event (Status null)"
        ))
    }

    /**
     * Reported as https://issuetracker.google.com/issues/446730408.
     */
    @Test(expected = NullPointerException::class)
    fun testUpdateEventStatusFromNullToNull() {
        val id = calendar.addEvent(Entity(contentValuesOf(
            Events.CALENDAR_ID to calendar.id,
            Events.DTSTART to System.currentTimeMillis(),
            Events.DTEND to System.currentTimeMillis() + 3600000,
            Events.TITLE to "Some Event (Status tentative)",
            Events.STATUS to null
        )))

        calendar.updateEventRow(id, contentValuesOf(
            Events.STATUS to null,      // updating status to null causes NullPointerException
            Events.TITLE to "Some Event (Status null)"
        ))
    }

}