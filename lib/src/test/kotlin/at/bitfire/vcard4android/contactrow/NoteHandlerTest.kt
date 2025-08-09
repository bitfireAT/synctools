/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.vcard4android.contactrow

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.Note
import at.bitfire.vcard4android.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteHandlerTest {

    @Test
    fun testNote_Empty() {
        val contact = Contact()
        NoteHandler.handle(ContentValues().apply {
            putNull(Note.NOTE)
        }, contact)
        assertNull(contact.note)
    }

    @Test
    fun testNote_Value() {
        val contact = Contact()
        NoteHandler.handle(ContentValues().apply {
            put(Note.NOTE, "Some Note")
        }, contact)
        assertEquals("Some Note", contact.note)
    }

}