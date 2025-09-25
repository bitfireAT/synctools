/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LegacyAndroidEventProcessorTest {

    private val processor = AndroidEventProcessor("account@example.com")

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzShanghai = tzRegistry.getTimeZone("Asia/Shanghai")!!
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")!!

    @Test
    fun `Populate regular exception`() {
        val result = processor.process(
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
        )
        val event = result.main!!
        assertEquals("Recurring non-all-day event with exception", event.summary.value)
        assertEquals(DtStart("20200706T193000", tzVienna), event.startDate)
        assertEquals("FREQ=DAILY;COUNT=10", event.getProperty<RRule>(Property.RRULE).value)
        val exception = result.exceptions.first()
        assertEquals(RecurrenceId("20200708T013000", tzShanghai), exception.recurrenceId)
        assertEquals(DtStart("20200706T203000", tzShanghai), exception.startDate)
        assertEquals("Event moved to one hour later", exception.summary.value)
    }

    @Test
    fun `Populate cancelled exception as EXDATE`() {
        val result = processor.process(
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
        )
        val main = result.main!!
        assertEquals("Recurring all-day event with cancelled exception", main.summary.value)
        assertEquals(DtStart("20200706T193000", tzVienna), main.startDate)
        assertEquals("FREQ=DAILY;COUNT=10", main.getProperty<RRule>(Property.RRULE).value)
        assertEquals(DateTime("20200708T013000", tzShanghai), main.getProperty<ExDate>(Property.EXDATE).dates.first())
        assertTrue(result.exceptions.isEmpty())
    }

    @Test
    fun `Populate cancelled exception without RECURRENCE-ID`() {
        val result = processor.process(
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
        )
        val main = result.main!!
        assertEquals("Recurring all-day event with cancelled exception and no RECURRENCE-ID", main.summary.value)
        assertEquals(DtStart("20200706T193000", tzVienna), main.startDate)
        assertEquals("FREQ=DAILY;COUNT=10", main.getProperty<RRule>(Property.RRULE).value)
        assertNull(main.getProperty<ExDate>(Property.EXDATE))
        assertTrue(result.exceptions.isEmpty())
    }

}