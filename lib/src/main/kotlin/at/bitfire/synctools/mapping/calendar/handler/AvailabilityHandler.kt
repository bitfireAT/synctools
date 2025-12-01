/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Transp

class AvailabilityHandler: AndroidEventFieldHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val transp: Transp = when (from.entityValues.getAsInteger(Events.AVAILABILITY)) {
            Events.AVAILABILITY_FREE ->
                Transp.TRANSPARENT

            /* Events.AVAILABILITY_BUSY, Events.AVAILABILITY_TENTATIVE */
            else ->
                Transp.OPAQUE
        }
        if (transp != Transp.OPAQUE)    // iCalendar default value is OPAQUE
            to.properties += transp
    }

}