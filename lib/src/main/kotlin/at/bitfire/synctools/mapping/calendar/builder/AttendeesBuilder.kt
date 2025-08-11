/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Attendee

class AttendeesBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        val attendees = from.getProperties<Attendee>(Property.ATTENDEE)

        // set HAS_ATTENDEE_DATA
        to.entityValues.put(Events.HAS_ATTENDEE_DATA, if (attendees.isNotEmpty()) 1 else 0)

        for (attendee in attendees) {
            val attendeeValues = buildAttendee(attendee)
            to.addSubValue(Attendees.CONTENT_URI, attendeeValues)
        }

        return true
    }

    private fun buildAttendee(attendee: Attendee): ContentValues {
        val values = ContentValues()

        // TODO

        return values
    }

}