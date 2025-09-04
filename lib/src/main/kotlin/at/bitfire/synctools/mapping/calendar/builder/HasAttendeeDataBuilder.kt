/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.isGroupScheduled
import net.fortuna.ical4j.model.component.VEvent

/**
 * The [Events.HAS_ATTENDEE_DATA] flags seems to be used by calendar apps to show the
 * UI components for group-scheduling (attendees, organizer, ...).
 */
class HasAttendeeDataBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        val groupScheduled = from.isGroupScheduled()
        to.entityValues.put(Events.HAS_ATTENDEE_DATA, if (groupScheduled) 1 else 0)
        return true
    }

}