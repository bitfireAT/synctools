/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.vcard4android.contactrow

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.Event
import androidx.annotation.VisibleForTesting
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.LabeledProperty
import at.bitfire.vcard4android.Utils.trimToNull
import at.bitfire.vcard4android.property.XAbDate
import ezvcard.property.Anniversary
import ezvcard.property.Birthday
import ezvcard.util.PartialDate
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.Temporal

object EventHandler : DataRowHandler() {

    // source: https://cs.android.com/android/platform/superproject/main/+/main:packages/apps/Contacts/src/com/android/contacts/util/CommonDateUtils.java;drc=61197364367c9e404c7da6900658f1b16c42d0da;l=25
    private val acceptableFormats: List<Pair<DateTimeFormatter, (String, DateTimeFormatter) -> Temporal>> = listOf(
        // Formats provided by Android's CommonDateUtils
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX") to OffsetDateTime::parse,
        DateTimeFormatter.ofPattern("yyyy-MM-dd") to LocalDate::parse,
        // Additional common formats
        DateTimeFormatter.ISO_OFFSET_DATE_TIME to OffsetDateTime::parse, // "yyyy-MM-dd'T'HH:mm:ssXXX"
    )

    override fun forMimeType() = Event.CONTENT_ITEM_TYPE

    /**
     * Tries to parse a date string into a [Temporal] object using multiple acceptable formats.
     * Returns the parsed [Temporal] if successful, or `null` if none of the formats match.
     * @param dateString The date string to parse.
     * @return If format is:
     * - `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` or `yyyy-MM-dd'T'HH:mm:ssXXX` -> [OffsetDateTime]
     * - `yyyy-MM-dd` -> [LocalDate]
     * - else -> `null`
     */
    @VisibleForTesting
    internal fun parseStartDate(dateString: String): Temporal? {
        for ((formatter, parse) in acceptableFormats) {
            try {
                return parse(dateString, formatter)
            } catch (_: DateTimeParseException) {
                // ignore: given date is not valid
                continue
            }
        }

        // could not parse date
        return null
    }

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        val dateStr = values.getAsString(Event.START_DATE) ?: return
        val full: Temporal? = parseStartDate(dateStr)
        val partial: PartialDate? = if (full == null) try {
            PartialDate.parse(dateStr)
        } catch (_: IllegalArgumentException) {
            null
        } else {
            null
        }

        if (full != null || partial != null)
            when (values.getAsInteger(Event.TYPE)) {
                Event.TYPE_ANNIVERSARY ->
                    contact.anniversary =
                        if (full != null) Anniversary(full) else Anniversary(partial)

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