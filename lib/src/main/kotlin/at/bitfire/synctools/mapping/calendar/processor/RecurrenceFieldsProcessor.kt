/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.synctools.exception.InvalidLocalResourceException
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import java.util.logging.Level
import java.util.logging.Logger

class RecurrenceFieldsProcessor: AndroidEventFieldProcessor {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()

    override fun process(from: Entity, main: Entity, to: Event) {
        val values = from.entityValues
        val rRuleField = values.getAsString(Events.RRULE)
        val rDateField = values.getAsString(Events.RDATE)

        // generate recurrence properties only for recurring main events
        val recurring = rRuleField != null || rDateField != null
        if (from !== main && !recurring)
            return

        val allDay = (values.getAsInteger(Events.ALL_DAY) ?: 0) != 0
        val tsStart = values.getAsLong(Events.DTSTART) ?: throw InvalidLocalResourceException("Found event without DTSTART")

        // RRULE
        if (rRuleField != null)
            try {
                for (rule in rRuleField.split(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR))
                    to.rRules += RRule(rule)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse RRULE field, ignoring", e)
            }

        // RDATE
        if (rDateField != null)
            try {
                val rDate = AndroidTimeUtils.androidStringToRecurrenceSet(rDateField, tzRegistry, allDay, tsStart) { RDate(it) }
                to.rDates += rDate
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse RDATE field, ignoring", e)
            }

        // EXRULE
        values.getAsString(Events.EXRULE)?.let { rulesStr ->
            try {
                for (rule in rulesStr.split(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR))
                    to.exRules += ExRule(null, rule)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse recurrence rules, ignoring", e)
            }
        }

        // EXDATE
        values.getAsString(Events.EXDATE)?.let { datesStr ->
            try {
                val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(datesStr, tzRegistry, allDay) { ExDate(it) }
                to.exDates += exDate
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse recurrence rules, ignoring", e)
            }
        }
    }

}