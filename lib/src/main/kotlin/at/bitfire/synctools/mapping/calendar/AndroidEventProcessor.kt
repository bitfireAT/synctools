/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.AssociatedEvents
import at.bitfire.synctools.mapping.calendar.processor.AccessLevelProcessor
import at.bitfire.synctools.mapping.calendar.processor.AndroidEventFieldProcessor
import at.bitfire.synctools.mapping.calendar.processor.AttendeesProcessor
import at.bitfire.synctools.mapping.calendar.processor.AvailabilityProcessor
import at.bitfire.synctools.mapping.calendar.processor.CategoriesProcessor
import at.bitfire.synctools.mapping.calendar.processor.ColorProcessor
import at.bitfire.synctools.mapping.calendar.processor.DescriptionProcessor
import at.bitfire.synctools.mapping.calendar.processor.DurationProcessor
import at.bitfire.synctools.mapping.calendar.processor.EndTimeProcessor
import at.bitfire.synctools.mapping.calendar.processor.LocationProcessor
import at.bitfire.synctools.mapping.calendar.processor.MutatorsProcessor
import at.bitfire.synctools.mapping.calendar.processor.OrganizerProcessor
import at.bitfire.synctools.mapping.calendar.processor.OriginalInstanceTimeProcessor
import at.bitfire.synctools.mapping.calendar.processor.RecurrenceFieldsProcessor
import at.bitfire.synctools.mapping.calendar.processor.RemindersProcessor
import at.bitfire.synctools.mapping.calendar.processor.SequenceProcessor
import at.bitfire.synctools.mapping.calendar.processor.StartTimeProcessor
import at.bitfire.synctools.mapping.calendar.processor.StatusProcessor
import at.bitfire.synctools.mapping.calendar.processor.TitleProcessor
import at.bitfire.synctools.mapping.calendar.processor.UidProcessor
import at.bitfire.synctools.mapping.calendar.processor.UnknownPropertiesProcessor
import at.bitfire.synctools.mapping.calendar.processor.UrlProcessor
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import java.util.LinkedList

/**
 * Mapper from Android event main + data rows to [VEvent].
 *
 * @param accountName   account name (used to generate self-attendee)
 */
class AndroidEventProcessor(
    private val accountName: String
) {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()

    private val fieldProcessors: Array<AndroidEventFieldProcessor> = arrayOf(
        // event row fields
        MutatorsProcessor(),    // for PRODID – TODO
        UidProcessor(),
        OriginalInstanceTimeProcessor(tzRegistry),
        TitleProcessor(),
        LocationProcessor(),
        StartTimeProcessor(tzRegistry),
        EndTimeProcessor(tzRegistry),
        DurationProcessor(tzRegistry),
        RecurrenceFieldsProcessor(tzRegistry),
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


    fun populate(eventAndExceptions: EventAndExceptions): AssociatedEvents {
        // main event
        val main = populateEvent(
            entity = eventAndExceptions.main,
            main = eventAndExceptions.main
        )

        // Add exceptions of recurring main event
        val rRules = main.getProperties<RRule>(Property.RRULE)
        val rDates = main.getProperties<RDate>(Property.RDATE)
        val exceptions = LinkedList<VEvent>()
        if (rRules.isNotEmpty() || rDates.isNotEmpty()) {
            for (exception in eventAndExceptions.exceptions) {
                // convert exception to Event
                val exceptionEvent = populateEvent(
                    entity = exception,
                    main = eventAndExceptions.main
                )

                // make sure that exception has a RECURRENCE-ID
                val recurrenceId = exceptionEvent.recurrenceId ?: continue

                // generate EXDATE instead of VEVENT with RECURRENCE-ID for cancelled instances
                if (exception.entityValues.getAsInteger(Events.STATUS) == Events.STATUS_CANCELED)
                    main.properties += asExDate(exception, recurrenceId)
                else
                    exceptions += exceptionEvent
            }
        }

        return AssociatedEvents(main, exceptions)
    }

    private fun asExDate(entity: Entity, recurrenceId: RecurrenceId): ExDate {
        val originalAllDay = (entity.entityValues.getAsInteger(Events.ORIGINAL_ALL_DAY) ?: 0) != 0
        val list = DateList(
            if (originalAllDay) Value.DATE else Value.DATE_TIME,
            recurrenceId.timeZone
        )
        list.add(recurrenceId.date)
        return ExDate(list).apply {
            // also set TZ properties of ExDate (not only the list)
            if (!originalAllDay) {
                if (recurrenceId.isUtc)
                    setUtc(true)
                else
                    timeZone = recurrenceId.timeZone
            }
        }
    }

    /**
     * Reads data of an event from the calendar provider, i.e. converts the [entity] values into a [VEvent].
     *
     * @param entity            event row as returned by the calendar provider
     * @param main              main event row as returned by the calendar provider
     *
     * @return generated data object
     */
    private fun populateEvent(entity: Entity, main: Entity): VEvent {
        val vEvent = VEvent()
        for (processor in fieldProcessors)
            processor.process(from = entity, main = main, to = vEvent)
        return vEvent
    }

}