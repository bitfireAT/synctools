/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.property.Attendee
import java.util.logging.Logger

class OrganizerBuilder(
    private val ownerAccount: String?
): AndroidEntityBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues
        val groupScheduled = from.getProperties<Attendee>(Property.ATTENDEE).isNotEmpty()
        if (groupScheduled) {
            values.put(Events.HAS_ATTENDEE_DATA, 1)
            values.put(Events.ORGANIZER, from.organizer?.let { organizer ->
                val uri = organizer.calAddress
                val email = if (uri.scheme.equals("mailto", true))
                    uri.schemeSpecificPart
                else
                    organizer.getParameter<Email>(Parameter.EMAIL)?.value

                if (email != null)
                    return@let email

                logger.warning("Ignoring ORGANIZER without email address (not supported by Android)")
                null
            } ?: ownerAccount)

        } else { /* !groupScheduled */
            values.put(Events.HAS_ATTENDEE_DATA, 0)
            values.put(Events.ORGANIZER, ownerAccount)
        }
    }

}