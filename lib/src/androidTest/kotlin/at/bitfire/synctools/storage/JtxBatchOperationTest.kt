/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.JtxCollection
import at.bitfire.ical4android.JtxICalObject
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.GrantPermissionOrSkipRule
import at.techbee.jtx.JtxContract
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class JtxBatchOperationTest {

    @get:Rule
    val permissionRule = GrantPermissionOrSkipRule(TaskProvider.PERMISSIONS_JTX.toSet())

    private val testAccount = Account(javaClass.name, javaClass.packageName)

    lateinit var provider: ContentProviderClient

    @Before
    fun setUp() {
        provider = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            .acquireContentProviderClient(JtxContract.AUTHORITY)!!
    }

    @After
    fun tearDown() {
        provider.closeCompat()
    }


    @Test
    fun testJtxBoard_OperationsPerYieldPoint_501() {
        val builder = JtxBatchOperation(provider)
        val uri = JtxCollection.create(testAccount, provider, ContentValues().apply {
            put(JtxContract.JtxCollection.ACCOUNT_NAME, testAccount.name)
            put(JtxContract.JtxCollection.ACCOUNT_TYPE, testAccount.type)
            put(JtxContract.JtxCollection.DISPLAYNAME, javaClass.name)
        })
        val collectionId = ContentUris.parseId(uri)

        try {
            // 501 operations should succeed with JtxBatchOperation
            repeat(501) { idx ->
                builder.enqueue(
                    BatchOperation.CpoBuilder.newInsert(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount))
                        .withValue(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collectionId)
                        .withValue(JtxContract.JtxICalObject.SUMMARY, "Entry $idx")
                )
            }
            builder.commit()
        } finally {
            val collection = JtxCollection<JtxICalObject>(testAccount, provider, mockk(), collectionId)
            collection.delete()
        }
    }

}