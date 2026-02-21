/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.util.TimeApiExtensions.abs
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDate
import at.bitfire.ical4android.util.TimeApiExtensions.toIcal4jDateTime
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import java.time.Instant
import java.time.ZoneOffset

/**
 * Maps a potentially present [Events.DURATION] to a VEvent [DtEnd] property.
 *
 * Does nothing when:
 *
 * - [Events.DTEND] is present / not null (because DTEND then takes precedence over DURATION), and/or
 * - [Events.DURATION] is null / not present.
 */
class DurationHandler(
    private val tzRegistry: TimeZoneRegistry
): AndroidEventFieldHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        TODO("ical4j 4.x")
    }

}