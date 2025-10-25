/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.synctools.util.AndroidTimeUtils
import java.time.ZoneId

class StartTimeBuilder: AndroidEntityBuilder {

    override fun build(from: Event, main: Event, to: Entity) {
        val values = to.entityValues

        val dtStart = from.requireDtStart()

        // start time: UNIX timestamp
        values.put(Events.DTSTART, dtStart.date.time)

        // start time: timezone ID
        if (DateUtils.isDateTime(dtStart)) {
            /* DTSTART is a DATE-TIME. This can be:
               - date/time with timezone ID ("DTSTART;TZID=Europe/Vienna:20251006T155623")
               - UTC ("DTSTART:20251006T155623Z")
               - floating time ("DTSTART:20251006T155623") */

            if (dtStart.isUtc) {
                // UTC
                values.put(Events.EVENT_TIMEZONE, AndroidTimeUtils.TZID_UTC)

            } else if (dtStart.timeZone != null) {
                // timezone reference – make sure that time zone is known by Android
                values.put(Events.EVENT_TIMEZONE, DateUtils.findAndroidTimezoneID(dtStart.timeZone.id))

            } else {
                // floating time, use system default
                values.put(Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            }

        } else {
            // DTSTART is a DATE
            values.put(Events.EVENT_TIMEZONE, AndroidTimeUtils.TZID_UTC)
        }
    }

}