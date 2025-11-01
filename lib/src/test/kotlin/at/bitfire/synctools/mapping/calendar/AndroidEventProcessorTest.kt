/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.Entity
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidEventProcessorTest {

    private val processor = AndroidEventProcessor(
        accountName = "account@example.com",
        prodIdGenerator = DefaultProdIdGenerator(javaClass.simpleName)
    )

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzShanghai = tzRegistry.getTimeZone("Asia/Shanghai")!!
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")!!


    // mapToVEvents → MappingResult.associatedEvents

    @Test
    fun `mapToVEvents processes exceptions`() {
        val result = processor.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.TITLE to "Recurring non-all-day event with exception",
                    Events.DTSTART to 1594056600000L,
                    Events.EVENT_TIMEZONE to tzVienna.id,
                    Events.ALL_DAY to 0,
                    Events.RRULE to "FREQ=DAILY;COUNT=10"
                )),
                exceptions = listOf(
                    Entity(contentValuesOf(
                        Events.ORIGINAL_INSTANCE_TIME to 1594143000000L,
                        Events.ORIGINAL_ALL_DAY to 0,
                        Events.DTSTART to 1594038600000L,
                        Events.EVENT_TIMEZONE to tzShanghai.id,
                        Events.ALL_DAY to 0,
                        Events.TITLE to "Event moved to one hour later"
                    ))
                )
            )
        ).associatedEvents
        val main = result.main!!
        assertEquals("Recurring non-all-day event with exception", main.summary.value)
        assertEquals(DtStart("20200706T193000", tzVienna), main.startDate)
        assertEquals("FREQ=DAILY;COUNT=10", main.getProperty<RRule>(Property.RRULE).value)
        val exception = result.exceptions.first()
        assertEquals(RecurrenceId("20200708T013000", tzShanghai), exception.recurrenceId)
        assertEquals(DtStart("20200706T203000", tzShanghai), exception.startDate)
        assertEquals("Event moved to one hour later", exception.summary.value)
    }

    @Test
    fun `mapToVEvents ignores exception when there's only one invalid RRULE`() {
        val result = processor.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.TITLE to "Factically non-recurring non-all-day event with exception",
                    Events.DTSTART to 1594056600000L,
                    Events.EVENT_TIMEZONE to tzVienna.id,
                    Events.ALL_DAY to 0,
                    Events.RRULE to "FREQ=DAILY;UNTIL=20200706T173000Z"
                )),
                exceptions = listOf(
                    Entity(contentValuesOf(
                        Events.ORIGINAL_INSTANCE_TIME to 1594143000000L,
                        Events.ORIGINAL_ALL_DAY to 0,
                        Events.DTSTART to 1594038600000L,
                        Events.EVENT_TIMEZONE to tzShanghai.id,
                        Events.ALL_DAY to 0,
                        Events.TITLE to "Event moved to one hour later"
                    ))
                )
            )
        ).associatedEvents
        val main = result.main!!
        assertEquals("Factically non-recurring non-all-day event with exception", main.summary.value)
        assertEquals(DtStart("20200706T193000", tzVienna), main.startDate)
        assertTrue(main.getProperties<RRule>(Property.RRULE).isEmpty())
        assertTrue(result.exceptions.isEmpty())
    }

    @Test
    fun `mapToVEvents rewrites cancelled exception to EXDATE`() {
        val result = processor.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.TITLE to "Recurring all-day event with cancelled exception",
                    Events.DTSTART to 1594056600000L,
                    Events.EVENT_TIMEZONE to tzVienna.id,
                    Events.ALL_DAY to 0,
                    Events.RRULE to "FREQ=DAILY;COUNT=10"
                )),
                exceptions = listOf(
                    Entity(contentValuesOf(
                        Events.ORIGINAL_INSTANCE_TIME to 1594143000000L,
                        Events.ORIGINAL_ALL_DAY to 0,
                        Events.DTSTART to 1594143000000L,
                        Events.ALL_DAY to 0,
                        Events.EVENT_TIMEZONE to tzShanghai.id,
                        Events.STATUS to Events.STATUS_CANCELED
                    ))
                )
            )
        ).associatedEvents
        val main = result.main!!
        assertEquals("Recurring all-day event with cancelled exception", main.summary.value)
        assertEquals(DtStart("20200706T193000", tzVienna), main.startDate)
        assertEquals("FREQ=DAILY;COUNT=10", main.getProperty<RRule>(Property.RRULE).value)
        assertEquals(DateTime("20200708T013000", tzShanghai), main.getProperty<ExDate>(Property.EXDATE)?.dates?.first())
        assertTrue(result.exceptions.isEmpty())
    }

    @Test
    fun `mapToVEvents ignores cancelled exception without RECURRENCE-ID`() {
        val result = processor.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.TITLE to "Recurring all-day event with cancelled exception and no RECURRENCE-ID",
                    Events.DTSTART to 1594056600000L,
                    Events.EVENT_TIMEZONE to tzVienna.id,
                    Events.ALL_DAY to 0,
                    Events.RRULE to "FREQ=DAILY;COUNT=10"
                )),
                exceptions = listOf(
                    Entity(contentValuesOf(
                        Events.ORIGINAL_ALL_DAY to 0,
                        Events.DTSTART to 1594143000000L,
                        Events.ALL_DAY to 0,
                        Events.EVENT_TIMEZONE to tzShanghai.id,
                        Events.STATUS to Events.STATUS_CANCELED
                    ))
                )
            )
        ).associatedEvents
        val main = result.main!!
        assertEquals("Recurring all-day event with cancelled exception and no RECURRENCE-ID", main.summary.value)
        assertEquals(DtStart("20200706T193000", tzVienna), main.startDate)
        assertEquals("FREQ=DAILY;COUNT=10", main.getProperty<RRule>(Property.RRULE).value)
        assertNull(main.getProperty<ExDate>(Property.EXDATE))
        assertTrue(result.exceptions.isEmpty())
    }


    @Test
    fun `mapToVEvents generates DTSTAMP`() {
        val result = processor.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.DTSTART to 1594056600000L
                )),
                exceptions = emptyList()
            )
        ).associatedEvents
        assertNotNull(result.main?.dateStamp?.date)
    }


    @Test
    fun `mapToVEvents generates PRODID (no packages)`() {
        val result = processor.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.DTSTART to 1594056600000L
                )),
                exceptions = emptyList()
            )
        ).associatedEvents
        assertEquals(javaClass.simpleName, result.prodId)
    }

    @Test
    fun `mapToVEvents generates PRODID (two packages)`() {
        val result = processor.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.DTSTART to 1594056600000L,
                    Events.MUTATORS to "pkg1,pkg2"
                )),
                exceptions = emptyList()
            )
        ).associatedEvents
        assertEquals("${javaClass.simpleName} (pkg1, pkg2)", result.prodId)
    }


    // mapToVEvents → MappingResult.uid

    @Test
    fun `mapToVEvents generates UID when necessary`() {
        val result = processor.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.DTSTART to 1594056600000L
                )),
                exceptions = emptyList()
            )
        )
        assertTrue(result.generatedUid)
        assertNotNull(result.uid)
        assertEquals(result.uid, result.associatedEvents.main?.uid?.value)
    }

    @Test
    fun `mapToVEvents takes UID from main event row`() {
        val result = processor.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.DTSTART to 1594056600000L,
                    Events.UID_2445 to "sample-uid"
                )),
                exceptions = emptyList()
            )
        )
        assertFalse(result.generatedUid)
        assertEquals("sample-uid", result.uid)
        assertEquals("sample-uid", result.associatedEvents.main?.uid?.value)
    }

    @Test
    fun `mapToVEvents takes UID from Google Calendar data row`() {
        val result = processor.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.DTSTART to 1594056600000L
                )).apply {
                    addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
                        ExtendedProperties.NAME to EventsContract.EXTNAME_GOOGLE_CALENDAR_UID,
                        ExtendedProperties.VALUE to "sample-uid"
                    ))
                },
                exceptions = emptyList()
            )
        )
        assertFalse(result.generatedUid)
        assertEquals("sample-uid", result.uid)
        assertEquals("sample-uid", result.associatedEvents.main?.uid?.value)
    }

    @Test
    fun `mapToVEvents prefers UID from main event row over Google Calendar data row`() {
        val result = processor.mapToVEvents(
            eventAndExceptions = EventAndExceptions(
                main = Entity(contentValuesOf(
                    Events.DTSTART to 1594056600000L,
                    Events.UID_2445 to "sample-uid"
                )).apply {
                    addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
                        ExtendedProperties.NAME to EventsContract.EXTNAME_GOOGLE_CALENDAR_UID,
                        ExtendedProperties.VALUE to "google-calendar"
                    ))
                },
                exceptions = emptyList()
            )
        )
        assertFalse(result.generatedUid)
        assertEquals("sample-uid", result.uid)
        assertEquals("sample-uid", result.associatedEvents.main?.uid?.value)
    }

}