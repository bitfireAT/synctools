/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.ical4android.ICalendar.Companion.minifyVTimeZone
import at.bitfire.ical4android.ICalendar.Companion.softValidate
import at.bitfire.ical4android.ICalendar.Companion.withUserAgents
import at.bitfire.synctools.icalendar.Css3Color
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Categories
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.Comment
import net.fortuna.ical4j.model.property.Created
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.Location
import net.fortuna.ical4j.model.property.PercentComplete
import net.fortuna.ical4j.model.property.Priority
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Url
import net.fortuna.ical4j.model.property.Version
import java.io.Writer
import java.net.URI
import java.net.URISyntaxException
import java.util.logging.Level
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
        TODO("ical4j 4.x")
    }

}