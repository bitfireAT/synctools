/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.annotation.VisibleForTesting
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import at.bitfire.synctools.icalendar.requireDtStart
import at.bitfire.synctools.mapping.calendar.MappingUtil
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import java.time.ZoneId
import java.time.ZonedDateTime

class EndTimeBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues

        /* The calendar provider requires
           - DTEND when the event is non-recurring, and
           - DURATION when the event is recurring.

        So we'll skip if this event is a recurring main event (only main events can be recurring). */
        val rRules = from.getProperties<RRule>(Property.RRULE)
        val rDates = from.getProperties<RDate>(Property.RDATE)
        if (from === main && (rRules.isNotEmpty() || rDates.isNotEmpty())) {
            values.putNull(Events.DTEND)
            return
        }

        val dtStart = from.requireDtStart()

        // potentially calculate DTEND from DTSTART + DURATION, and always align with DTSTART value type
        val calculatedDtEnd = from.getEndDate(/* don't let ical4j calculate DTEND from DURATION */ false)
            ?.let { alignWithDtStart(it, dtStart = dtStart) }
            ?: MappingUtil.dtEndFromDuration(dtStart, from.duration?.duration)

        // ignore DTEND when not after DTSTART and use default duration, if necessary
        val dtEnd = calculatedDtEnd
            ?.takeIf { it.date.toInstant() > dtStart.date.toInstant() }     // only use DTEND if it's after DTSTART [1]
            ?: MappingUtil.dtEndFromDefault(dtStart)

        /**
         * [1] RFC 5545 3.8.2.2 Date-Time End:
         * […] its value MUST be later in time than the value of the "DTSTART" property.
         */

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
     * Aligns the given DTEND to the VALUE-type (DATE-TIME/DATE) of DTSTART.
     *
     * @param dtEnd     DTEND to be aligned
     * @param dtStart   DTSTART to compare with
     *
     * @return
     *
     * - DTEND and DTSTART are both either DATE or DATE-TIME → original DTEND
     * - DTEND is DATE, DTSTART is DATE-TIME → DTEND is amended to DATE-TIME with time and timezone from DTSTART
     * - DTEND is DATE-TIME, DTSTART is DATE → DTEND is reduced to its date component
     *
     * @see at.bitfire.synctools.mapping.calendar.handler.RecurrenceFieldsHandler.alignUntil
     */
    @VisibleForTesting
    internal fun alignWithDtStart(dtEnd: DtEnd, dtStart: DtStart): DtEnd {
        if (DateUtils.isDate(dtEnd)) {
            // DTEND is DATE
            if (DateUtils.isDate(dtStart)) {
                // DTEND is DATE, DTSTART is DATE
                return dtEnd
            } else {
                // DTEND is DATE, DTSTART is DATE-TIME → amend with time and timezone
                val endDate = dtEnd.date.toLocalDate()
                val startTime = (dtStart.date as DateTime).toZonedDateTime()
                val endDateWithTime = ZonedDateTime.of(endDate, startTime.toLocalTime(), startTime.zone)
                return DtEnd(endDateWithTime.toIcal4jDateTime())
            }
        } else {
            // DTEND is DATE-TIME
            if (DateUtils.isDate(dtStart)) {
                // DTEND is DATE-TIME, DTSTART is DATE → only take date part
                val endDate = dtEnd.date.toLocalDate()
                return DtEnd(endDate.toIcal4jDate())
            } else {
                // DTEND is DATE-TIME, DTSTART is DATE-TIME
                return dtEnd
            }
        }
    }

}