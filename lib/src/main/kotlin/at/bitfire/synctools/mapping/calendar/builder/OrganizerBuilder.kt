/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.isGroupScheduled
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.property.Organizer

class OrganizerBuilder(
    private val accountName: String
): AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        // always use account name for non-group-scheduled events
        if (!from.isGroupScheduled()) {
            to.entityValues.put(Events.ORGANIZER, accountName)
            return true
        }

        val organizer: Organizer? = from.organizer
        val uri = organizer?.calAddress

        // Currently, only email addresses are supported for ORGANIZER.
        val email = if (uri != null && uri.scheme.equals("mailto", true))
            uri.schemeSpecificPart
        else
            organizer?.getParameter<Email>(Parameter.EMAIL)?.value

        // fall back to account name
        to.entityValues.put(Events.ORGANIZER, email ?: accountName)
        return true
    }

}