/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.vcard4android.property

import ezvcard.io.scribe.UriPropertyScribe
import ezvcard.property.Member

class XAddressBookServerMember(value: String?): Member(value) {

    object Scribe :
        UriPropertyScribe<XAddressBookServerMember>(XAddressBookServerMember::class.java, "X-ADDRESSBOOKSERVER-MEMBER") {

        override fun _parseValue(value: String?) = XAddressBookServerMember(value)

    }

}