/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.util

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.DateProperty
import java.io.StringReader
import java.time.ZoneId
import java.util.logging.Logger

/**
 * Date/time utilities
 *
 * Before this object is accessed the first time, the accessing thread's contextClassLoader
 * must be set to an Android Context.classLoader!
 */
object DateUtils {

    private val logger
        get() = Logger.getLogger(javaClass.name)


    // time zones

    /**
     * Find the best matching Android (= available in system and Java timezone registry)
     * time zone ID for a given arbitrary time zone ID:
     *
     * 1. Use a case-insensitive match ("EUROPE/VIENNA" will return "Europe/Vienna",
     *    assuming "Europe/Vienna") is available in Android.
     * 2. Find partial matches (case-sensitive) in both directions, so both "Vienna"
     *    and "MyClient: Europe/Vienna" will return "Europe/Vienna". This shouln't be
     *    case-insensitive, because that would for instance return "EST" for "Westeuropäische Sommerzeit".
     * 3. If nothing can be found or [tzID] is `null`, return the system default time zone.
     *
     * @param tzID time zone ID to be converted into Android time zone ID
     *
     * @return best matching Android time zone ID
     */
    fun findAndroidTimezoneID(tzID: String?): String {
        val availableTZs = ZoneId.getAvailableZoneIds()
        var result: String? = null

        if (tzID != null) {
            // first, try to find an exact match (case insensitive)
            result = availableTZs.firstOrNull { it.equals(tzID, true) }

            // if that doesn't work, try to find something else that matches
            if (result == null)
                for (availableTZ in availableTZs)
                    if (availableTZ.contains(tzID) || tzID.contains(availableTZ)) {
                        result = availableTZ
                        logger.warning("Couldn't find system time zone \"$tzID\", assuming $result")
                        break
                    }
        }

        // if that doesn't work, use device default as fallback
        return result ?: TimeZone.getDefault().id
    }

    /**
     * Gets a [ZoneId] from a given ID string. In opposite to [ZoneId.of],
     * this methods returns null when the zone is not available.
     *
     * @param id    zone ID, like "Europe/Berlin" (may be null)
     *
     * @return      ZoneId or null if the argument was null or no zone with this ID could be found
     */
    fun getZoneId(id: String?): ZoneId? =
            id?.let {
                try {
                    val zone = ZoneId.of(id)
                    zone
                } catch (_: Exception) {
                    null
                }
            }

    /**
     * Determines whether a given date represents a DATE value.
     * @param date date property to check
     * @return *true* if the date is a DATE value; *false* otherwise (for instance, when the argument is a DATE-TIME value or null)
     */
    fun isDate(date: DateProperty?) = date != null && date.date is Date && date.date !is DateTime

    /**
     * Determines whether a given date represents a DATE-TIME value.
     * @param date date property to check
     * @return *true* if the date is a DATE-TIME value; *false* otherwise (for instance, when the argument is a DATE value or null)
     */
    fun isDateTime(date: DateProperty?) = date != null && date.date is DateTime

    /**
     * Parses an iCalendar that only contains a `VTIMEZONE` definition to a VTimeZone object.
     *
     * @param timezoneDef iCalendar with only a `VTIMEZONE` definition
     *
     * @return parsed [VTimeZone], or `null` when the timezone definition can't be parsed
     */
    fun parseVTimeZone(timezoneDef: String ): VTimeZone? {
        val builder = CalendarBuilder()
        try {
            val cal = builder.build(StringReader(timezoneDef))
            return cal.getComponent(VTimeZone.VTIMEZONE) as VTimeZone
        } catch (_: Exception) {
            // Couldn't parse timezone definition
            return null
        }
    }

}