/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import androidx.annotation.IntRange
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.synctools.icalendar.Css3Color
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Categories
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.Comment
import net.fortuna.ical4j.model.property.Completed
import net.fortuna.ical4j.model.property.Created
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.Geo
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.Location
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.PercentComplete
import net.fortuna.ical4j.model.property.Priority
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RelatedTo
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Url
import net.fortuna.ical4j.model.property.Version
import java.io.OutputStream
import java.net.URI
import java.net.URISyntaxException
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Data class representing a task
 *
 * - as it is extracted from an iCalendar or
 * - as it should be generated into an iCalendar.
 */
data class Task(
    var createdAt: Long? = null,
    var lastModified: Long? = null,

    var summary: String? = null,
    var location: String? = null,
    var geoPosition: Geo? = null,
    var description: String? = null,
    var color: Int? = null,
    var url: String? = null,
    var organizer: Organizer? = null,

    @IntRange(from = 0, to = 9)
    var priority: Int = Priority.UNDEFINED.level,

    var classification: Clazz? = null,
    var status: Status? = null,

    var dtStart: DtStart? = null,
    var due: Due? = null,
    var duration: Duration? = null,
    var completedAt: Completed? = null,

    @IntRange(from = 0, to = 100)
    var percentComplete: Int? = null,

    var rRule: RRule? = null,
    val rDates: LinkedList<RDate> = LinkedList(),
    val exDates: LinkedList<ExDate> = LinkedList(),

    val categories: LinkedList<String> = LinkedList(),
    var comment: String? = null,
    var relatedTo: LinkedList<RelatedTo> = LinkedList(),
    val unknownProperties: LinkedList<Property> = LinkedList(),

    val alarms: LinkedList<VAlarm> = LinkedList(),
) : ICalendar() {

    companion object {

        private val logger
            get() = Logger.getLogger(Task::class.java.name)

    }


    /**
     * Generates an iCalendar from the Task.
     *
     * @param os        stream that the iCalendar is written to
     * @param prodId    `PRODID` that identifies the app
     */
    fun write(os: OutputStream, prodId: ProdId) {
        val ical = Calendar()
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
        CalendarOutputter(false).output(ical, os)
    }


    fun isAllDay(): Boolean {
        return  dtStart?.let { DateUtils.isDate(it) } ?:
                due?.let { DateUtils.isDate(it) } ?:
                true
    }

}
