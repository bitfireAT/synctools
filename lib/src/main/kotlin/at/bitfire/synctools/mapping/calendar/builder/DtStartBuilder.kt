/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.component.VEvent
import java.util.logging.Logger

class DtStartBuilder: AndroidEventFieldBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        /*
        Events.DTSTART is required by Android:
        https://developer.android.com/reference/android/provider/CalendarContract.Events#operations

        So if it's not available in the VEvent, return false to indicate that the resulting Entity is invalid.
        */
        val dtStartDate = from.startDate?.date
        if (dtStartDate == null) {
            logger.warning("Ignoring event without DTSTART")
            return false
        }

        /*
        ical4j uses GMT for the timestamp of all-day events because we have configured it that way
        (net.fortuna.ical4j.timezone.date.floating = false).

        However, this should be verified by a test.
        */

        to.entityValues.put(Events.DTSTART, dtStartDate.time)
        return true
    }

}