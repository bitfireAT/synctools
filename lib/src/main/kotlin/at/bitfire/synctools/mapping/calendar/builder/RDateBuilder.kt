/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.vcard4android.Utils.trimToNull
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import java.util.logging.Logger

class RDateBuilder: AndroidEventFieldBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        // only build for main events
        if (from !== main) {
            to.entityValues.putNull(Events.RDATE)
            return true
        }

        // ignore RDATEs when there's also an infinite RRULE [https://issuetracker.google.com/issues/216374004]
        val rRules = from.getProperties<RRule>(Property.RRULE)
        val infiniteRrule = rRules.any {
            it.recur.count == -1 &&  // no COUNT AND
            it.recur.until == null   // no UNTIL
        }

        if (infiniteRrule) {
            logger.warning("Android can't handle infinite RRULE + RDATE; ignoring RDATE(s)")
            to.entityValues.putNull(Events.RDATE)
            return true
        }

        val rDates = from.getProperties<RDate>(Property.RDATE)

        // make sure that RDATE time zones are available on the system
        for (rDate in rDates)
            AndroidTimeUtils.androidifyTimeZone(rDate)

        // Calendar provider drops DTSTART instance when using RDATE [https://code.google.com/p/android/issues/detail?id=171292],
        // so we have to add the DTSTART as an entry to the RDATEs.
        val dtStartDate = from.startDate?.date
        if (dtStartDate != null) {
            val listWithDtStart = DateList().apply {
                add(dtStartDate)
            }
            rDates.add(0, RDate(listWithDtStart))
        }

        val androidRDates = AndroidTimeUtils.recurrenceSetsToAndroidString(rDates, dtStartDate)

        to.entityValues.put(
            Events.RDATE,
            androidRDates.trimToNull()      // use null if there are no lines
        )
        return true
    }

}