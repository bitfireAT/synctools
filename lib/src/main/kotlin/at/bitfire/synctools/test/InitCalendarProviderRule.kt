/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.test

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Entity
import android.os.Build
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import org.junit.Assert
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.logging.Logger

/**
 * JUnit ClassRule which initializes the AOSP calendar provider so that queries work as they should.
 *
 * It seems that the calendar provider unfortunately forgets the very first requests when it is used the very first time
 * (like in a fresh emulator in CI tests or directly after clearing the calendar provider storage), so things like querying
 * the instances fails in that case.
 *
 * Android-internal `CalendarProvider2Tests` use a `CalendarProvider2ForTesting` that disables the asynchronous code
 * which causes the problems (like `updateTimezoneDependentFields()`). Unfortunately, we can't do that because we have
 * to use the real calendar provider.
 *
 * So this rule brings the calendar provider into its "normal working state" by creating an event and querying
 * its instances as long until the result is correct. This works for now, but may fail in the future because
 * it's only a trial-and-error workaround.
 */
class InitCalendarProviderRule private constructor() : TestRule {

    companion object {

        private var isInitialized = false

        fun initialize(): RuleChain = RuleChain
            .outerRule(GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            .around(InitCalendarProviderRule())

    }

    private val logger
        get() = Logger.getLogger(javaClass.name)

    val account = Account(javaClass.name, CalendarContract.ACCOUNT_TYPE_LOCAL)


    // TestRule implementation

    override fun apply(base: Statement?, description: Description?) = object: Statement() {
        override fun evaluate() {
            before()
            base?.evaluate()
            // after() not needed
        }
    }


    // custom wrappers

    fun before() {
        if (!isInitialized) {
            logger.warning("Calendar provider initialization may or may not work. See InitCalendarProviderRule KDoc.")

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val client = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)
            Assert.assertNotNull("Couldn't acquire calendar provider", client)

            client!!.use {
                initCalendarProvider(client)
                isInitialized = true
            }
        }
    }

    private fun initCalendarProvider(provider: ContentProviderClient) {
        // Sometimes, the calendar provider returns an ID for the created calendar, but then fails to find it.
        var calendarOrNull: AndroidCalendar? = null
        tryUntilTrue {
            calendarOrNull = createAndVerifyCalendar(provider)
            calendarOrNull != null
        }
        val calendar = calendarOrNull!!

        try {
            // insert recurring event and query instances until the result is correct (max 50 times)
            tryUntilTrue {
                val syncId = "test-sync-id"
                val id = calendar.addEvent(Entity(contentValuesOf(
                    Events.CALENDAR_ID to calendar.id,
                    Events._SYNC_ID to syncId,
                    Events.DTSTART to 1642640523000,
                    Events.DURATION to "PT1H",
                    Events.TITLE to "Event with 5 instances, two of them are exceptions",
                    Events.RRULE to "FREQ=DAILY;COUNT=5"
                )))
                calendar.addEvent(Entity(contentValuesOf(
                    Events.CALENDAR_ID to calendar.id,
                    Events.ORIGINAL_SYNC_ID to syncId,
                    Events.ORIGINAL_INSTANCE_TIME to 1642640523000 + 2*86400000,
                    Events.DTSTART to 1642640523000 + 2*86400000 + 3600000, // one hour later
                    Events.DTEND to 1642640523000 + 2*86400000 + 2*3600000,
                    Events.TITLE to "Exception on 3rd day",
                )))
                calendar.addEvent(Entity(contentValuesOf(
                    Events.CALENDAR_ID to calendar.id,
                    Events.ORIGINAL_SYNC_ID to syncId,
                    Events.ORIGINAL_INSTANCE_TIME to 1642640523000 + 4*86400000,
                    Events.DTSTART to 1642640523000 + 4*86400000 + 3600000, // one hour later
                    Events.DTEND to 1642640523000 + 4*86400000 + 2*3600000,
                    Events.TITLE to "Exception on 5th day",
                )))
                calendar.numInstances(id) == 3
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)     // year 2074 is not supported by Android <11 Calendar Storage
                tryUntilTrue {
                    val id = calendar.addEvent(Entity(contentValuesOf(
                        Events.CALENDAR_ID to calendar.id,
                        Events.DTSTART to 1642640523000,
                        Events.DURATION to "PT1H",
                        Events.TITLE to "Event until 2074",
                        Events.RRULE to "FREQ=YEARLY;UNTIL=20740119T010203Z"
                    )))
                    calendar.numInstances(id) == 52
                }
        } finally {
            calendar.delete()
        }
    }

    private fun createAndVerifyCalendar(provider: ContentProviderClient): AndroidCalendar? {
        val calendarProvider = AndroidCalendarProvider(account, provider)
        val id = calendarProvider.createCalendar(contentValuesOf(
            Calendars.ACCOUNT_NAME to account.name,
            Calendars.ACCOUNT_TYPE to account.type
        ))

        return try {
            calendarProvider.getCalendar(id)
        } catch (e: Exception) {
            logger.warning("Couldn't find calendar after creation: $e")
            null
        }
    }

    private fun tryUntilTrue(block: () -> Boolean) {
        for (i in 1..100) {     // wait up to 100*100 ms = 10 seconds
            val resultOk = block()
            if (resultOk)
                return
            Thread.sleep(100)
        }
        throw IllegalStateException("Couldn't initialize calendar provider to get desired result")
    }

}