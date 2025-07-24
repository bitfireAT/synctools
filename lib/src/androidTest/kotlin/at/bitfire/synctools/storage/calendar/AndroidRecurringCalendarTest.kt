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
import android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AndroidRecurringCalendarTest {

    @get:Rule
    val permissonRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val testAccount = Account(javaClass.name, ACCOUNT_TYPE_LOCAL)

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
    fun testAddEventAndExceptions() {
        // TODO
    }

    @Test
    fun testUpdateEventAndExceptions() {
        // TODO
    }

    @Test
    fun testDeleteEventAndExceptions() {
        // TODO
    }

}