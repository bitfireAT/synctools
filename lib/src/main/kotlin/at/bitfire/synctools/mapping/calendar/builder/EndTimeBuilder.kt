/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class EndTimeBuilder: AndroidEntityBuilder {

    override fun build(from: Event, main: Event, to: Entity) {
        val values = to.entityValues

        /* The calendar provider requires
           - DTEND when the event is non-recurring, and
           - DURATION when the event is recurring.

        So we'll skip if this event is a recurring main event (only main events can be recurring). */

        val recurring = from === main && (from.rRules.isNotEmpty() || from.rDates.isNotEmpty())
        if (recurring) {
            values.putNull(Events.DTEND)
            return
        }

        val dtStart = from.requireDtStart()
        val dtEnd = from.dtEnd?.alignWith(dtStart = dtStart)
            ?: calculateDtEndFromDuration(from.dtStart, from.duration)
            ?: calculateDtEndFromDefault(dtStart)

        // end time: UNIX timestamp
        values.put(Events.DTEND, dtEnd.date.time)

        // end time: timezone ID
        if (DateUtils.isDateTime(dtEnd)) {
            /* DTEND is a DATE-TIME. This can be:
               - date/time with timezone ID ("DTEND;TZID=Europe/Vienna:20251006T155623")
               - UTC ("DTEND:20251006T155623Z")
               - floating time ("DTEND:20251006T155623") */

            if (dtEnd.isUtc) {
                // UTC
                values.put(Events.EVENT_END_TIMEZONE, AndroidTimeUtils.TZID_UTC)

            } else if (dtEnd.timeZone != null) {
                // timezone reference – make sure that time zone is known by Android
                values.put(Events.EVENT_END_TIMEZONE, DateUtils.findAndroidTimezoneID(dtEnd.timeZone.id))

            } else {
                // floating time, use system default
                values.put(Events.EVENT_END_TIMEZONE, ZoneId.systemDefault().id)
            }

        } else {
            // DTEND is a DATE
            values.put(Events.EVENT_END_TIMEZONE, AndroidTimeUtils.TZID_UTC)
        }
    }


    /**
     * Aligns `this` DTEND to the VALUE-type (DATE-TIME/DATE) of DTSTART.
     *
     * @param dtStart   DTSTART to compare with
     *
     * @return
     *
     * - DTEND and DTSTART are both either DATE or DATE-TIME → original DTEND
     * - DTEND is DATE, DTSTART is DATE-TIME → DTEND is amended to DATE-TIME with time and time zone from DTSTART
     * - DTEND is DATE-TIME, DTSTART is DATE → DTEND is reduced to its date component
     */
    private fun DtEnd.alignWith(dtStart: DtStart): DtEnd {
        // TODO
        return this
    }

    private fun calculateDtEndFromDuration(dtStart: DtStart?, duration: net.fortuna.ical4j.model.property.Duration?): DtEnd? {
        if (dtStart == null || duration == null)
            return null
        TODO()
    }

    private fun calculateDtEndFromDefault(dtStart: DtStart): DtEnd =
        /* RFC 5545 about empty DTEND values:

        > For cases where a "VEVENT" calendar component
        > specifies a "DTSTART" property with a DATE value type but no
        > "DTEND" nor "DURATION" property, the event's duration is taken to
        > be one day.

        > For cases where a "VEVENT" calendar component
        > specifies a "DTSTART" property with a DATE-TIME value type but no
        > "DTEND" property, the event ends on the same calendar date and
        > time of day specified by the "DTSTART" property.
        */
        if (DateUtils.isDate(dtStart)) {
            // DATE → one day duration
            val endDate: LocalDate = dtStart.date.toLocalDate() + Duration.ofDays(1)
            DtEnd(endDate.toIcal4jDate())
        } else {
            // DATE-TIME → same as DTSTART
            DtEnd(dtStart.value, dtStart.timeZone)
        }

}