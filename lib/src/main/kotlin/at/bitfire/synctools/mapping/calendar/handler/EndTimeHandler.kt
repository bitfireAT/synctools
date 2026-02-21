/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.annotation.VisibleForTesting
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import java.time.Duration
import java.time.Instant
import java.util.logging.Logger

/**
 * Maps a potentially present [Events.DTEND] to a VEvent [DtEnd] property.
 *
 * If [Events.DTEND] is null / not present:
 *
 * - If [Events.DURATION] is present / not null, [DurationHandler] is responsible for generating the VEvent's [DtEnd].
 * - If [Events.DURATION] is null / not present, this class is responsible for generating the VEvent's [DtEnd].
 */
class EndTimeHandler(
    private val tzRegistry: TimeZoneRegistry
): AndroidEventFieldHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: VEvent) {
        TODO("ical4j 4.x")
    }

    @VisibleForTesting
    internal fun calculateFromDefault(tsStart: Long, allDay: Boolean): Long =
        if (allDay) {
            // all-day: default duration is PT1D; all-day events are always in UTC time zone
            val start = Instant.ofEpochMilli(tsStart)
            val end = start + Duration.ofDays(1)
            end.toEpochMilli()
        } else {
            // non-all-day: default duration is PT0S; end time = start time
            tsStart
        }

}