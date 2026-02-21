/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Attendees
import androidx.annotation.VisibleForTesting
import at.bitfire.synctools.mapping.calendar.AttendeeMappings
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.property.Attendee

class AttendeesBuilder(
    private val calendar: AndroidCalendar
): AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        TODO("ical4j 4.x")
    }

    private fun buildAttendee(attendee: Attendee, event: VEvent): ContentValues {
        TODO("ical4j 4.x")
    }

    @VisibleForTesting
    internal fun organizerEmail(event: VEvent): String? {
        TODO("ical4j 4.x")
    }

}