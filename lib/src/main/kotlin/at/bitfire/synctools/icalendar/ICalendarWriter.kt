/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.ical4android.ICalendar.Companion.minifyVTimeZone
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.Version
import java.io.Writer
import kotlin.math.min

class ICalendarWriter(
    private val prodId: String
) {

    fun write(components: ComponentList<CalendarComponent>, to: Writer) {
        val properties = propertyListOf(
            Version.VERSION_2_0,
            ProdId(prodId)
        )
        val iCalendar = Calendar(properties, components)

        val tzInfo = extractTimeZones(components)
        val firstDate = tzInfo.firstTimestamp?.let { firstTs ->
            DateTime(true).apply {
                time = firstTs
            }
        }
        for (timeZone in tzInfo.usedTimeZones)
            iCalendar.components += minifyVTimeZone(
                originalTz = timeZone.vTimeZone,
                start = firstDate
            )

        CalendarOutputter(/* validating = */ true).output(iCalendar, to)
    }

    
    // time zone processing
    
    class TimeZoneInfo(
        val usedTimeZones: Set<TimeZone>,
        val firstTimestamp: Long?
    )
    
    private fun extractTimeZones(components: ComponentList<CalendarComponent>): TimeZoneInfo {
        val usedTimeZones = mutableSetOf<TimeZone>()
        var firstTs: Long? = null
        for (component in components)
            for (dateProperty in component.properties.filterIsInstance<DateProperty>()) {
                if (dateProperty.timeZone != null)
                    usedTimeZones += dateProperty.timeZone
                firstTs = if (firstTs == null)
                    dateProperty.date.time
                else
                    min(firstTs, dateProperty.date.time)
            }
        return TimeZoneInfo(
            usedTimeZones = usedTimeZones,
            firstTimestamp = firstTs
        )
    }

}