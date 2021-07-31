package at.bitfire.vcard4android.property

import ezvcard.io.scribe.StringPropertyScribe
import ezvcard.property.TextProperty

class XPhoneticMiddleName(value: String?): TextProperty(value) {

    object Scribe :
        StringPropertyScribe<XPhoneticMiddleName>(XPhoneticMiddleName::class.java, "X-PHONETIC-MIDDLE-NAME") {

        override fun _parseValue(value: String?) = XPhoneticMiddleName(value)

    }

}