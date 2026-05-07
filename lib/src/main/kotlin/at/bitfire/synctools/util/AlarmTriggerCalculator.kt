/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.util

import at.bitfire.synctools.util.AndroidTimeUtils.toInstant
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Trigger
import java.time.Duration
import java.time.Period
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull
import net.fortuna.ical4j.model.property.Duration as ICalDuration

object AlarmTriggerCalculator {
    private val logger = Logger.getLogger(javaClass.name)

    /**
     * Calculates the minutes before/after an event/task to know when a given alarm occurs.
     *
     * Note: Android's alarm granularity is minutes. This method calculates with milliseconds, but
     * the result is rounded down to minutes (seconds cut off).
     *
     * @param alarm         the alarm to calculate the minutes from
     * @param refStart      reference `DTSTART` from the calendar component
     * @param refEnd        reference `DTEND` (`VEVENT`) or `DUE` (`VTODO`) from the calendar component
     * @param allowRelEnd   *true*: caller accepts minutes related to the end;
     * *false*: caller only accepts minutes related to the start
     *
     * @return Pair of values:
     *
     * 1. whether the minutes are related to the start or end (always [Related.START] if [allowRelEnd] is *false*)
     * 2. number of minutes before start/end (negative value means number of minutes *after* start/end)
     *
     * May be *null* if there's not enough information to calculate the number of minutes.
     */
    fun vAlarmToMin(
        alarm: VAlarm,
        refStart: DtStart<*>?,
        refEnd: DateProperty<*>?,
        refDuration: ICalDuration?,
        allowRelEnd: Boolean
    ): Pair<Related, Int>? {
        val trigger = alarm.getProperty<Trigger>(Property.TRIGGER).getOrNull() ?: return null

        // Note: big method – maybe split?

        val minutes: Int    // minutes before/after the event
        var related: Related = trigger.getParameter<Related>(Parameter.RELATED).getOrNull() ?: Related.START

        // event/task start/end time
        val start = refStart?.date?.toInstant()
        var end = refEnd?.date?.toInstant()

        // event/task end time
        if (end == null && start != null)
            end = when (val refDur = refDuration?.duration) {
                is Duration -> start + refDur
                is Period -> start + Duration.between(start, start + refDur)
                else -> null
            }

        // event/task duration
        val duration: Duration? =
            if (start != null && end != null)
                Duration.between(start, end)
            else
                null

        val triggerDur = trigger.duration
        val triggerTime = trigger.date

        if (triggerDur != null) {
            // TRIGGER value is a DURATION. Important:
            // 1) Negative values in TRIGGER mean positive values in Reminders.MINUTES and vice versa.
            // 2) Android doesn't know alarm seconds, but only minutes. Cut off seconds from the final result.
            // 3) DURATION can be a Duration (time-based) or a Period (date-based), which have to be treated differently.
            var millisBefore =
                when (triggerDur) {
                    is Duration -> -triggerDur.toMillis()
                    is Period -> {
                        // TODO: Take time zones into account (will probably be possible with ical4j 4.x).
                        // For instance, an alarm one day before the DST change should be 23/25 hours before the event.
                        -Duration.ofDays(triggerDur.days.toLong()).toMillis()     // months and years are not used in DURATION values; weeks are calculated to days
                    }
                    else -> throw AssertionError("triggerDur must be Duration or Period")
                }

            if (related == Related.END && !allowRelEnd) {
                if (duration == null) {
                    logger.warning("Event/task without duration; can't calculate END-related alarm")
                    return null
                }
                // move alarm towards end
                related = Related.START
                millisBefore -= duration.toMillis()
            }
            minutes = (millisBefore / 60000).toInt()

        } else if (triggerTime != null && start != null) {
            // TRIGGER value is a DATE-TIME, calculate minutes from start time
            related = Related.START
            minutes = Duration.between(triggerTime, start).toMinutes().toInt()

        } else {
            logger.log(Level.WARNING, "VALARM TRIGGER type is not DURATION or DATE-TIME (requires event DTSTART for Android), ignoring alarm", alarm)
            return null
        }

        return Pair(related, minutes)
    }
}