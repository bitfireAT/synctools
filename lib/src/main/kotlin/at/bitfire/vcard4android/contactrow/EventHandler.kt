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
import at.bitfire.vcard4android.contactrow.EventHandler.fullDateFormat
import at.bitfire.vcard4android.contactrow.EventHandler.fullDateTimeFormats
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

    // CommonDateUtils: https://android.googlesource.com/platform/packages/apps/Contacts/+/c326c157541978c180be4e3432327eceb1e66637/src/com/android/contacts/util/CommonDateUtils.java#25

    /**
     * Date formats for full date with time. Converts to [OffsetDateTime].
     */
    private val fullDateTimeFormats = listOf(
        // Provided by Android's CommonDateUtils
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
        // "yyyy-MM-dd'T'HH:mm:ssXXX"
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
    )

    /**
     * Date format for full date without time. Converts to [LocalDate].
     */
    private val fullDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")


    override fun forMimeType() = Event.CONTENT_ITEM_TYPE

    /**
     * Tries to parse a date string into a [Temporal] object using multiple acceptable formats.
     * Returns the parsed [Temporal] if successful, or `null` if none of the formats match.
     * @param dateString The date string to parse.
     * @return If format is:
     * - `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` or `yyyy-MM-dd'T'HH:mm:ssXXX` ([fullDateTimeFormats]) -> [OffsetDateTime]
     * - `yyyy-MM-dd` ([fullDateFormat]) -> [LocalDate]
     * - else -> `null`
     */
    @VisibleForTesting
    internal fun parseFullDate(dateString: String): Temporal? {
        for (formatter in fullDateTimeFormats) {
            try {
                return OffsetDateTime.parse(dateString, formatter)
            } catch (_: DateTimeParseException) {
                // ignore: given date is not valid
            }
        }

        // try parsing as full date only (no time)
        try {
            return LocalDate.parse(dateString, fullDateFormat)
        } catch (_: DateTimeParseException) {
            // ignore: given date is not valid
        }

        // could not parse date
        return null
    }

    /**
     * Tries to parse a date string into a [PartialDate] object.
     * Returns the parsed [PartialDate] if successful, or `null` if parsing fails.
     *
     * Does some preprocessing to handle 'Z' suffix and strip nanoseconds, both not supported by
     * [PartialDate.parse].
     *
     * @param dateString The date string to parse.
     * @return The parsed [PartialDate] or `null` if parsing fails.
     */
    @VisibleForTesting
    internal fun parsePartialDate(dateString: String): PartialDate? {
        return try {
            // convert Android partial date/date-time to vCard partial date/date-time so that it can be parsed by ez-vcard

            val withoutZ = if (dateString.endsWith('Z')) {
                // 'Z' is not supported for suffix in PartialDate, replace with actual offset
                dateString.removeSuffix("Z") + "+00:00"
            } else {
                dateString
            }

            val regex = "\\.\\d+".toRegex()
            if (withoutZ.contains(regex)) {
                // partial dates do not accept nanoseconds, so strip them if present
                val withoutNanos = withoutZ.replace(regex, "")
                PartialDate.parse(withoutNanos)
            } else {
                PartialDate.parse(withoutZ)
            }
        } catch (_: IllegalArgumentException) {
            // An error was thrown by PartialDate.parse
            null
        }
    }

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        val dateStr = values.getAsString(Event.START_DATE) ?: return
        val full: Temporal? = parseFullDate(dateStr)
        val partial: PartialDate? = if (full == null) {
            parsePartialDate(dateStr)
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