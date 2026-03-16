/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import androidx.annotation.VisibleForTesting
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion
import java.io.Writer
import java.time.temporal.Temporal
import javax.annotation.WillNotClose
import kotlin.jvm.optionals.getOrNull

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
        ical += ImmutableVersion.VERSION_2_0

        // add PRODID
        if (event.prodId != null)
            ical += event.prodId

        // keep record of used timezone IDs and earliest DTSTART in order to be able to add VTIMEZONEs
        var earliestStart: Temporal? = null
        val usedTimezoneIds = mutableSetOf<String>()

        // add main event
        if (event.main != null) {
            ical += event.main

            earliestStart = event.main.dtStart<Temporal>()?.date
            usedTimezoneIds += timeZonesOf(event.main)
        }

        // recurrence exceptions
        for (exception in event.exceptions) {
            ical += exception

            exception.dtStart<Temporal>()?.date?.let { start ->
                if (earliestStart == null || TemporalAdapter.isBefore(start, earliestStart))
                    earliestStart = start
            }
            usedTimezoneIds += timeZonesOf(exception)
        }

        /* Add VTIMEZONE components. Unfortunately can't generate VTIMEZONEs from the actual ZoneIds,
        so we have to include the VTIMEZONEs shipped with ical4j, even if those are not the same
        as the system time zones. This is a known problem, but there's currently no known solution.
        Most clients ignore the VTIMEZONE anyway if they know the TZID [RFC 7809 3.1.3 "Observation
        and experiments"], and Android/Java/IANA timezones are usually known to all clients. */
        val tzReg = TimeZoneRegistryFactory.getInstance().createRegistry()
        for (tzId in usedTimezoneIds) {
            val vTimeZone = tzReg.getTimeZone(tzId).vTimeZone

            // TODO update vTimezone TZID if != tzId + example (Kiev)

            // TODO: extract minifyVTimeZone to class
            /*val minifiedVTimeZone = ICalendar.minifyVTimeZone(vTimeZone, earliestStart)
            ical += minifiedVTimeZone*/
            ical += vTimeZone
        }

        CalendarOutputter(false).output(ical, to)
    }

    /**
     * Extracts all unique time zone identifiers from the given component and its subcomponents.
     *
     * This method searches through all properties of the component, filtering for date properties
     * that contain a TZID parameter. It also recursively processes subcomponents (such as alarms)
     * if the component is a VEvent.
     *
     * @param component The component to extract time zone identifiers from.
     * @return A set of unique time zone identifiers found in the component and its subcomponents.
     */
    @VisibleForTesting
    internal fun timeZonesOf(component: Component): Set<String> {
        val timeZones = mutableSetOf<String>()

        // iterate through all properties
        timeZones += component.propertyList.all
            .filterIsInstance<DateProperty<*>>()
            .mapNotNull {
                /* Note: When a property like DTSTART is created like DtStart(ZonedDateTime()),
                the setDate() calls refreshParameters.refreshParameters() and that one sets the TZID
                from the actual timezone ID. */
                it.getParameter<TzId>(Parameter.TZID).getOrNull()?.value
            }
            .toSet()

        // also iterate through subcomponents like alarms recursively
        if (component is VEvent)
            for (subcomponent in component.componentList.all)
                timeZones += timeZonesOf(subcomponent)

        return timeZones
    }

    /*private fun Temporal?.toZonedDateTime(zoneId: ZoneId): ZonedDateTime? {
        return when (this) {
            is LocalDate -> this.atStartOfDay().atZone(zoneId)
            is LocalDateTime -> this.atZone(zoneId)
            is OffsetDateTime -> this.atZoneSameInstant(zoneId)
            is Instant -> this.atZone(zoneId)
            is ZonedDateTime -> this
            else -> null
        }
    }
    */

}