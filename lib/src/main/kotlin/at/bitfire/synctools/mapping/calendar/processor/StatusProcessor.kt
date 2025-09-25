/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Status

class StatusProcessor: AndroidEventFieldProcessor {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        when (from.entityValues.getAsInteger(Events.STATUS)) {
            Events.STATUS_CONFIRMED ->
                to.properties += Status.VEVENT_CONFIRMED

            Events.STATUS_TENTATIVE ->
                to.properties += Status.VEVENT_TENTATIVE

            Events.STATUS_CANCELED ->
                to.properties += Status.VEVENT_CANCELLED

            // do nothing for null
        }
    }

}