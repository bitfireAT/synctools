/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.property.ProdId
import java.io.Writer
import java.util.logging.Logger

/**
 * Writes a [Task] data class to a stream that contains an iCalendar
 * (VCALENDAR with VTODOs and optional VTIMEZONEs).
 *
 * @param prodId    PRODID to use in iCalendar, which identifies DAVx⁵
 */
class TaskWriter(
    private val prodId: ProdId
) {

    private val logger
        get() = Logger.getLogger(TaskWriter::class.java.name)


    /**
     * Generates an iCalendar from the provided Task.
     *
     * @param task  task to write
     * @param to    stream that the iCalendar is written to
     */
    fun write(task: Task, to: Writer): Unit = with(task) {
        TODO()
        /*val ical = Calendar()
        ical.properties += Version.VERSION_2_0
        ical.properties += prodId.withUserAgents(userAgents)

        val vTodo = VToDo(true /* generates DTSTAMP */)
        ical.components += vTodo
        val props = vTodo.properties

        uid?.let { props += Uid(uid) }
        sequence?.let {
            if (it != 0)
                props += Sequence(it)
        }

        createdAt?.let { props += Created(DateTime(it)) }
        lastModified?.let { props += LastModified(DateTime(it)) }

        summary?.let { props += Summary(it) }
        location?.let { props += Location(it) }
        geoPosition?.let { props += it }
        description?.let { props += Description(it) }
        color?.let { props += Color(null, Css3Color.nearestMatch(it).name) }
        url?.let {
            try {
                props += Url(URI(it))
            } catch (e: URISyntaxException) {
                logger.log(Level.WARNING, "Ignoring invalid task URL: $url", e)
            }
        }
        organizer?.let { props += it }

        if (priority != Priority.UNDEFINED.level)
            props += Priority(priority)
        classification?.let { props += it }
        status?.let { props += it }

        rRule?.let { props += it }
        rDates.forEach { props += it }
        exDates.forEach { props += it }

        if (categories.isNotEmpty())
            props += Categories(TextList(categories.toTypedArray()))
        comment?.let { props += Comment(it) }
        props.addAll(relatedTo)
        props.addAll(unknownProperties)

        // remember used time zones
        val usedTimeZones = HashSet<TimeZone>()
        due?.let {
            props += it
            it.timeZone?.let(usedTimeZones::add)
        }
        duration?.let(props::add)
        dtStart?.let {
            props += it
            it.timeZone?.let(usedTimeZones::add)
        }
        completedAt?.let {
            props += it
            it.timeZone?.let(usedTimeZones::add)
        }
        percentComplete?.let { props += PercentComplete(it) }

        if (alarms.isNotEmpty())
            vTodo.components.addAll(alarms)

        // determine earliest referenced date
        val earliest = arrayOf(
            dtStart?.date,
            due?.date,
            completedAt?.date
        ).filterNotNull().minOrNull()
        // add VTIMEZONE components
        for (tz in usedTimeZones)
            ical.components += minifyVTimeZone(tz.vTimeZone, earliest)

        softValidate(ical)
        CalendarOutputter(false).output(ical, to)*/
    }

}