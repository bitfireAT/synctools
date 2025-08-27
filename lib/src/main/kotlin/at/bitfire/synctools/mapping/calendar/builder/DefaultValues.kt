/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import java.time.Duration
import java.time.Period
import java.time.temporal.TemporalAmount

object DefaultValues {

    /**
     * Provides a default duration for an event when there's a DTSTART, but no
     * DTEND and no DURATION.
     *
     * @param allDay    whether DTSTART is a DATE (all-day event)
     *
     * For the chosen values, see RFC 5545 3.6.1 Event Component:
     *
     * > For cases where a "VEVENT" calendar component
     * > specifies a "DTSTART" property with a DATE value type but no
     * > "DTEND" nor "DURATION" property, the event's duration is taken to
     * > be one day.
     *
     * and
     *
     * > For cases where a "VEVENT" calendar component
     * > specifies a "DTSTART" property with a DATE-TIME value type but no
     * > "DTEND" property, the event ends on the same calendar date and
     * > time of day specified by the "DTSTART" property.
     *
     * @return one day if [allDay] is `true`, zero seconds otherwise
     */
    fun defaultDuration(allDay: Boolean): TemporalAmount =
        if (allDay)
            Period.ofDays(1)    // days (actual length in seconds may vary)
        else
            Duration.ofSeconds(0)   // exact number of seconds

}