/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.util

import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import at.bitfire.synctools.util.AndroidTimeUtils.androidifyTimeZone
import at.bitfire.synctools.util.AndroidTimeUtils.storageTzId
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TemporalAmountAdapter
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.DateListProperty
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.util.TimeZones
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Period
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAmount
import java.util.LinkedList
import java.util.Locale
import java.util.TimeZone
import java.util.logging.Logger

object AndroidTimeUtils {

    /**
     * Timezone ID to store for all-day events, according to CalendarContract.Events SDK documentation.
     */
    const val TZID_UTC = "UTC"

    private const val RECURRENCE_LIST_TZID_SEPARATOR = ';'
    private const val RECURRENCE_LIST_VALUE_SEPARATOR = ","

    /**
     * Used to separate multiple RRULEs/EXRULEs in the RRULE/EXRULE storage field.
     */
    const val RECURRENCE_RULE_SEPARATOR = "\n"

    private val logger
        get() = Logger.getLogger(javaClass.name)


    /**
     * Ensures that a given [net.fortuna.ical4j.model.property.DateProperty] either
     *
     * 1. has a time zone with an ID that is available in Android, or
     * 2. is an UTC property ([net.fortuna.ical4j.model.property.DateProperty.isUtc] = *true*).
     *
     * To get the time zone ID which shall be given to the Calendar provider,
     * use [storageTzId].
     *
     * @param date [net.fortuna.ical4j.model.property.DateProperty] to validate. Values which are not DATE-TIME will be ignored.
     * @param tzRegistry    time zone registry to get time zones from
     */
    fun <T : java.time.temporal.Temporal> androidifyTimeZone(date: DateProperty<T>?, tzRegistry: TimeZoneRegistry) {
        TODO("ical4j 4.x")
    }

    /**
     * Ensures that a given [net.fortuna.ical4j.model.property.DateListProperty] either
     *
     * 1. has a time zone with an ID that is available in Android, or
     * 2. is an UTC property ([DateProperty.isUtc] = *true*).
     * *
     * @param dateList [net.fortuna.ical4j.model.property.DateListProperty] to validate. Values which are not DATE-TIME will be ignored.
     */
    fun <T : java.time.temporal.Temporal> androidifyTimeZone(dateList: DateListProperty<T>) {
        TODO("ical4j 4.x")
    }

    /**
     * Returns the time-zone ID for a given date or date-time that should be used to store it
     * in the Android calendar provider.
     *
     * Does not check whether Android actually knows the time zone ID – use [androidifyTimeZone] for that.
     *
     * @param date DateProperty (DATE or DATE-TIME) whose time-zone information is used
     *
     * @return - UTC for dates and UTC date-times
     *         - the specified time zone ID for date-times with given time zone
     *         - the currently set default time zone ID for floating date-times
     */
    fun <T : java.time.temporal.Temporal> storageTzId(date: DateProperty<T>): String =
        TODO("ical4j 4.x")


    // recurrence sets

    /**
     * Concatenates, if necessary, multiple RDATE/EXDATE lists and converts them to
     * a formatted string which Android calendar provider can process.
     *
     * Android [expects this format](https://android.googlesource.com/platform/frameworks/opt/calendar/+/68b3632330e7a9a4f9813b7eb671dbfd78c25bcd/src/com/android/calendarcommon2/RecurrenceSet.java#138):
     * `[TZID;]date1,date2,date3` where date is `yyyymmddThhmmss` (when
     * TZID is given) or `yyyymmddThhmmssZ`. We don't use the TZID format here because then we're limited
     * to one time-zone, while an iCalendar may contain multiple EXDATE/RDATE lines with different time zones.
     *
     * This method converts the values to the type of [dtStart], if necessary:
     *
     * - DTSTART (DATE-TIME) and RDATE/EXDATE (DATE) → method converts RDATE/EXDATE to DATE-TIME with same time as DTSTART
     * - DTSTART (DATE) and RDATE/EXDATE (DATE-TIME) → method converts RDATE/EXDATE to DATE (just drops time)
     *
     * @param dates     one more more lists of RDATE or EXDATE
     * @param dtStart   used to determine whether the event is an all-day event or not; also used to
     *                  generate the date-time if the event is not all-day but the exception is
     *
     * @return formatted string for Android calendar provider
     */
    fun <T : java.time.temporal.Temporal> recurrenceSetsToAndroidString(dates: List<DateListProperty<T>>, dtStart: Date): String {
        TODO("ical4j 4.x")
    }

    /**
     * Takes a formatted string as provided by the Android calendar provider and returns a DateListProperty
     * constructed from these values.
     *
     * @param dbStr         formatted string from Android calendar provider (RDATE/EXDATE field)
     *                      expected format: `[TZID;]date1,date2,date3` where date is `yyyymmddThhmmss[Z]`
     * @param tzRegistry    time zone registry
     * @param allDay        true: list will contain DATE values; false: list will contain DATE_TIME values
     * @param exclude       this time stamp won't be added to the [DateListProperty]
     * @param generator     generates the [DateListProperty]; must call the constructor with the one argument of type [net.fortuna.ical4j.model.DateList]
     *
     * @return instance of "type" containing the parsed dates/times from the string
     *
     * @throws java.text.ParseException when the string cannot be parsed
     */
    fun<T: DateListProperty<*>> androidStringToRecurrenceSet(
        dbStr: String,
        tzRegistry: TimeZoneRegistry,
        allDay: Boolean,
        exclude: Long? = null,
        generator: (DateList<*>) -> T
    ): T {
        TODO("ical4j 4.x")
    }

    /**
     * Concatenates, if necessary, multiple RDATE/EXDATE lists and converts them to
     * a formatted string which OpenTasks can process.
     * OpenTasks expect a list of RFC 5545 DATE (`yyyymmdd`) or DATE-TIME (`yyyymmdd[Z]`) values,
     * where the time zone is stored in a separate field.
     *
     * @param dates         one more more lists of RDATE or EXDATE
     * @param tz            output time zone (*null* for all-day event)
     *
     * @return formatted string for Android calendar provider
     */
    fun <T : java.time.temporal.Temporal> recurrenceSetsToOpenTasksString(dates: List<DateListProperty<T>>, tz: net.fortuna.ical4j.model.TimeZone?): String {
        TODO("ical4j 4.x")
    }


    // duration

    /**
     * Checks and fixes DURATION values with incorrect format which can't be
     * parsed by ical4j. Searches for values like "1H" and "3M" and
     * groups them together in a standards-compliant way.
     *
     * @param durationStr value from the content provider (like "PT3600S" or "P3600S")
     * @return duration value in RFC 2445 format ("PT3600S" when the argument was "P3600S")
     */
    fun parseDuration(durationStr: String): TemporalAmount {
        TODO("ical4j 4.x")
    }

}