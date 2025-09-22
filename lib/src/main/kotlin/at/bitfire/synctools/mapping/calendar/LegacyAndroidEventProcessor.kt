/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.synctools.mapping.calendar.processor.AccessLevelProcessor
import at.bitfire.synctools.mapping.calendar.processor.AndroidEventFieldProcessor
import at.bitfire.synctools.mapping.calendar.processor.AttendeesProcessor
import at.bitfire.synctools.mapping.calendar.processor.AvailabilityProcessor
import at.bitfire.synctools.mapping.calendar.processor.CategoriesProcessor
import at.bitfire.synctools.mapping.calendar.processor.ColorProcessor
import at.bitfire.synctools.mapping.calendar.processor.DescriptionProcessor
import at.bitfire.synctools.mapping.calendar.processor.LocationProcessor
import at.bitfire.synctools.mapping.calendar.processor.MutatorsProcessor
import at.bitfire.synctools.mapping.calendar.processor.OrganizerProcessor
import at.bitfire.synctools.mapping.calendar.processor.RecurrenceFieldsProcessor
import at.bitfire.synctools.mapping.calendar.processor.RemindersProcessor
import at.bitfire.synctools.mapping.calendar.processor.SequenceProcessor
import at.bitfire.synctools.mapping.calendar.processor.StatusProcessor
import at.bitfire.synctools.mapping.calendar.processor.TimeFieldsProcessor
import at.bitfire.synctools.mapping.calendar.processor.TitleProcessor
import at.bitfire.synctools.mapping.calendar.processor.UidProcessor
import at.bitfire.synctools.mapping.calendar.processor.UnknownPropertiesProcessor
import at.bitfire.synctools.mapping.calendar.processor.UrlProcessor
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Status
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Legacy mapper from Android event main + data rows to an [Event]
 * (former "populate..." methods).
 *
 * Important: To use recurrence exceptions, you MUST set _SYNC_ID and ORIGINAL_SYNC_ID
 * in populateEvent() / buildEvent. Setting _ID and ORIGINAL_ID is not sufficient.
 *
 * @param accountName   account name (used to generate self-attendee)
 */
class LegacyAndroidEventProcessor(
    private val accountName: String
) {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val fieldProcessors: Array<AndroidEventFieldProcessor> = arrayOf(
        // event row fields
        MutatorsProcessor(),    // for PRODID
        UidProcessor(),
        TitleProcessor(),
        LocationProcessor(),
        TimeFieldsProcessor(),
        RecurrenceFieldsProcessor(),
        DescriptionProcessor(),
        ColorProcessor(),
        AccessLevelProcessor(),
        AvailabilityProcessor(),
        StatusProcessor(),
        // scheduling
        SequenceProcessor(),
        OrganizerProcessor(),
        AttendeesProcessor(),
        // extended properties
        CategoriesProcessor(),
        UnknownPropertiesProcessor(),
        UrlProcessor(),
        // sub-components
        RemindersProcessor(accountName)
    )


    fun populate(eventAndExceptions: EventAndExceptions, to: Event) {
        populateEvent(
            entity = eventAndExceptions.main,
            main = eventAndExceptions.main,
            to = to
        )
        populateExceptions(
            exceptions = eventAndExceptions.exceptions,
            main = eventAndExceptions.main,
            originalAllDay = DateUtils.isDate(to.dtStart),
            to = to
        )
    }

    /**
     * Reads data of an event from the calendar provider, i.e. converts the [entity] values into
     * an [Event] data object.
     *
     * @param entity            event row as returned by the calendar provider
     * @param main              main event row as returned by the calendar provider
     * @param to                destination data object
     */
    private fun populateEvent(entity: Entity, main: Entity, to: Event) {
        // legacy processors
        val hasAttendees = entity.subValues.any { it.uri == Attendees.CONTENT_URI }
        populateEventRow(entity.entityValues, groupScheduled = hasAttendees, to = to)

        // new processors
        for (processor in fieldProcessors)
            processor.process(from = entity, main = main, to = to)
    }

    private fun populateEventRow(row: ContentValues, groupScheduled: Boolean, to: Event) {
        logger.log(Level.FINE, "Read event entity from calender provider", row)

        // exceptions from recurring events
        row.getAsLong(Events.ORIGINAL_INSTANCE_TIME)?.let { originalInstanceTime ->
            val originalAllDay = (row.getAsInteger(Events.ORIGINAL_ALL_DAY) ?: 0) != 0
            val originalDate =
                if (originalAllDay)
                    Date(originalInstanceTime)
                else
                    DateTime(originalInstanceTime)
            if (originalDate is DateTime) {
                to.dtStart?.let { dtStart ->
                    if (dtStart.isUtc)
                        originalDate.isUtc = true
                    else if (dtStart.timeZone != null)
                        originalDate.timeZone = dtStart.timeZone
                }
            }
            to.recurrenceId = RecurrenceId(originalDate)
        }
    }

    private fun populateExceptions(exceptions: List<Entity>, main: Entity, originalAllDay: Boolean, to: Event) {
        for (exception in exceptions) {
            val exceptionEvent = Event()

            // convert exception row to Event
            populateEvent(exception, main, to = exceptionEvent)

            // exceptions are required to have a RECURRENCE-ID
            val recurrenceId = exceptionEvent.recurrenceId ?: continue

            // generate EXDATE instead of RECURRENCE-ID exceptions for cancelled instances
            if (exceptionEvent.status == Status.VEVENT_CANCELLED) {
                val list = DateList(
                    if (originalAllDay) Value.DATE else Value.DATE_TIME,
                    recurrenceId.timeZone
                )
                list.add(recurrenceId.date)
                to.exDates += ExDate(list).apply {
                    // also set TZ properties of ExDate (not only the list)
                    if (!originalAllDay) {
                        if (recurrenceId.isUtc)
                            setUtc(true)
                        else
                            timeZone = recurrenceId.timeZone
                    }
                }

            } else /* exceptionEvent.status != Status.VEVENT_CANCELLED */ {
                // add exception to list of exceptions
                to.exceptions += exceptionEvent
            }
        }
    }

}