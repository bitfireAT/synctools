/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CalendarBatchOperationTest {

    @get:Rule
    val permissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR
    )

    private val testAccount = Account(javaClass.name, CalendarContract.ACCOUNT_TYPE_LOCAL)

    lateinit var provider: ContentProviderClient

    @Before
    fun setUp() {
        provider = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            .acquireContentProviderClient(CalendarContract.AUTHORITY)!!
    }

    @After
    fun tearDown() {
        // delete all events in test account
        provider.delete(
            Events.CONTENT_URI,
            "${Events.ACCOUNT_TYPE}=? AND ${Events.ACCOUNT_NAME}=?",
            arrayOf(testAccount.type, testAccount.name)
        )
        provider.closeCompat()
    }


    @Test
    fun testCalendarProvider_OperationsPerYieldPoint_501() {
        val builder = CalendarBatchOperation(provider)

        // 501 operations should succeed with CalendarBatchOperation
        repeat(501) { idx ->
            builder.enqueue(
                BatchOperation.CpoBuilder.newInsert(Events.CONTENT_URI.asSyncAdapter(testAccount))
                    .withValue(Events.TITLE, "Event $idx"))
        }
        builder.commit()
    }

}