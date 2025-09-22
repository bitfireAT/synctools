/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Attendees
import at.bitfire.ical4android.Event
import at.bitfire.synctools.mapping.calendar.AttendeeMappings
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.property.Attendee
import java.net.URI
import java.net.URISyntaxException
import java.util.logging.Level
import java.util.logging.Logger

class AttendeesProcessor: AndroidEventFieldProcessor {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: Event) {
        for (row in from.subValues.filter { it.uri == Attendees.CONTENT_URI })
            populateAttendee(row.values, to)
    }

    private fun populateAttendee(row: ContentValues, to: Event) {
        logger.log(Level.FINE, "Read event attendee from calendar provider", row)

        try {
            val attendee: Attendee
            val email = row.getAsString(Attendees.ATTENDEE_EMAIL)
            val idNS = row.getAsString(Attendees.ATTENDEE_ID_NAMESPACE)
            val id = row.getAsString(Attendees.ATTENDEE_IDENTITY)

            if (idNS != null || id != null) {
                // attendee identified by namespace and ID
                attendee = Attendee(URI(idNS, id, null))
                email?.let { attendee.parameters.add(Email(it)) }
            } else
                // attendee identified by email address
                attendee = Attendee(URI("mailto", email, null))
            val params = attendee.parameters

            // always add RSVP (offer attendees to accept/decline)
            params.add(Rsvp.TRUE)

            row.getAsString(Attendees.ATTENDEE_NAME)?.let { cn -> params.add(Cn(cn)) }

            // type/relation mapping is complex and thus outsourced to AttendeeMappings
            AttendeeMappings.androidToICalendar(row, attendee)

            // status
            when (row.getAsInteger(Attendees.ATTENDEE_STATUS)) {
                Attendees.ATTENDEE_STATUS_INVITED -> params.add(PartStat.NEEDS_ACTION)
                Attendees.ATTENDEE_STATUS_ACCEPTED -> params.add(PartStat.ACCEPTED)
                Attendees.ATTENDEE_STATUS_DECLINED -> params.add(PartStat.DECLINED)
                Attendees.ATTENDEE_STATUS_TENTATIVE -> params.add(PartStat.TENTATIVE)
                Attendees.ATTENDEE_STATUS_NONE -> { /* no information, don't add PARTSTAT */ }
            }

            to.attendees.add(attendee)
        } catch (e: URISyntaxException) {
            logger.log(Level.WARNING, "Couldn't parse attendee information, ignoring", e)
        }
    }

}