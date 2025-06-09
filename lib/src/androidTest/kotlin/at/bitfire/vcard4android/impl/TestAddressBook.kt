/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.vcard4android.impl

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import at.bitfire.vcard4android.*

class TestAddressBook(
        account: Account,
        provider: ContentProviderClient
): AndroidAddressBook<AndroidContact, AndroidGroup>(account, provider, ContactFactory, GroupFactory) {

    object ContactFactory: AndroidContactFactory<AndroidContact> {

        override fun fromProvider(addressBook: AndroidAddressBook<AndroidContact, *>, values: ContentValues) =
                AndroidContact(addressBook, values)

    }


    object GroupFactory: AndroidGroupFactory<AndroidGroup> {

        override fun fromProvider(addressBook: AndroidAddressBook<*, AndroidGroup>, values: ContentValues) =
                AndroidGroup(addressBook, values)

    }
    
}