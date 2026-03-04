/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.ical4android.ICalendar.Companion.fromReader
import at.bitfire.synctools.exception.InvalidICalendarException
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VToDo
import java.io.IOException
import java.io.Reader
import java.util.LinkedList
import java.util.logging.Logger

/**
 * Generates a single or list of [Task] from an iCalendar in a [Reader] source.
 */
class TaskReader {

    private val logger
        get() = Logger.getLogger(TaskReader::class.java.name)

    /**
     * Parses an iCalendar resource, applies [at.bitfire.synctools.icalendar.validation.ICalPreprocessor] to increase compatibility
     * and extracts the VTODOs.
     *
     * @param reader where the iCalendar is taken from
     *
     * @return array of filled [Task] data objects (may have size 0)
     *
     * @throws InvalidICalendarException when the iCalendar can't be parsed
     * @throws IOException on I/O errors
     */
    fun readTasks(reader: Reader): List<Task> {
        val ical = fromReader(reader)
        val vToDos = ical.getComponents<VToDo>(Component.VTODO)
        return vToDos.mapTo(LinkedList()) { this.fromVToDo(it) }
    }

    private fun fromVToDo(todo: VToDo): Task {
        TODO("ical4j 4.x")
        /*val t = Task()

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

        return t*/
    }

}