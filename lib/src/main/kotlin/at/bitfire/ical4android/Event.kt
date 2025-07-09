/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.ical4android.ICalendar.Companion.CALENDAR_NAME
import at.bitfire.ical4android.validation.EventValidator
import at.bitfire.synctools.icalendar.CalendarUidSplitter
import at.bitfire.synctools.icalendar.Css3Color
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Categories
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.Location
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Transp
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Url
import java.io.IOException
import java.io.Reader
import java.net.URI
import java.util.LinkedList
import java.util.UUID
import java.util.logging.Logger

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

    val organizerEmail: String?
        get() {
            var email: String? = null
            organizer?.let { organizer ->
                val uri = organizer.calAddress
                email = if (uri.scheme.equals("mailto", true))
                    uri.schemeSpecificPart
                else
                    organizer.getParameter<Email>(Parameter.EMAIL)?.value
            }
            return email
        }


    companion object {

        private val logger
            get() = Logger.getLogger(Event::class.java.name)

        /**
         * Parses an iCalendar resource, applies [at.bitfire.synctools.icalendar.validation.ICalPreprocessor]
         * and [EventValidator] to increase compatibility and extracts the VEVENTs.
         *
         * @param reader        where the iCalendar is read from
         * @param properties    Known iCalendar properties (like [CALENDAR_NAME]) will be put into this map. Key: property name; value: property value
         *
         * @return array of filled [Event] data objects (may have size 0)
         *
         * @throws IOException on I/O errors
         * @throws ParserException when the iCalendar can't be parsed
         */
        fun eventsFromReader(
            reader: Reader,
            properties: MutableMap<String, String>? = null
        ): List<Event> {
            val ical = fromReader(reader, properties)

            // process VEVENTs
            val splitter = CalendarUidSplitter<VEvent>()
            val vEventsByUid = splitter.associateByUid(ical, Component.VEVENT)

            /* Note: There may be UIDs which have only RECURRENCE-ID entries and not a main entry (for instance, a recurring
            event with an exception where the current user has been invited only to this exception. In this case,
            the UID will not appear in mainEvents but only in exceptions. */

            // make sure every event has an UID
            vEventsByUid[null]?.let { withoutUid ->
                val uid = Uid(UUID.randomUUID().toString())
                logger.warning("Found VEVENT without UID, using a random one: ${uid.value}")
                withoutUid.main?.properties?.add(uid)
                withoutUid.exceptions.forEach { it.properties.add(uid) }
            }

            // convert into Events (data class)
            val events = mutableListOf<Event>()
            for (associatedEvents in vEventsByUid.values) {
                val mainVEvent = associatedEvents.main ?:
                    // no main event but only exceptions, create fake main event
                    // FIXME: we should construct a proper recurring fake event, not just take first the exception
                    associatedEvents.exceptions.first()

                val event = fromVEvent(mainVEvent)
                associatedEvents.exceptions.mapTo(event.exceptions) { exceptionVEvent ->
                    fromVEvent(exceptionVEvent).also { exception ->
                        // make sure that exceptions have at least a SUMMARY (if the main event does have one)
                        if (exception.summary == null)
                            exception.summary = event.summary
                    }
                }

                events += event
            }

            // Try to repair all events after reading the whole iCalendar
            for (event in events)
                EventValidator.repair(event)

            return events
        }

        fun fromVEvent(event: VEvent): Event {
            val e = Event()

            // sequence must only be null for locally created, not-yet-synchronized events
            e.sequence = 0

            // process properties
            for (prop in event.properties)
                when (prop) {
                    is Uid -> e.uid = prop.value
                    is RecurrenceId -> e.recurrenceId = prop
                    is Sequence -> e.sequence = prop.sequenceNo
                    is Summary -> e.summary = prop.value
                    is Location -> e.location = prop.value
                    is Url -> e.url = prop.uri
                    is Description -> e.description = prop.value
                    is Categories ->
                        for (category in prop.categories)
                            e.categories += category

                    is Color -> e.color = Css3Color.fromString(prop.value)
                    is DtStart -> e.dtStart = prop
                    is DtEnd -> e.dtEnd = prop
                    is Duration -> e.duration = prop
                    is RRule -> e.rRules += prop
                    is RDate -> e.rDates += prop
                    is ExRule -> e.exRules += prop
                    is ExDate -> e.exDates += prop
                    is Clazz -> e.classification = prop
                    is Status -> e.status = prop
                    is Transp -> e.opaque = prop == Transp.OPAQUE
                    is Organizer -> e.organizer = prop
                    is Attendee -> e.attendees += prop
                    is LastModified -> e.lastModified = prop
                    is ProdId, is DtStamp -> { /* don't save these as unknown properties */ }

                    else -> e.unknownProperties += prop
                }

            e.alarms.addAll(event.alarms)

            return e
        }
    }

}