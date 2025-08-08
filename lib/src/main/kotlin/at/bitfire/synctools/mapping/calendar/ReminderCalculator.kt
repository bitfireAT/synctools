/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Related
import java.time.Duration
import java.time.Period
import java.util.logging.Level
import java.util.logging.Logger

class ReminderCalculator {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Calculates the minutes before/after an event/task a given alarm occurs.
     *
     * @param alarm the alarm to calculate the minutes from
     * @param reference reference [VEvent] or [VToDo] to take start/end time from (required for calculations)
     * @param allowRelEnd *true*: caller accepts minutes related to the end;
     * *false*: caller only accepts minutes related to the start
     *
     * Android's alarm granularity is minutes. This methods calculates with milliseconds, but the result
     * is rounded down to minutes (seconds cut off).
     *
     * @return Pair of values:
     *
     * 1. whether the minutes are related to the start or end (always [Related.START] if [allowRelEnd] is *false*)
     * 2. number of minutes before start/end (negative value means number of minutes *after* start/end)
     *
     * May be *null* if there's not enough information to calculate the number of minutes.
     */
    fun vAlarmToMin(alarm: VAlarm, reference: CalendarComponent, allowRelEnd: Boolean): Pair<Related, Int>? {
        val trigger = alarm.trigger ?: return null

        val minutes: Int    // minutes before/after the event
        var related = trigger.getParameter<Related>(Parameter.RELATED) ?: Related.START

        // event/task start and end time
        val start: java.util.Date?
        var end: java.util.Date?
        when (reference) {
            is VEvent -> {
                start = reference.startDate?.date
                end = reference.endDate?.date
            }
            is VToDo -> {
                start = reference.startDate?.date
                end = reference.due?.date
            }
            else -> throw IllegalArgumentException("reference must be VEvent or VTodo")
        }

        // calculate event/task end time from duration, if required
        if (end == null && start != null) {
            val duration = when (reference) {
                is VEvent -> reference.duration?.duration
                is VToDo -> reference.duration?.duration
                else -> throw IllegalArgumentException("reference must be Event or Task")
            }
            if (duration != null)
                end = java.util.Date.from(start.toInstant() + duration)
        }

        // process trigger
        val triggerDur = trigger.duration
        val triggerTime = trigger.dateTime

        if (triggerDur != null) {
            // TRIGGER value is a DURATION. Important:
            // 1) Negative values in TRIGGER mean positive values in Reminders.MINUTES and vice versa.
            // 2) Android doesn't know alarm seconds, but only minutes. Cut off seconds from the final result.
            // 3) DURATION can be a Duration (time-based) or a Period (date-based), which have to be treated differently.
            var millisBefore =
                when (triggerDur) {
                    is Duration ->
                        -triggerDur.toMillis()
                    is Period ->
                        // TODO: Take time zones into account (will probably be possible with ical4j 4.x).
                        // For instance, an alarm one day before the DST change should be 23/25 hours before the event.
                        -triggerDur.days.toLong()*24*3600000     // months and years are not used in DURATION values; weeks are calculated to days
                    else ->
                        throw AssertionError("triggerDur must be Duration or Period")
                }

            if (related == Related.END && !allowRelEnd) {
                // event/task duration for calculation
                val duration: Duration? =
                    if (start != null && end != null)
                        Duration.between(start.toInstant(), end.toInstant())
                    else
                        null

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
            minutes = Duration.between(triggerTime.toInstant(), start.toInstant()).toMinutes().toInt()

        } else {
            logger.log(Level.WARNING, "VALARM TRIGGER type is not DURATION or DATE-TIME (requires event DTSTART for Android), ignoring alarm", alarm)
            return null
        }

        return Pair(related, minutes)
    }

}