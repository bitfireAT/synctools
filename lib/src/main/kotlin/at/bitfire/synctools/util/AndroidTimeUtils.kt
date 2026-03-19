/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.util

import at.bitfire.ical4android.util.TimeApiExtensions
import net.fortuna.ical4j.model.CalendarDateFormat
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.model.TemporalAmountAdapter
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.DateListProperty
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.RDate
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoField
import java.time.temporal.Temporal
import java.time.temporal.TemporalAmount
import java.util.logging.Logger
import kotlin.collections.buildList
import kotlin.jvm.optionals.getOrDefault

object AndroidTimeUtils {

    /**
     * Timezone ID to store for all-day events, according to CalendarContract.Events SDK documentation.
     */
    const val TZID_UTC = "UTC"

    private const val RECURRENCE_LIST_TZID_SEPARATOR = ';'
    private const val RECURRENCE_LIST_VALUE_SEPARATOR = ","

    private val logger
        get() = Logger.getLogger(javaClass.name)


    @Deprecated("Use DatePropertyTzMapper instead", replaceWith =
        ReplaceWith("date.normalizedDate()", "at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate"))
    fun androidifyTimeZone(date: DateProperty<*>?, tzRegistry: TimeZoneRegistry) {
        TODO("Will be removed during ical4j 4.x update")
    }

    /**
     * Ensures that a given [net.fortuna.ical4j.model.property.DateListProperty] either
     *
     * 1. has a time zone with an ID that is available in Android, or
     * 2. is an UTC property ([DateProperty.isUtc] = *true*).
     * *
     * @param dateList [net.fortuna.ical4j.model.property.DateListProperty] to validate. Values which are not DATE-TIME will be ignored.
     */
    @Deprecated("Use DatePropertyTzMapper instead")
    fun androidifyTimeZone(dateList: DateListProperty<*>): DateListProperty<*> =
        TODO("Should be implemented in DatePropertyTzMapper, if needed")
        /*val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }

        // periods (RDate only)
        TODO("ical4j 4.x")
        val periods = (dateList as? RDate)?.periods
        if (periods != null && periods.isNotEmpty() && !periods.isUtc) {
            val tzID = DateUtils.findAndroidTimezoneID(periods.timeZone?.id)

            // Setting the time zone won't work until resolved in ical4j (https://github.com/ical4j/ical4j/discussions/568)
            // DateListProperty.setTimeZone() does not set the timeZone property when the DateList has PERIODs
            dateList.timeZone = tzRegistry.getTimeZone(tzID)

            return //  RDate can only contain periods OR dates - not both, bail out fast
        }

        // date-times (RDate and ExDate)
        val dates = dateList.dates
        if (dates != null && dates.isNotEmpty()) {
            if (dates.type == Value.DATE_TIME && !dates.isUtc) {
                val tzID = DateUtils.findAndroidTimezoneID(dates.timeZone?.id)
                dateList.timeZone = tzRegistry.getTimeZone(tzID)
            }
        }*/

    @Deprecated("Implementation may vary by provider and should be done in the respective mapper")
    fun storageTzId(date: DateProperty<*>): String =
        TODO("Will be removed during ical4j 4.x update")


    // recurrence sets

