/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Reminders
import android.util.Patterns
import at.bitfire.ical4android.Event
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.Summary
import java.net.URI
import java.time.Duration
import java.util.logging.Level
import java.util.logging.Logger

class RemindersProcessor(
    private val accountName: String
): AndroidEventFieldProcessor {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, to: Event) {
        for (row in from.subValues.filter { it.uri == Reminders.CONTENT_URI })
            populateReminder(row.values, to)
    }

    private fun populateReminder(row: ContentValues, to: Event) {
        logger.log(Level.FINE, "Read event reminder from calendar provider", row)

        val alarm = VAlarm(Duration.ofMinutes(-row.getAsLong(Reminders.MINUTES)))

        val props = alarm.properties
        when (row.getAsInteger(Reminders.METHOD)) {
            Reminders.METHOD_EMAIL -> {
                if (Patterns.EMAIL_ADDRESS.matcher(accountName).matches()) {
                    props += Action.EMAIL
                    // ACTION:EMAIL requires SUMMARY, DESCRIPTION, ATTENDEE
                    props += Summary(to.summary)
                    props += Description(to.description ?: to.summary)
                    // Android doesn't allow to save email reminder recipients, so we always use the
                    // account name (should be account owner's email address)
                    props += Attendee(URI("mailto", accountName, null))
                } else {
                    logger.warning("Account name is not an email address; changing EMAIL reminder to DISPLAY")
                    props += Action.DISPLAY
                    props += Description(to.summary)
                }
            }

            // default: set ACTION:DISPLAY (requires DESCRIPTION)
            else -> {
                props += Action.DISPLAY
                props += Description(to.summary)
            }
        }
        to.alarms += alarm
    }

}