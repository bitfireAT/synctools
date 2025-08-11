/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Transp

class AvailabilityBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        val availability = when (from.transparency) {
            Transp.TRANSPARENT ->
                Events.AVAILABILITY_FREE

            else /* including Transp.OPAQUE */ ->
                Events.AVAILABILITY_BUSY
        }
        to.entityValues.put(Events.AVAILABILITY, availability)
        return true
    }

}