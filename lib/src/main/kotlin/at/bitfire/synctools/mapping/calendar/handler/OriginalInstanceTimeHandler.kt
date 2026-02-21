/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.util.DateUtils
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.util.TimeZones

class OriginalInstanceTimeHandler(
    private val tzRegistry: TimeZoneRegistry
): AndroidEventFieldHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        TODO("ical4j 4.x")
    }

}