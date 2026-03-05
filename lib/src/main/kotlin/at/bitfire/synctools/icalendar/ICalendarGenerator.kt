/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.ical4android.ICalendar
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion
import java.io.Writer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.Temporal
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
        ical.addProperty(ImmutableVersion.VERSION_2_0)

        // add PRODID
        if (event.prodId != null)
            ical.addProperty(event.prodId)

        // keep record of used timezones and earliest DTSTART to generate minified VTIMEZONEs
        var earliestStart: Temporal? = null
        val usedTimeZones = mutableSetOf<ZoneId>()

        // add main event
        if (event.main != null) {
            ical.addComponent(event.main)

            earliestStart = event.main.dtStart<Temporal>()?.date
            usedTimeZones += timeZonesOf(event.main)
        }

        // recurrence exceptions
        for (exception in event.exceptions) {
            ical.addComponent(exception)

            exception.dtStart<Temporal>()?.date?.let { start ->
                if (earliestStart == null || TemporalAdapter.isBefore(start, earliestStart))
                    earliestStart = start
            }
            usedTimeZones += timeZonesOf(exception)
        }

        // add VTIMEZONE components
        val tzReg = TimeZoneRegistryFactory.getInstance().createRegistry()
        for (tz in usedTimeZones) {
            val vTimeZone = tzReg.getTimeZone(tz.id).vTimeZone
            val minifiedVTimeZone = ICalendar.minifyVTimeZone(vTimeZone, earliestStart.toZonedDateTime(tz))
            ical.addComponent(minifiedVTimeZone)
        }

        CalendarOutputter(false).output(ical, to)
    }

    private fun timeZonesOf(component: CalendarComponent): Set<ZoneId> {
        val timeZones = mutableSetOf<ZoneId>()

        // properties
        timeZones += component.propertyList.all
            .filterIsInstance<DateProperty<*>>()
            .mapNotNull { (it.date as? ZonedDateTime)?.zone }

        // properties of subcomponents (alarms)
        if (component is VEvent)
            for (subcomponent in component.componentList.all)
                timeZones += subcomponent.propertyList.all
                    .filterIsInstance<DateProperty<*>>()
                    .mapNotNull { (it.date as? ZonedDateTime)?.zone }

        return timeZones
    }

    private fun Temporal?.toZonedDateTime(zoneId: ZoneId): ZonedDateTime? {
        return when (this) {
            is LocalDate -> this.atStartOfDay().atZone(zoneId)
            is LocalDateTime -> this.atZone(zoneId)
            is OffsetDateTime -> this.atZoneSameInstant(zoneId)
            is Instant -> this.atZone(zoneId)
            is ZonedDateTime -> this
            else -> null
        }
    }
}