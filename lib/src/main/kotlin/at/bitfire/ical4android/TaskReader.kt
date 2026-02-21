/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.ical4android.ICalendar.Companion.fromReader
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.synctools.exception.InvalidICalendarException
import at.bitfire.synctools.icalendar.Css3Color
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.DateTime
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
    }

}