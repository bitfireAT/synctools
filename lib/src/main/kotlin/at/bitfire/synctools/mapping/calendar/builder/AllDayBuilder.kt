/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.DateUtils

class AllDayBuilder: AndroidEntityBuilder {

    override fun build(from: Event, main: Event, to: Entity) {
        val allDay = DateUtils.isDate(from.dtStart)
        to.entityValues.put(Events.ALL_DAY, if (allDay) 1 else 0)
    }

}