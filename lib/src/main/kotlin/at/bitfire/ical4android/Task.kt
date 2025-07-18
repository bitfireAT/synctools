/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import androidx.annotation.IntRange
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.synctools.exception.InvalidRemoteResourceException
import at.bitfire.synctools.icalendar.Css3Color
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
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
import net.fortuna.ical4j.model.property.DtStamp
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
import java.io.IOException
import java.io.OutputStream
import java.io.Reader
import java.net.URI
import java.net.URISyntaxException
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

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

        /**
         * Parses an iCalendar resource, applies [at.bitfire.synctools.icalendar.validation.ICalPreprocessor] to increase compatibility
         * and extracts the VTODOs.
         *
         * @param reader where the iCalendar is taken from
         *
         * @return array of filled [Task] data objects (may have size 0)
         *
         * @throws InvalidRemoteResourceException when the iCalendar can't be parsed
         * @throws IOException on I/O errors
         */
        fun tasksFromReader(reader: Reader): List<Task> {
            val ical = fromReader(reader)
            val vToDos = ical.getComponents<VToDo>(Component.VTODO)
            return vToDos.mapTo(LinkedList()) { this.fromVToDo(it) }
        }

        private fun fromVToDo(todo: VToDo): Task {
            val t = Task()

            if (todo.uid != null)
                t.uid = todo.uid.value
            else {
                logger.warning("Received VTODO without UID, generating new one")
                t.generateUID()
            }

            // sequence must only be null for locally created, not-yet-synchronized events
            t.sequence = 0

            for (prop in todo.properties)
                when (prop) {
                    is Sequence -> t.sequence = prop.sequenceNo
                    is Created -> t.createdAt = prop.dateTime.time
                    is LastModified -> t.lastModified = prop.dateTime.time
                    is Summary -> t.summary = prop.value
                    is Location -> t.location = prop.value
                    is Geo -> t.geoPosition = prop
                    is Description -> t.description = prop.value
                    is Color -> t.color = Css3Color.fromString(prop.value)?.argb
                    is Url -> t.url = prop.value
                    is Organizer -> t.organizer = prop
                    is Priority -> t.priority = prop.level
                    is Clazz -> t.classification = prop
                    is Status -> t.status = prop
                    is Due -> { t.due = prop }
                    is Duration -> t.duration = prop
                    is DtStart -> { t.dtStart = prop }
                    is Completed -> { t.completedAt = prop }
                    is PercentComplete -> t.percentComplete = prop.percentage
                    is RRule -> t.rRule = prop
                    is RDate -> t.rDates += prop
                    is ExDate -> t.exDates += prop
                    is Categories ->
                        for (category in prop.categories)
                            t.categories += category
                    is Comment -> t.comment = prop.value
                    is RelatedTo -> t.relatedTo.add(prop)
                    is Uid, is ProdId, is DtStamp -> { /* don't save these as unknown properties */ }
                    else -> t.unknownProperties += prop
                }

            t.alarms.addAll(todo.alarms)

            // There seem to be many invalid tasks out there because of some defect clients, do some validation.
            val dtStart = t.dtStart
            val due = t.due

            if (dtStart != null && due != null) {
                if (DateUtils.isDate(dtStart) && DateUtils.isDateTime(due)) {
                    logger.warning("DTSTART is DATE but DUE is DATE-TIME, rewriting DTSTART to DATE-TIME")
                    t.dtStart = DtStart(DateTime(dtStart.value, due.timeZone))
                } else if (DateUtils.isDateTime(dtStart) && DateUtils.isDate(due)) {
                    logger.warning("DTSTART is DATE-TIME but DUE is DATE, rewriting DUE to DATE-TIME")
                    t.due = Due(DateTime(due.value, dtStart.timeZone))
                }


                if (due.date <= dtStart.date) {
                    logger.warning("Found invalid DUE <= DTSTART; dropping DTSTART")
                    t.dtStart = null
                }
            }

            if (t.duration != null && t.dtStart == null) {
                logger.warning("Found DURATION without DTSTART; ignoring")
                t.duration = null
            }

            return t
        }

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
