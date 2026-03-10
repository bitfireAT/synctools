/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.exception.InvalidICalendarException
import at.bitfire.synctools.icalendar.propertyListOf
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtStart
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.temporal.Temporal
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class StartTimeBuilderTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")

    private val builder = StartTimeBuilder()

    @Test(expected = InvalidICalendarException::class)
    fun `No start time`() {
        val result = Entity(ContentValues())
        val event = VEvent()
        builder.build(event, event, result)
    }

    @Test
    fun `All-day event`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart<Temporal>("20251010")
        ))
        builder.build(event, event, result)
        assertEquals(1760054400000, result.entityValues.get(Events.DTSTART))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_TIMEZONE))
    }

    @Test
    fun `Non-all-day event (floating DTSTART)`() {
        withDefaultTimeZone(tzVienna) {
            val result = Entity(ContentValues())
            val event = VEvent(propertyListOf(
                DtStart<Temporal>("20251010T010203")
            ))
            builder.build(event, event, result)
            assertEquals(1760050923000L, result.entityValues.get(Events.DTSTART))
            assertEquals("Europe/Vienna", result.entityValues.get(Events.EVENT_TIMEZONE))
        }
    }

    @Test
    fun `Non-all-day event (UTC DTSTART)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart<Temporal>("20251010T010203Z")
        ))
        builder.build(event, event, result)
        assertEquals(1760058123000L, result.entityValues.get(Events.DTSTART))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_TIMEZONE))
    }

    @Test
    fun `Non-all-day event (zoned DTSTART)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart<Temporal>(LocalDateTime.parse("2025-10-10T01:02:03").atZone(tzVienna.toZoneId()))
        ))
        builder.build(event, event, result)
        assertEquals(1760050923000, result.entityValues.get(Events.DTSTART))
        assertEquals("Europe/Vienna", result.entityValues.get(Events.EVENT_TIMEZONE))
    }

}

private inline fun withDefaultTimeZone(timeZone: TimeZone, block: () -> Unit) {
    val originalTimeZone = TimeZone.getDefault()
    try {
        TimeZone.setDefault(timeZone)

        block()
    } finally {
        TimeZone.setDefault(originalTimeZone)
    }
}