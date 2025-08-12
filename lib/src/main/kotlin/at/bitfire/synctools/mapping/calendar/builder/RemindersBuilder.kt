/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Reminders
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.DurationCalculator
import at.bitfire.synctools.icalendar.asLocalDate
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Trigger
import java.time.Duration
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.time.temporal.TemporalAmount
import java.util.Locale

class RemindersBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        for (alarm in from.alarms)
            to.addSubValue(
                Reminders.CONTENT_URI,
                buildReminder(alarm, from)
            )
        return true
    }

    private fun buildReminder(alarm: VAlarm, event: VEvent): ContentValues {
        val method = when (alarm.action?.value?.uppercase(Locale.ROOT)) {
            Action.DISPLAY.value,
            Action.AUDIO.value -> Reminders.METHOD_ALERT    // will trigger an alarm on the Android device

            // Note: The calendar provider doesn't support saving specific attendees for email reminders.
            Action.EMAIL.value -> Reminders.METHOD_EMAIL

            else -> Reminders.METHOD_DEFAULT                // won't trigger an alarm on the Android device
        }

        val minutes = alarm.trigger?.let {
            triggerToMinutes(it, event)
        } ?: Reminders.MINUTES_DEFAULT

        return contentValuesOf(
            Reminders.METHOD to method,
            Reminders.MINUTES to minutes
        )
    }

    private fun triggerToMinutes(trigger: Trigger, event: VEvent): Int? {
        // TRIGGER can reference absolute or relative time
        val triggerDate = trigger.date
        val triggerDuration = trigger.duration
        val dtStartDate = event.startDate?.date
        return when {
            triggerDate is DateTime && dtStartDate != null ->
                absTriggerToMinutes(
                    at = triggerDate,
                    eventStart = dtStartDate
                )

            triggerDuration != null ->
                relTriggerToMinutes(
                    dur = triggerDuration,
                    related = trigger.getParameter(Parameter.RELATED) ?: Related.START,
                    event = event
                )

            else ->
                null
        }
    }

    private fun absTriggerToMinutes(at: DateTime, eventStart: Date): Int {
        val refStart: Temporal = if (eventStart is DateTime)
            eventStart.toInstant()
        else {
            /**
             * RFC 5545, 3.8.6.3 Trigger:
             *
             * Alarms specified in an event or to-do that is defined in terms of
             * a DATE value type will be triggered relative to 00:00:00 of the
             * user's configured time zone on the specified date, or relative to
             * 00:00:00 UTC on the specified date if no configured time zone can
             * be found for the user.  For example, if "DTSTART" is a DATE value
             * set to 19980205 then the duration trigger will be relative to
             * 19980205T000000 America/New_York for a user configured with the
             * America/New_York time zone.
             */
            ZonedDateTime.of(
                eventStart.asLocalDate(),
                LocalTime.of(0, 0, 0),
                ZoneId.systemDefault()
            )
        }

        return Duration.between(
            /* startInclusive = */ at.toInstant(),
            /* endExclusive = */ refStart
        ).toMinutes().toInt()
    }

    private fun relTriggerToMinutes(dur: TemporalAmount, related: Related, event: VEvent): Int? {
        val dtStartDate = event.startDate?.date
        val eventDuration = event.duration?.duration
        val dtEndDate = event.endDate?.date
            ?: if (dtStartDate != null && eventDuration != null)
                DurationCalculator.calculateEndDate(dtStartDate, eventDuration)
            else
                null

        if (dur is Duration) {
            // return exact number of minutes, if available
            if (related == Related.START)
                return -dur.toMinutes().toInt()

            else if (dtStartDate != null && dtEndDate != null) {
                // calculate duration from DTSTART
                val durFromStart = dur - Duration.between(dtEndDate.toInstant(), dtStartDate.toInstant())
                return -durFromStart.toMinutes().toInt()
            }

        } else if (dur is Period && dtStartDate != null) {
            // We have a number of days, but it doesn't map to an exact number of minutes without further information
            // (there may be a DST change on that day).

            // Also, the trigger may also be related to the end, but we always want the number of minutes before the start time.
            val ref =
                if (related == Related.START)
                    event.startDate?.date
                else
                    event.endDate?.date

            if (ref == null)
                return null

            val at = ref.toInstant() + dur
            return Duration.between(
                /* startInclusive = */ at,
                /* endExclusive = */ dtStartDate.toInstant()
            ).toMinutes().toInt()
        }

        return null
    }

}