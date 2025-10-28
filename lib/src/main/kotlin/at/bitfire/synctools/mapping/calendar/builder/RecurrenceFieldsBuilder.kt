/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import java.util.logging.Logger

class RecurrenceFieldsBuilder: AndroidEntityBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues

        val rRules = from.getProperties<RRule>(Property.RRULE)
        val rDates = from.getProperties<RDate>(Property.RDATE)
        val recurring = rRules.isNotEmpty() || rDates.isNotEmpty()
        if (recurring && from === main) {
            // generate recurrence fields only for recurring main events
            val dtStart = from.requireDtStart()

            // RRULE
            if (rRules.isNotEmpty())
                values.put(Events.RRULE, rRules.joinToString(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR) { it.value })
            else
                values.putNull(Events.RRULE)

            // RDATE (start with null value)
            values.putNull(Events.RDATE)
            if (rDates.isNotEmpty()) {
                // ignore RDATEs when there's also an infinite RRULE [https://issuetracker.google.com/issues/216374004]
                val infiniteRrule = rRules.any { rRule ->
                    rRule.recur.count == -1 &&  // no COUNT AND
                    rRule.recur.until == null   // no UNTIL
                }
                if (infiniteRrule)
                    logger.warning("Android can't handle infinite RRULE + RDATE [https://issuetracker.google.com/issues/216374004]; ignoring RDATE(s)")
                else {
                    for (rDate in rDates)
                        AndroidTimeUtils.androidifyTimeZone(rDate)

                    // Calendar provider drops DTSTART instance when using RDATE [https://code.google.com/p/android/issues/detail?id=171292]
                    val listWithDtStart = DateList()
                    listWithDtStart.add(dtStart.date)
                    rDates.add(0, RDate(listWithDtStart))

                    values.put(Events.RDATE, AndroidTimeUtils.recurrenceSetsToAndroidString(rDates, dtStart.date))
                }
            }

            // EXRULE
            val exRules = from.getProperties<ExRule>(Property.EXRULE)
            if (exRules.isNotEmpty())
                values.put(Events.EXRULE, exRules.joinToString(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR) { it.value })
            else
                values.putNull(Events.EXRULE)

            // EXDATE
            val exDates = from.getProperties<ExDate>(Property.EXDATE)
            if (exDates.isNotEmpty()) {
                for (exDate in exDates)
                    AndroidTimeUtils.androidifyTimeZone(exDate)
                values.put(Events.EXDATE, AndroidTimeUtils.recurrenceSetsToAndroidString(exDates, dtStart.date))
            } else
                values.putNull(Events.EXDATE)

        } else {
            values.putNull(Events.RRULE)
            values.putNull(Events.EXRULE)
            values.putNull(Events.RDATE)
            values.putNull(Events.EXDATE)
        }
    }

}