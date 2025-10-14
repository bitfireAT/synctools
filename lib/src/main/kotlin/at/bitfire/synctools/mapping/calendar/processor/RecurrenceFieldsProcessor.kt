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
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

class RecurrenceFieldsProcessor: AndroidEventFieldProcessor {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()

    override fun process(from: Entity, main: Entity, to: Event) {
        val values = from.entityValues

        val tsStart = values.getAsLong(Events.DTSTART) ?: throw InvalidLocalResourceException("Found event without DTSTART")
        val allDay = (values.getAsInteger(Events.ALL_DAY) ?: 0) != 0

        // process RRULE field
        val rRules = LinkedList<RRule>()
        values.getAsString(Events.RRULE)?.let { rRuleField ->
            try {
                for (rule in rRuleField.split(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR)) {
                    val rule = RRule(rule)

                    // skip if UNTIL is before event's DTSTART
                    val tsUntil = rule.recur.until?.time
                    if (tsUntil != null && tsUntil <= tsStart) {
                        logger.warning("Ignoring $rule because UNTIL ($tsUntil) is not after DTSTART ($tsStart)")
                        continue
                    }

                    rRules += rule
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse RRULE field, ignoring", e)
            }
        }

        // process RDATE field
        val rDates = LinkedList<RDate>()
        values.getAsString(Events.RDATE)?.let { rDateField ->
            try {
                val rDate = AndroidTimeUtils.androidStringToRecurrenceSet(rDateField, tzRegistry, allDay, tsStart) {
                    RDate(it)
                }
                rDates += rDate
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse RDATE field, ignoring", e)
            }
        }

        // EXRULE
        val exRules = LinkedList<ExRule>()
        values.getAsString(Events.EXRULE)?.let { exRuleField ->
            try {
                for (rule in exRuleField.split(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR)) {
                    val rule = ExRule(null, rule)

                    // skip if UNTIL is before event's DTSTART
                    val tsUntil = rule.recur.until?.time
                    if (tsUntil != null && tsUntil <= tsStart) {
                        logger.warning("Ignoring $rule because UNTIL ($tsUntil) is not after DTSTART ($tsStart)")
                        continue
                    }

                    exRules += rule
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse recurrence rules, ignoring", e)
            }
        }

        // EXDATE
        val exDates = LinkedList<ExDate>()
        values.getAsString(Events.EXDATE)?.let { exDateField ->
            try {
                val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(exDateField, tzRegistry, allDay) { ExDate(it) }
                exDates += exDate
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse recurrence rules, ignoring", e)
            }
        }

        // generate recurrence properties only for recurring main events
        val recurring = rRules.isNotEmpty() || rDates.isNotEmpty()
        if (from === main && recurring) {
            to.rRules += rRules
            to.rDates += rDates
            to.exRules += exRules
            to.exDates += exDates
        }
    }

}