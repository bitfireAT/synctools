/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.test

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.os.Build
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.Event
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import org.junit.Assert
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.util.logging.Logger

/**
 * JUnit ClassRule which initializes the AOSP CalendarProvider.
 *
 * It seems that the calendar provider unfortunately forgets the very first requests when it is used the very first time,
 * maybe by some wrongly synchronized database initialization. So things like querying the instances
 * fails in this case.
 *
 * So this rule is needed to allow tests which need the calendar provider to succeed even when the calendar provider
 * is used the very first time (especially in CI tests / a fresh emulator).
 */
class InitCalendarProviderRule private constructor() : ExternalResource() {

    companion object {

        private var isInitialized = false
        private val logger = Logger.getLogger(InitCalendarProviderRule::javaClass.name)

        fun initialize(): RuleChain = RuleChain
            .outerRule(GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            .around(InitCalendarProviderRule())

    }

    val account = Account(javaClass.name, CalendarContract.ACCOUNT_TYPE_LOCAL)


    override fun before() {
        if (!isInitialized) {
            logger.info("Initializing calendar provider")
            if (Build.VERSION.SDK_INT < 31)
                logger.warning("Calendar provider initialization may or may not work. See InitCalendarProviderRule")

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
        for (i in 0..50) {
            calendarOrNull = createAndVerifyCalendar(provider)
            if (calendarOrNull != null)
                break
            else
                Thread.sleep(100)
        }
        val calendar = calendarOrNull ?: throw IllegalStateException("Couldn't create calendar")

        val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
        try {
            // single event init
            val normalEvent = Event(tzRegistry = tzRegistry).apply {
                dtStart = DtStart("20220120T010203Z")
                summary = "Event with 1 instance"
            }
            val normalLocalEvent = AndroidEvent(calendar, normalEvent, null, null, null, 0)
            normalLocalEvent.add()
            AndroidEvent.numInstances(provider, account, normalLocalEvent.id!!)

            // recurring event init
            val recurringEvent = Event(tzRegistry = tzRegistry).apply {
                dtStart = DtStart("20220120T010203Z")
                summary = "Event over 22 years"
                rRules.add(RRule("FREQ=YEARLY;UNTIL=20740119T010203Z"))     // year needs to be  >2074 (not supported by Android <11 Calendar Storage)
            }
            val localRecurringEvent = AndroidEvent(calendar, recurringEvent, null, null, null, 0)
            localRecurringEvent.add()
            AndroidEvent.numInstances(provider, account, localRecurringEvent.id!!)
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

}