/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import net.fortuna.ical4j.model.property.Organizer
import java.net.URI
import java.net.URISyntaxException
import java.util.logging.Level
import java.util.logging.Logger

class OrganizerProcessor: AndroidEventFieldProcessor {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: Event) {
        // In case of an exception, we're taking ORGANIZER information from the main event and not the exception. See also RFC 6638 3.1.
        val values = main.entityValues

        // IS_ORGANIZER helper in Event class (deprecated)
        to.isOrganizer = values.getAsBoolean(Events.IS_ORGANIZER)

        // ORGANIZER must only be set for group-scheduled events (= events with attendees)
        val hasAttendees = from.subValues.any { it.uri == Attendees.CONTENT_URI }
        if (hasAttendees && values.containsKey(Events.ORGANIZER))
            try {
                to.organizer = Organizer(URI("mailto", values.getAsString(Events.ORGANIZER), null))
            } catch (e: URISyntaxException) {
                logger.log(Level.WARNING, "Error when creating ORGANIZER mailto URI, ignoring", e)
            }
    }

}