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
        val ical = Calendar()
        ical.properties += Version.VERSION_2_0

        // add PRODID
        if (event.prodId != null)
            ical.properties += event.prodId

        // keep record of used timezones and earliest DTSTART to generate minified VTIMEZONEs
        var earliestStart: Date? = null
        val usedTimeZones = mutableSetOf<TimeZone>()

        // add main event
        if (event.main != null) {
            ical.components += event.main

            earliestStart = event.main.getProperty<DtStart>(Property.DTSTART)?.date
            usedTimeZones += timeZonesOf(event.main)
        }

        // recurrence exceptions
        for (exception in event.exceptions) {
            ical.components += exception

            exception.getProperty<DtStart>(Property.DTSTART)?.date?.let { start ->
                if (earliestStart == null || start <= earliestStart)
                    earliestStart = start
            }
            usedTimeZones += timeZonesOf(exception)
        }

        // add VTIMEZONE components
        for (tz in usedTimeZones)
            ical.components += ICalendar.minifyVTimeZone(tz.vTimeZone, earliestStart)

        CalendarOutputter(false).output(ical, to)
    }

    private fun timeZonesOf(component: CalendarComponent): Set<TimeZone> {
        val timeZones = mutableSetOf<TimeZone>()

        // properties
        timeZones += component.properties
            .filterIsInstance<DateProperty>()
            .mapNotNull { (it.date as? DateTime)?.timeZone }

        // properties of subcomponents (alarms)
        if (component is VEvent)
            for (subcomponent in component.components)
                timeZones += subcomponent.properties
                    .filterIsInstance<DateProperty>()
                    .mapNotNull { (it.date as? DateTime)?.timeZone }

        return timeZones
    }

}