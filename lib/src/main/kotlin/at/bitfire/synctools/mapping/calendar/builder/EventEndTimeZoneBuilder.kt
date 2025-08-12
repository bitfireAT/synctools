/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.ical4android.util.DateUtils
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.util.TimeZones

class EventEndTimeZoneBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        // DTSTART required to determine whether the event is all-day
        val dtStartDate = from.startDate?.date ?: return false

        val dtEndDate = from.endDate?.date

        val endTzId: String? = if (dtEndDate != null) {
            if (dtStartDate is DateTime) {
                // DTSTART is DATE-TIME
                if (dtEndDate is DateTime) {
                    // DTEND is DATE-TIME and can be UTC or have a time zone
                    if (dtEndDate.isUtc)
                        TimeZones.getUtcTimeZone().id
                    else
                        dtEndDate.timeZone?.id
                } else {
                    // DTSTART is DATE-TIME, but DTEND is DATE.
                    // DTEND will be rewritten to the time of DTSTART, so we also use the timezone of DTSTART.
                    dtStartDate.timeZone?.id
                }

            } else {
                // DTSTART is DATE → DTEND is also rewritten to DATE and thus the DTEND time zone is UTC
                AndroidTimeUtils.TZID_ALLDAY
            }

        } else {
            // no DTEND → no end time zone
            null
        }

        // make sure the time zone is available on the system
        val androidEndTzId = endTzId?.let { DateUtils.findAndroidTimezoneID(it) }

        to.entityValues.put(CalendarContract.Events.EVENT_END_TIMEZONE, androidEndTzId)
        return true
    }

}