    /**
     * Takes a formatted string as provided by the Android calendar provider and returns a DateListProperty
     * constructed from these values.
     *
     * @param dbStr         formatted string from Android calendar provider (RDATE/EXDATE field)
     *                      expected format: `[TZID;]date1,date2,date3` where date is `yyyymmddThhmmss[Z]`
     * @param allDay        true: list will contain DATE values; false: list will contain DATE_TIME values
     * @param exclude       this time stamp won't be added to the [DateListProperty]
     * @param generator     generates the [DateListProperty]; must call the constructor with the one argument of type [net.fortuna.ical4j.model.DateList]
     *
     * @return instance of "type" containing the parsed dates/times from the string
     *
     * @throws java.time.format.DateTimeParseException if one of the datestrings cannot be parsed
     * @throws java.time.DateTimeException if the TZID has an invalid format
     * @throws java.time.zone.ZoneRulesException if the TZID is a region ID that cannot be found
     */
    fun<T: DateListProperty<*>> androidStringToRecurrenceSet(
        dbStr: String,
        allDay: Boolean,
        exclude: Temporal? = null,
        generator: (DateList<*>) -> T
    ): T? {
        if (dbStr.isEmpty()) return null

        // split string into time zone and actual dates
        var zoneId: ZoneId?
        val datesStr: String

        val limiter = dbStr.indexOf(RECURRENCE_LIST_TZID_SEPARATOR)
        if (limiter != -1) {    // TZID given
            val tzId = dbStr.take(limiter)
            zoneId = ZoneId.of(tzId).takeIf { it != ZoneOffset.UTC }
            datesStr = dbStr.substring(limiter + 1)
        } else {
            zoneId = null
            datesStr = dbStr
        }

        // process date string and generate list of Temporals (exluding `exclude`)
        val dates = datesStr
            .splitToSequence(RECURRENCE_LIST_VALUE_SEPARATOR)
            .map { dateString ->
                if (zoneId == null) {
                    val instant = TemporalAdapter.parse<Instant>(dateString, CalendarDateFormat.UTC_DATE_TIME_FORMAT).temporal
                    if (allDay) {
                        instant.atZone(ZoneOffset.UTC).toLocalDate()
                    } else {
                        instant
                    }
                } else {
                    val localDateTime = TemporalAdapter.parse<LocalDateTime>(dateString, CalendarDateFormat.FLOATING_DATE_TIME_FORMAT).temporal
                    if (allDay) {
                        localDateTime.toLocalDate()
                    } else {
                        localDateTime.atZone(zoneId)
                    }
                }
            }
            .filterNot { date ->
                // filter excluded date
                date == exclude
            }
            .toList()

        if (dates.isEmpty())
            return null

        val dateList = DateList(dates)

        // generate requested DateListProperty (RDate/ExDate) from list of DATEs or DATE-TIMEs
        val dateListProperty = generator(dateList)

        // add TZID or DATE parameter if necessary
        when (val firstTemporal = dateList.dates.first()) {
            is ZonedDateTime -> dateListProperty.add<T>(TzId(firstTemporal.zone.id))
            is LocalDate -> dateListProperty.add<T>(Value.DATE)
        }

        return dateListProperty
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
    fun recurrenceSetsToOpenTasksString(dates: List<DateListProperty<*>>, tz: TimeZone?): String {
        val allDay = tz == null
        val strDatesBuilder = StringBuilder()
        for (dateListProp in dates) {
            if (dateListProp is RDate && dateListProp.periods.getOrDefault(emptyList()).isNotEmpty())
                logger.warning("RDATE PERIOD not supported, ignoring")

            fun Int.padWithZeros(length: Int = 2) = toString().padStart(length, '0')

            for (date in dateListProp.dates) {
                // The timezone is handled externally by a specific timezone column. We just need
                // to use the datetime adjusted by this tz
                val isUtc: Boolean = date.isSupported(ChronoField.OFFSET_SECONDS) && date.get(ChronoField.OFFSET_SECONDS) == 0
                val adjDate = if (!allDay && !TemporalAdapter.isFloating(date)) {
                    if (isUtc)
                        // UTC dates are not converted, they get 'Z' added at the end
                        date
                    else
                        OffsetDateTime.from(date).atZoneSameInstant(tz.toZoneId())
                } else {
                    date
                }

                val sb = StringBuilder()
                sb.append(adjDate.get(ChronoField.YEAR))
                sb.append(adjDate.get(ChronoField.MONTH_OF_YEAR).padWithZeros())
                sb.append(adjDate.get(ChronoField.DAY_OF_MONTH).padWithZeros())
                if (!allDay) {
                    sb.append('T')
                    if (adjDate.isSupported(ChronoField.HOUR_OF_DAY)) {
                        sb.append(adjDate.get(ChronoField.HOUR_OF_DAY).padWithZeros())
                        sb.append(adjDate.get(ChronoField.MINUTE_OF_HOUR).padWithZeros())
                        sb.append(adjDate.get(ChronoField.SECOND_OF_MINUTE).padWithZeros())
                    } else {
                        // Time not supported - date doesn't have time (LocalDate)
                        // Force time to start of day
                        sb.append("000000")
                    }
                }

                // If the original date was UTC, append 'Z' at the end
                if (isUtc) sb.append('Z')

                strDatesBuilder.append(sb)
                strDatesBuilder.append(RECURRENCE_LIST_VALUE_SEPARATOR)
            }
        }
        // Remove suffix of RECURRENCE_LIST_VALUE_SEPARATOR to get rid of last added one
        return strDatesBuilder.toString().removeSuffix(RECURRENCE_LIST_VALUE_SEPARATOR)
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
        /** [RFC 2445/5445]
         * dur-value  = (["+"] / "-") "P" (dur-date / dur-time / dur-week)
         * dur-date   = dur-day [dur-time]
         * dur-day    = 1*DIGIT "D"
         * dur-time   = "T" (dur-hour / dur-minute / dur-second)
         * dur-week   = 1*DIGIT "W"
         * dur-hour   = 1*DIGIT "H" [dur-minute]
         * dur-minute = 1*DIGIT "M" [dur-second]
         * dur-second = 1*DIGIT "S"
         */
        val possibleFormats = Regex("([+-]?)P?(T|((\\d+)W)|((\\d+)D)|((\\d+)H)|((\\d+)M)|((\\d+)S))*")
                                         //  1            4         6         8         10        12
        possibleFormats.matchEntire(durationStr)?.let { result ->
            fun fromMatch(s: String) = if (s.isEmpty()) 0 else s.toInt()

            val intSign = if (result.groupValues[1] == "-") -1 else 1
            val intDays = fromMatch(result.groupValues[4]) * TimeApiExtensions.DAYS_PER_WEEK + fromMatch(result.groupValues[6])
            val intHours = fromMatch(result.groupValues[8])
            val intMinutes = fromMatch(result.groupValues[10])
            val intSeconds = fromMatch(result.groupValues[12])

            return if (intDays != 0 && intHours == 0 && intMinutes == 0 && intSeconds == 0)
                Period.ofDays(intSign * intDays)
            else
                Duration.ofSeconds(intSign * (
                        intDays * TimeApiExtensions.SECONDS_PER_DAY.toLong() +
                        intHours * TimeApiExtensions.SECONDS_PER_HOUR +
                        intMinutes * TimeApiExtensions.SECONDS_PER_MINUTE +
                        intSeconds
                ))
        }
        // no match, try TemporalAmountAdapter
        return TemporalAmountAdapter.parse(durationStr).duration
    }

}