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
import at.bitfire.ical4android.util.TimeApiExtensions.abs
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import at.bitfire.synctools.icalendar.requireDtStart
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAmount

class EndTimeBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        TODO("ical4j 4.x")
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
    internal fun <T : java.time.temporal.Temporal> alignWithDtStart(dtEnd: DtEnd<T>, dtStart: DtStart<T>): DtEnd<T> {
        TODO("ical4j 4.x")
    }

    /**
     * Calculates the DTEND from DTSTART + DURATION, if possible.
     *
     * @param dtStart   start date/date-time
     * @param duration  (optional) duration
     *
     * @return end date/date-time (same value type as [dtStart]) or `null` if [duration] was not given
     */
    @VisibleForTesting
    internal fun calculateFromDuration(dtStart: DtStart<*>, duration: TemporalAmount?): DtEnd<*>? {
        TODO("ical4j 4.x")
    }

    /**
     * Chooses a DTEND value for the content provider when the iCalendar doesn't have a DTEND.
     *
     * RFC 5545 says the following about empty DTEND values:
     *
     * > For cases where a "VEVENT" calendar component specifies a "DTSTART" property with a DATE value type but no
     * > "DTEND" nor "DURATION" property, the event's duration is taken to be one day. For cases where a "VEVENT" calendar
     * > component specifies a "DTSTART" property with a DATE-TIME value type but no "DTEND" property, the event
     * > ends on the same calendar date and time of day specified by the "DTSTART" property.
     *
     * In iCalendar, `DTEND` is non-inclusive at must be at a later time than `DTEND`. However in Android we can use
     * the same value for both the `DTSTART` and the `DTEND` field, and so we use this to indicate a missing DTEND in
     * the original iCalendar.
     *
     * @param dtStart   start time to calculate end time from
     * @return End time to use for content provider:
     *
     * - when [dtStart] is a `DATE`: [dtStart] + 1 day
     * - when [dtStart] is a `DATE-TIME`: [dtStart]
     */
    @VisibleForTesting
    internal fun <T : java.time.temporal.Temporal> calculateFromDefault(dtStart: DtStart<T>): DtEnd<T> {
        TODO("ical4j 4.x")
    }

}