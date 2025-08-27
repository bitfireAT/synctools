/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.synctools.icalendar.isAllDay
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.util.TimeZones

class EventTimeZoneBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        /*
        Events.EVENT_TIME_ZONE is required by Android:
        https://developer.android.com/reference/android/provider/CalendarContract.Events#operations

        So if it's not available in the VEvent, we use the default time zone.
        */

        val dtStart = from.startDate ?: DtStart()
        val dtStartDate = dtStart.date ?: DateTime()

        val androidTzId = if (dtStartDate.isAllDay()) {
            // DATE event
            AndroidTimeUtils.TZID_ALLDAY

        } else {
            // DATE-TIME event (can be with TZID, ...Z or floating)
            val dtStartTz = if (dtStart.isUtc)
                TimeZones.getUtcTimeZone()
            else
                dtStart.timeZone ?: TimeZone.getDefault()

            /*
            The value of DTSTART;TZID=... may not be available on the local system. For instance, Windows-based
            clients may use Windows time zone names: https://github.com/unicode-org/cldr/blob/main/common/supplemental/windowsZones.xml

            We have to make sure that the used value is available on the system so that it can calculate recurrences etc.
            */
            DateUtils.findAndroidTimezoneID(dtStartTz.id)
        }

        to.entityValues.put(Events.EVENT_TIMEZONE, androidTzId)
        return true
    }

}