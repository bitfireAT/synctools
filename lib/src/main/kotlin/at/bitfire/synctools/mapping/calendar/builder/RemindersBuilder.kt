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
import at.bitfire.ical4android.ICalendar
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Action
import java.util.Locale

class RemindersBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        for (reminder in from.alarms)
            to.addSubValue(Reminders.CONTENT_URI, buildReminder(reminder, from))
    }

    private fun buildReminder(alarm: VAlarm, event: VEvent): ContentValues {
        val method = when (alarm.action?.value?.uppercase(Locale.ROOT)) {
            Action.DISPLAY.value,
            Action.AUDIO.value -> Reminders.METHOD_ALERT    // will trigger an alarm on the Android device

            // Note: The calendar provider doesn't support saving specific attendees for email reminders.
            Action.EMAIL.value -> Reminders.METHOD_EMAIL

            else -> Reminders.METHOD_DEFAULT                // won't trigger an alarm on the Android device
        }

        val minutes = ICalendar.vAlarmToMin(
            alarm = alarm,
            refStart = event.startDate,
            refEnd = event.endDate,
            refDuration = event.duration,
            allowRelEnd = false
        )?.second ?: Reminders.MINUTES_DEFAULT

        return contentValuesOf(
            Reminders.METHOD to method,
            Reminders.MINUTES to minutes
        )
    }

}