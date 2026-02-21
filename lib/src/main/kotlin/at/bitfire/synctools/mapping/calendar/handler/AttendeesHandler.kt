/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Attendees
import at.bitfire.synctools.mapping.calendar.AttendeeMappings
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.property.Attendee
import java.net.URI
import java.net.URISyntaxException
import java.util.logging.Level
import java.util.logging.Logger

class AttendeesHandler: AndroidEventFieldHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: VEvent) {
        TODO("ical4j 4.x")
    }

    private fun populateAttendee(row: ContentValues, to: VEvent) {
        TODO("ical4j 4.x")
    }

}