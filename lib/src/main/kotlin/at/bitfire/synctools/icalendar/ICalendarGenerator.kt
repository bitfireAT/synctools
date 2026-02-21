/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.ical4android.ICalendar
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Version
import java.io.Writer
import javax.annotation.WillNotClose

/**
 * Writes an ical4j [net.fortuna.ical4j.model.Calendar] to a stream that contains an iCalendar
 * (VCALENDAR with respective components and optional VTIMEZONEs).
 */
class ICalendarGenerator {

    /**
     * Generates an iCalendar from the given [AssociatedComponents].
     *
     * @param event     event to generate iCalendar from
     * @param to        stream that the iCalendar is written to
     */
    fun write(event: AssociatedComponents<*>, @WillNotClose to: Writer) {
        TODO("ical4j 4.x")
    }

    private fun timeZonesOf(component: CalendarComponent): Set<TimeZone> {
        TODO("ical4j 4.x")
    }

}