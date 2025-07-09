/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.ical4android.ICalendar.Companion.minifyVTimeZone
import at.bitfire.ical4android.ICalendar.Companion.softValidate
import at.bitfire.ical4android.ICalendar.Companion.withUserAgents
import at.bitfire.ical4android.util.DateUtils.isDateTime
import at.bitfire.ical4android.validation.EventValidator
import at.bitfire.synctools.exception.InvalidLocalResourceException
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Categories
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.Location
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Transp
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Url
import net.fortuna.ical4j.model.property.Version
import java.io.OutputStream
import java.util.logging.Logger

/**
 * Writes an [Event] data class to a stream that contains an iCalendar
 * (VCALENDAR with VEVENTs and optional VTIMEZONEs).
 *
 * @param prodId    PRODID to use in iCalendar
 */
class EventWriter(
    private val prodId: ProdId
) {

    private val logger: Logger
        get() = Logger.getLogger(javaClass.name)


    /**
     * Applies error correction over [EventValidator] to an [Event] and generates an iCalendar from it.
     *
     * @param event     event to generate iCalendar from
     * @param os        stream that the iCalendar is written to
     */
    fun write(event: Event, os: OutputStream) {
        val ical = Calendar()
        ical.properties += Version.VERSION_2_0
        ical.properties += prodId.withUserAgents(event.userAgents)

        val dtStart = event.dtStart ?: throw InvalidLocalResourceException("Won't generate event without start time")

        EventValidator.repair(event)     // repair this event before creating the VEVENT

        // "main event" (without exceptions)
        val components = ical.components
        val mainEvent = toVEvent(event)
        components += mainEvent

        // remember used time zones
        val usedTimeZones = mutableSetOf<TimeZone>()
        dtStart.timeZone?.let(usedTimeZones::add)
        event.dtEnd?.timeZone?.let(usedTimeZones::add)

        // recurrence exceptions
        for (exception in event.exceptions) {
            // exceptions must always have the same UID as the main event
            exception.uid = event.uid

            val recurrenceId = exception.recurrenceId
            if (recurrenceId == null) {
                logger.warning("Ignoring exception without recurrenceId")
                continue
            }

            /* Exceptions must always have the same value type as DTSTART [RFC 5545 3.8.4.4].
               If this is not the case, we don't add the exception to the event because we're
               strict in what we send (and servers may reject such a case).
             */
            if (isDateTime(recurrenceId) != isDateTime(dtStart)) {
                logger.warning("Ignoring exception $recurrenceId with other date type than dtStart: $dtStart")
                continue
            }

            // for simplicity and compatibility, rewrite date-time exceptions to the same time zone as DTSTART
            if (isDateTime(recurrenceId) && recurrenceId.timeZone != dtStart.timeZone) {
                logger.fine("Changing timezone of $recurrenceId to same time zone as dtStart: $dtStart")
                recurrenceId.timeZone = dtStart.timeZone
            }

            // create and add VEVENT for exception
            val vException = toVEvent(exception)
            components += vException

            // remember used time zones
            exception.dtStart?.timeZone?.let(usedTimeZones::add)
            exception.dtEnd?.timeZone?.let(usedTimeZones::add)
        }

        // determine first dtStart (there may be exceptions with an earlier DTSTART that the main event)
        val dtStarts = mutableListOf(dtStart.date)
        dtStarts.addAll(event.exceptions.mapNotNull { it.dtStart?.date })
        val earliest = dtStarts.minOrNull()
        // add VTIMEZONE components
        for (tz in usedTimeZones)
            ical.components += minifyVTimeZone(tz.vTimeZone, earliest)

        softValidate(ical)
        CalendarOutputter(false).output(ical, os)
    }

    /**
     * Generates a VEvent representation of this event.
     *
     * @return generated VEvent
     */
    private fun toVEvent(from: Event): VEvent {
        val event = VEvent(/* generates DTSTAMP */)
        val props = event.properties
        props += Uid(from.uid)

        from.recurrenceId?.let { props += it }
        from.sequence?.let {
            if (it != 0)
                props += Sequence(it)
        }

        from.summary?.let { props += Summary(it) }
        from.location?.let { props += Location(it) }
        from.url?.let { props += Url(it) }
        from.description?.let { props += Description(it) }
        from.color?.let { props += Color(null, it.name) }

        from.dtStart?.let { props += it }
        from.dtEnd?.let { props += it }
        from.duration?.let { props += it }

        props.addAll(from.rRules)
        props.addAll(from.rDates)
        props.addAll(from.exRules)
        props.addAll(from.exDates)

        from.classification?.let { props += it }
        from.status?.let { props += it }
        if (!from.opaque)
            props += Transp.TRANSPARENT

        from.organizer?.let { props += it }
        props.addAll(from.attendees)

        if (from.categories.isNotEmpty())
            props += Categories(TextList(from.categories.toTypedArray()))
        props.addAll(from.unknownProperties)

        from.lastModified?.let { props += it }

        event.components.addAll(from.alarms)

        return event
    }

}