/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.vcard4android.contactrow

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.Event
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.LabeledProperty
import at.bitfire.vcard4android.Utils.trimToNull
import at.bitfire.vcard4android.property.XAbDate
import ezvcard.property.Anniversary
import ezvcard.property.Birthday
import ezvcard.util.PartialDate
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.Temporal
import java.util.logging.Level

object EventHandler: DataRowHandler() {

    override fun forMimeType() = Event.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        val dateStr = values.getAsString(Event.START_DATE) ?: return
        var full: Temporal? = null
        var partial: PartialDate? = null
        try {
            full = LocalDate.parse(dateStr)
        } catch(_: DateTimeParseException) {
            try {
                // Some server providers (e.g. t-mobile) include time information
                // This is allowed by RFC2426: https://www.rfc-editor.org/rfc/rfc2426#section-3.1.5
                // And RFC6350: https://www.rfc-editor.org/rfc/rfc6350#section-6.2.5
                full = ZonedDateTime.parse(dateStr)
            } catch (_: DateTimeParseException) {
                try {
                    partial = PartialDate.parse(dateStr)
                } catch (e: IllegalArgumentException) {
                    logger.log(Level.WARNING, "Couldn't parse birthday/anniversary date from database: $dateStr", e)
                }
            }
        }

        if (full != null || partial != null)
            when (values.getAsInteger(Event.TYPE)) {
                Event.TYPE_ANNIVERSARY ->
                    contact.anniversary = if (full != null) Anniversary(full) else Anniversary(partial)
                Event.TYPE_BIRTHDAY ->
                    contact.birthDay = if (full != null) Birthday(full) else Birthday(partial)
                /* Event.TYPE_OTHER,
                Event.TYPE_CUSTOM */
                else -> {
                    val abDate = if (full != null) XAbDate(full) else XAbDate(partial)
                    val label = values.getAsString(Event.LABEL).trimToNull()
                    contact.customDates += LabeledProperty(abDate, label)
                }
            }
    }

}