/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.vcard4android.property

import ezvcard.io.scribe.StringPropertyScribe
import ezvcard.property.TextProperty

class XPhoneticLastName(value: String?): TextProperty(value) {

    object Scribe :
        StringPropertyScribe<XPhoneticLastName>(XPhoneticLastName::class.java, "X-PHONETIC-LAST-NAME") {

        override fun _parseValue(value: String?) = XPhoneticLastName(value)

    }

}