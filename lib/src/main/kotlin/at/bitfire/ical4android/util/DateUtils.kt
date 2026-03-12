/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.util

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.DateProperty
import java.io.StringReader
import java.time.ZoneId

/**
 * Date/time utilities
 *
 * Before this object is accessed the first time, the accessing thread's contextClassLoader
 * must be set to an Android Context.classLoader!
 */
object DateUtils {

    // time zones

    @Deprecated("Use DatePropertyTzMapper instead")
    fun findAndroidTimezoneID(tzID: String?): String =
        TODO("Will be removed during ical4j 4.x update")

    /**
     * Gets a [ZoneId] from a given ID string. In opposite to [ZoneId.of],
     * this methods returns null when the zone is not available.
     *
     * @param id    zone ID, like "Europe/Berlin" (may be null)
     *
     * @return      ZoneId or null if the argument was null or no zone with this ID could be found
     */
    @Deprecated("Not needed with correct mapping")
    fun getZoneId(id: String?): ZoneId? =
        TODO("Will be removed during ical4j 4.x update")

    /**
     * Determines whether a given date represents a DATE value.
     * @param date date property to check
     * @return *true* if the date is a DATE value; *false* otherwise (for instance, when the argument is a DATE-TIME value or null)
     */
    fun isDate(date: DateProperty<*>?): Boolean =
        date != null && !TemporalAdapter.isDateTimePrecision(date.date)

    /**
     * Determines whether a given date represents a DATE-TIME value.
     * @param date date property to check
     * @return *true* if the date is a DATE-TIME value; *false* otherwise (for instance, when the argument is a DATE value or null)
     */
    fun isDateTime(date: DateProperty<*>?): Boolean =
        TODO("ical4j 4.x")
        //date != null && date.date is DateTime

    /**
     * Parses an iCalendar that only contains a `VTIMEZONE` definition to a VTimeZone object.
     *
     * @param timezoneDef iCalendar with only a `VTIMEZONE` definition
     *
     * @return parsed [VTimeZone], or `null` when the timezone definition can't be parsed
     */
    fun parseVTimeZone(timezoneDef: String): VTimeZone? {
        val builder = CalendarBuilder()
        try {
            val cal = builder.build(StringReader(timezoneDef))
            return TODO("ical4j 4.x")
            //return cal.getComponent(VTimeZone.VTIMEZONE) as VTimeZone
        } catch (_: Exception) {
            // Couldn't parse timezone definition
            return null
        }
    }

}