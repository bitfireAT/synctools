/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.synctools.exception.InvalidICalendarException
import at.bitfire.synctools.icalendar.Css3Color
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Status
import java.net.URI
import java.util.LinkedList

/**
 * Data class that represents an event
 *
 * - as it is extracted from an iCalendar or
 * - as it should be generated into an iCalendar.
 */
@Deprecated(
    "Use AssociatedEvents instead",
    replaceWith = ReplaceWith("AssociatedEvents", "at.bitfire.synctools.icalendar"),
    level = DeprecationLevel.WARNING
)
data class Event(
    override var uid: String? = null,
    override var sequence: Int? = null,
    var isOrganizer: Boolean? = null,

    /** list of Calendar User Agents which have edited the event since last sync */
    override var userAgents: LinkedList<String> = LinkedList(),

    // uid and sequence are inherited from iCalendar
    var recurrenceId: RecurrenceId? = null,

    var summary: String? = null,
    var location: String? = null,
    var url: URI? = null,
    var description: String? = null,
    var color: Css3Color? = null,

    var dtStart: DtStart? = null,
    var dtEnd: DtEnd? = null,

    var duration: Duration? = null,
    val rRules: LinkedList<RRule> = LinkedList(),
    val exRules: LinkedList<ExRule> = LinkedList(),
    val rDates: LinkedList<RDate> = LinkedList(),
    val exDates: LinkedList<ExDate> = LinkedList(),

    val exceptions: LinkedList<Event> = LinkedList(),

    var classification: Clazz? = null,
    var status: Status? = null,

    var opaque: Boolean = true,

    var organizer: Organizer? = null,
    val attendees: LinkedList<Attendee> = LinkedList(),

    val alarms: LinkedList<VAlarm> = LinkedList(),

    var lastModified: LastModified? = null,

    val categories: LinkedList<String> = LinkedList(),
    val unknownProperties: LinkedList<Property> = LinkedList()
) : ICalendar() {

    fun requireDtStart(): DtStart =
        dtStart ?: throw InvalidICalendarException("Missing DTSTART in VEVENT")

}