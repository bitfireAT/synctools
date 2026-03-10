/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.toEpochMillis
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jZonedDateTime
import at.bitfire.synctools.icalendar.requireDtStart
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.parameter.TzId
import java.time.ZoneId
import java.time.temporal.Temporal

class StartTimeBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues

        val dtStart = from.requireDtStart<Temporal>()
        val dtStartZonedDateTime = dtStart.date.toIcal4jZonedDateTime()

        dtStart.getParameter<Related>(Parameter.RELATED).orElse(Related.START)


        // start time: UNIX timestamp
        values.put(Events.DTSTART, dtStartZonedDateTime.toEpochMillis())

        // start time: timezone ID
        if (DateUtils.isDateTime(dtStart)) {
            /* DTSTART is a DATE-TIME. This can be:
               - date/time with timezone ID ("DTSTART;TZID=Europe/Vienna:20251006T155623")
               - UTC ("DTSTART:20251006T155623Z")
               - floating time ("DTSTART:20251006T155623") */

            if (dtStart.isUtc) {
                // UTC
                values.put(Events.EVENT_TIMEZONE, AndroidTimeUtils.TZID_UTC)

            } else if (!TemporalAdapter.isFloating(dtStart.date)) {
                // timezone reference – make sure that time zone is known by Android
                val tzid = dtStart.getRequiredParameter<TzId>(Parameter.TZID).value
                values.put(Events.EVENT_TIMEZONE, DateUtils.findAndroidTimezoneID(tzid))

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