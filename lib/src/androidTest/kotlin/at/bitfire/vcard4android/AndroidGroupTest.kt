/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.vcard4android

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.provider.ContactsContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.vcard4android.impl.TestAddressBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test

class AndroidGroupTest {

    companion object {
        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)!!

        private val testAddressBookAccount = Account("AndroidContactGroupTest", "at.bitfire.vcard4android")

        private lateinit var provider: ContentProviderClient
        private lateinit var addressBook: TestAddressBook

        @BeforeClass
        @JvmStatic
        fun connect() {
            val context = InstrumentationRegistry.getInstrumentation().context
            provider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)!!
            assertNotNull(provider)

            addressBook = TestAddressBook(testAddressBookAccount, provider)
        }

        @BeforeClass
        @JvmStatic
        fun disconnect() {
            @Suppress("DEPRECATION")
            provider.release()
        }
    }

    @Before
    fun setUp() {
        removeGroups()
    }

    @After
    fun tearDown() {
        removeGroups()
    }

    private fun removeGroups() {
        addressBook.provider!!.delete(addressBook.groupsSyncUri(), null, null)
        assertEquals(0, addressBook.queryGroups(null, null).size)
    }


    @Test
    fun testCreateReadDeleteGroup() {
        val contact = Contact()
        contact.displayName = "at.bitfire.vcard4android-AndroidGroupTest"
        contact.note = "(test group)"

        // ensure we start without this group
        assertEquals(0, addressBook.queryGroups("${ContactsContract.Groups.TITLE}=?", arrayOf(contact.displayName!!)).size)

        // create group
        val group = AndroidGroup(addressBook, contact, null, null)
        group.add()
        val groups = addressBook.queryGroups("${ContactsContract.Groups.TITLE}=?", arrayOf(contact.displayName!!))
        assertEquals(1, groups.size)
        val contact2 = groups.first().getContact()
        assertEquals(contact.displayName, contact2.displayName)
        assertEquals(contact.note, contact2.note)

        // delete group
        group.delete()
        assertEquals(0, addressBook.queryGroups("${ContactsContract.Groups.TITLE}=?", arrayOf(contact.displayName!!)).size)
    }

    @Test
    fun testAdd_readOnly() {
        addressBook.readOnly = true

        val contact = Contact()
        contact.displayName = "at.bitfire.vcard4android-AndroidGroupTest"
        contact.note = "(test group)"

        // ensure we start without this group
        assertEquals(0, addressBook.queryGroups("${ContactsContract.Groups.TITLE}=?", arrayOf(contact.displayName!!)).size)

        // create group
        val group = AndroidGroup(addressBook, contact, null, null)
        group.add()
        val groups = addressBook.queryGroups("${ContactsContract.Groups.GROUP_IS_READ_ONLY}=?", arrayOf("1"))
        assertEquals(1, groups.size)

        // delete group
        group.delete()
        assertEquals(0, addressBook.queryGroups("${ContactsContract.Groups.TITLE}=?", arrayOf(contact.displayName!!)).size)
    }

    @Test
    fun testAdd_notReadOnly() {
        addressBook.readOnly = false

        val contact = Contact()
        contact.displayName = "at.bitfire.vcard4android-AndroidGroupTest"
        contact.note = "(test group)"

        // ensure we start without this group
        assertEquals(0, addressBook.queryGroups("${ContactsContract.Groups.TITLE}=?", arrayOf(contact.displayName!!)).size)

        // create group
        val group = AndroidGroup(addressBook, contact, null, null)
        group.add()
        val groups = addressBook.queryGroups("${ContactsContract.Groups.GROUP_IS_READ_ONLY}=?", arrayOf("0"))
        assertEquals(1, groups.size)

        // delete group
        group.delete()
        assertEquals(0, addressBook.queryGroups("${ContactsContract.Groups.TITLE}=?", arrayOf(contact.displayName!!)).size)
    }

}
