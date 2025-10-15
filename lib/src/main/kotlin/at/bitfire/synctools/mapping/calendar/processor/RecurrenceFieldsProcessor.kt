/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import at.bitfire.synctools.exception.InvalidLocalResourceException
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import java.time.ZonedDateTime
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

class RecurrenceFieldsProcessor(
    private val tzRegistry: TimeZoneRegistry
): AndroidEventFieldProcessor {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: Event) {
        val values = from.entityValues

        val tsStart = values.getAsLong(Events.DTSTART) ?: throw InvalidLocalResourceException("Found event without DTSTART")
        val allDay = (values.getAsInteger(Events.ALL_DAY) ?: 0) != 0

        // provide start date as ical4j Date, if needed
        val startDate by lazy {
            AndroidTimeField(
                timestamp = tsStart,
                timeZone = values.getAsString(Events.EVENT_TIMEZONE),
                allDay = allDay,
                tzRegistry = tzRegistry
            ).asIcal4jDate()
        }

        // process RRULE field
        val rRules = LinkedList<RRule>()
        values.getAsString(Events.RRULE)?.let { rRuleField ->
            try {
                for (rule in rRuleField.split(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR)) {
                    val rule = RRule(rule)

                    // align RRULE UNTIL to DTSTART, if needed
                    rule.recur = alignUntil(rule.recur, startDate)

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

                    // align RRULE UNTIL to DTSTART, if needed
                    rule.recur = alignUntil(rule.recur, startDate)

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

    /**
     * Aligns the `UNTIL` of the given recurrence info to the VALUE-type (DATE-TIME/DATE) of [startDate].
     *
     * If the aligned `UNTIL` is a DATE-TIME, this method also makes sure that it's specified in UTC format
     * as required by RFC 5545 3.3.10.
     *
     * @param recur         recurrence info whose `UNTIL` shall be aligned
     * @param startDate     `DTSTART` date to compare with
     *
     * @return
     *
     * - UNTIL not set → original recur
     * - UNTIL and DTSTART are both either DATE or DATE-TIME → original recur
     * - UNTIL is DATE, DTSTART is DATE-TIME → UNTIL is amended to DATE-TIME with time and timezone from DTSTART
     * - UNTIL is DATE-TIME, DTSTART is DATE → UNTIL is reduced to its date component
     *
     * @see at.bitfire.synctools.mapping.calendar.builder.EndTimeBuilder.alignWithDtStart
     */
    fun alignUntil(recur: Recur, startDate: Date): Recur {
        val until: Date? = recur.until
        if (until == null)
            return recur

        if (until is DateTime) {
            // UNTIL is DATE-TIME
            if (startDate is DateTime) {
                // DTSTART is DATE-TIME
                return if (until.isUtc)
                    recur
                else
                    Recur.Builder(recur)
                        .until(DateTime(until).apply {
                            isUtc = true
                        })
                        .build()
            } else {
                // DTSTART is DATE → only take date part
                val untilDate = until.toLocalDate()
                return Recur.Builder(recur)
                    .until(untilDate.toIcal4jDate())
                    .build()
            }
        } else {
            // UNTIL is DATE
            if (startDate is DateTime) {
                // DTSTART is DATE-TIME
                val untilDate = until.toLocalDate()
                val startTime = startDate.toZonedDateTime()
                val untilDateWithTime = ZonedDateTime.of(untilDate, startTime.toLocalTime(), startTime.zone)
                return Recur.Builder(recur)
                    .until(untilDateWithTime.toIcal4jDateTime(tzRegistry).apply {
                        isUtc = true
                    })
                    .build()
            } else {
                // DTSTART is DATE
                return recur
            }
        }
    }

}