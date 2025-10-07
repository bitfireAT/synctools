/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
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
import at.bitfire.synctools.mapping.calendar.processor.OriginalInstanceTimeProcessor
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
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RecurrenceId

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

    private val fieldProcessors: Array<AndroidEventFieldProcessor> = arrayOf(
        // event row fields
        MutatorsProcessor(),    // for PRODID
        UidProcessor(),
        OriginalInstanceTimeProcessor(),
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
        // main event
        populateEvent(
            entity = eventAndExceptions.main,
            main = eventAndExceptions.main,
            to = to
        )

        // Add exceptions of recurring main event
        if (to.rRules.isNotEmpty() || to.rDates.isNotEmpty()) {
            for (exception in eventAndExceptions.exceptions) {
                val exceptionEvent = Event()

                // convert exception to Event
                populateEvent(
                    entity = exception,
                    main = eventAndExceptions.main,
                    to = exceptionEvent
                )

                // make sure that exception has a RECURRENCE-ID
                val recurrenceId = exceptionEvent.recurrenceId ?: continue

                // generate EXDATE instead of VEVENT with RECURRENCE-ID for cancelled instances
                if (exception.entityValues.getAsInteger(Events.STATUS) == Events.STATUS_CANCELED)
                    addAsExDate(exception, recurrenceId, to = to)
                else
                    to.exceptions += exceptionEvent
            }
        }
    }

    private fun addAsExDate(entity: Entity, recurrenceId: RecurrenceId, to: Event) {
        val originalAllDay = (entity.entityValues.getAsInteger(Events.ORIGINAL_ALL_DAY) ?: 0) != 0
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
        // new processors
        for (processor in fieldProcessors)
            processor.process(from = entity, main = main, to = to)
    }

}