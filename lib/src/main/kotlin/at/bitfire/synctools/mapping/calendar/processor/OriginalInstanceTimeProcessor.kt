/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.DateUtils
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.util.TimeZones

class OriginalInstanceTimeProcessor: AndroidEventFieldProcessor {

    private val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }

    override fun process(from: Entity, main: Entity, to: Event) {
        // only applicable to exceptions, not to main events
        if (from === main)
            return

        val values = from.entityValues
        values.getAsLong(Events.ORIGINAL_INSTANCE_TIME)?.let { originalInstanceTime ->
            val originalAllDay = (values.getAsInteger(Events.ORIGINAL_ALL_DAY) ?: 0) != 0
            val originalDate =
                if (originalAllDay)
                    Date(originalInstanceTime)
                else
                    DateTime(originalInstanceTime)

            if (originalDate is DateTime) {
                // get DTSTART time zone
                val startTzId = DateUtils.findAndroidTimezoneID(values.getAsString(Events.EVENT_TIMEZONE))
                val startTz = tzRegistry.getTimeZone(startTzId)

                if (startTz != null) {
                    if (TimeZones.isUtc(startTz))
                        originalDate.isUtc = true
                    else
                        originalDate.timeZone = startTz
                }
            }

            to.recurrenceId = RecurrenceId(originalDate)
        }
    }

}