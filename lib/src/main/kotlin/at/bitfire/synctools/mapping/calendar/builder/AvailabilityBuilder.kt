/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Transp

class AvailabilityBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val availability = when (from.transparency) {
            Transp.OPAQUE -> Events.AVAILABILITY_BUSY
            Transp.TRANSPARENT -> Events.AVAILABILITY_FREE
        }
        to.entityValues.put(
            Events.AVAILABILITY,
            if (from.transparency) Events.AVAILABILITY_BUSY else Events.AVAILABILITY_FREE
        )
    }

}