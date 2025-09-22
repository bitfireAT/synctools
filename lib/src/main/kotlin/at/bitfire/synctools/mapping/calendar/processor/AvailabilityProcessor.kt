/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event

class AvailabilityProcessor: AndroidEventFieldProcessor {

    override fun process(from: Entity, main: Entity, to: Event) {
        to.opaque = from.entityValues.getAsInteger(Events.AVAILABILITY) != Events.AVAILABILITY_FREE
    }

}