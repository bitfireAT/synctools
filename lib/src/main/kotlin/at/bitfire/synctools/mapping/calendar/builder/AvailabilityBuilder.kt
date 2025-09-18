/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event

class AvailabilityBuilder: AndroidEntityBuilder {

    override fun build(from: Event, main: Event, to: Entity) {
        to.entityValues.put(
            Events.AVAILABILITY,
            if (from.opaque) Events.AVAILABILITY_BUSY else Events.AVAILABILITY_FREE
        )
    }

}