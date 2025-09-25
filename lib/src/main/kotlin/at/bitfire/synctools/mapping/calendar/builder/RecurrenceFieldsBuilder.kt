/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.AndroidTimeUtils
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.property.RDate
import java.util.logging.Logger

class RecurrenceFieldsBuilder: AndroidEntityBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: Event, main: Event, to: Entity) {
        val values = to.entityValues

        val recurring = from.rRules.isNotEmpty() || from.rDates.isNotEmpty()
        if (recurring && from === main) {
            // generate recurrence fields only for recurring main events
            val dtStart = from.dtStart

            // RRULE
            if (from.rRules.isNotEmpty())
                values.put(Events.RRULE, from.rRules.joinToString(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR) { it.value })
            else
                values.putNull(Events.RRULE)

            // RDATE (start with null value)
            values.putNull(Events.RDATE)
            if (from.rDates.isNotEmpty() && dtStart != null) {
                // ignore RDATEs when there's also an infinite RRULE [https://issuetracker.google.com/issues/216374004]
                val infiniteRrule = from.rRules.any { rRule ->
                    rRule.recur.count == -1 &&  // no COUNT AND
                    rRule.recur.until == null   // no UNTIL
                }
                if (infiniteRrule)
                    logger.warning("Android can't handle infinite RRULE + RDATE [https://issuetracker.google.com/issues/216374004]; ignoring RDATE(s)")
                else {
                    for (rDate in from.rDates)
                        AndroidTimeUtils.androidifyTimeZone(rDate)

                    // Calendar provider drops DTSTART instance when using RDATE [https://code.google.com/p/android/issues/detail?id=171292]
                    val listWithDtStart = DateList()
                    listWithDtStart.add(dtStart.date)
                    from.rDates.addFirst(RDate(listWithDtStart))

                    values.put(Events.RDATE, AndroidTimeUtils.recurrenceSetsToAndroidString(from.rDates, dtStart.date))
                }
            }

            // EXRULE
            if (from.exRules.isNotEmpty())
                values.put(Events.EXRULE, from.exRules.joinToString(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR) { it.value })
            else
                values.putNull(Events.EXRULE)

            // EXDATE
            if (from.exDates.isNotEmpty() && dtStart != null) {
                for (exDate in from.exDates)
                    AndroidTimeUtils.androidifyTimeZone(exDate)
                values.put(Events.EXDATE, AndroidTimeUtils.recurrenceSetsToAndroidString(from.exDates, dtStart.date))
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