/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.propertyListOf
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.RRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.temporal.Temporal

@RunWith(RobolectricTestRunner::class)
class EndTimeBuilderTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")

    private val builder = EndTimeBuilder()

    @Test
    fun `Recurring event`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart<Temporal>("20251010"),
            DtEnd<Temporal>("20251011"),
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        ))
        builder.build(event, event, result)
        assertTrue(result.entityValues.containsKey(Events.DTEND))
        assertNull(result.entityValues.get(Events.DTEND))
    }


    @Test
    fun `Non-recurring all-day event (with DTEND)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart<Temporal>("20251010"),
            DtEnd<Temporal>("20251011")
        ))
        builder.build(event, event, result)
        assertEquals(1760140800000, result.entityValues.get(Events.DTEND))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring all-day event (with DTEND before DTSTART)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart<Temporal>("20251010"),
            DtEnd<Temporal>("20251001")     // before DTSTART, should be ignored
        ))
        builder.build(event, event, result)
        // default duration: one day → 20251011
        assertEquals(1760140800000, result.entityValues.get(Events.DTEND))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring all-day event (with DTEND equals DTSTART)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart<Temporal>("20251010"),
            DtEnd<Temporal>("20251010")     // equals DTSTART, should be ignored
        ))
        builder.build(event, event, result)
        // default duration: one day → 20251011
        assertEquals(1760140800000, result.entityValues.get(Events.DTEND))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring non-all-day event (with floating DTEND)`() {
        withDefaultTimeZone(tzVienna) {
            val result = Entity(ContentValues())
            val event = VEvent(
                propertyListOf(
                    DtStart(LocalDateTime.parse("2025-10-10T01:02:03").atZone(tzVienna.toZoneId())),
                    DtEnd<Temporal>("20251011T040506")
                )
            )
            builder.build(event, event, result)
            assertEquals(1760148306000L, result.entityValues.get(Events.DTEND))
            assertEquals("Europe/Vienna", result.entityValues.get(Events.EVENT_END_TIMEZONE))
        }
    }

    @Test
    fun `Non-recurring non-all-day event (with UTC DTEND)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(LocalDateTime.parse("2025-10-10T01:02:03").atZone(tzVienna.toZoneId())),
            DtEnd<Temporal>("20251011T040506Z")
        ))
        builder.build(event, event, result)
        assertEquals(1760155506000, result.entityValues.get(Events.DTEND))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring non-all-day event (with zoned DTEND)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(LocalDateTime.parse("2025-10-10T01:02:03").atZone(tzVienna.toZoneId())),
            DtEnd(LocalDateTime.parse("2025-10-11T04:05:06").atZone(tzVienna.toZoneId()))
        ))
        builder.build(event, event, result)
        assertEquals(1760148306000, result.entityValues.get(Events.DTEND))
        assertEquals("Europe/Vienna", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring non-all-day event (with zoned DTEND before DTSTART)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(LocalDateTime.parse("2025-10-11T04:05:06").atZone(tzVienna.toZoneId())),
            DtEnd(LocalDateTime.parse("2025-10-10T04:05:06").atZone(tzVienna.toZoneId()))    // before DTSTART, should be ignored
        ))
        builder.build(event, event, result)
        // default duration: 0 sec -> DTEND == DTSTART in calendar provider
        assertEquals(1760148306000, result.entityValues.get(Events.DTEND))
        assertEquals("Europe/Vienna", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring non-all-day event (with zoned DTEND equals DTSTART)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(LocalDateTime.parse("2025-10-11T04:05:06").atZone(tzVienna.toZoneId())),
            DtEnd(LocalDateTime.parse("2025-10-11T04:05:06").atZone(tzVienna.toZoneId()))    // equals DTSTART, should be ignored
        ))
        builder.build(event, event, result)
        // default duration: 0 sec -> DTEND == DTSTART in calendar provider
        assertEquals(1760148306000, result.entityValues.get(Events.DTEND))
        assertEquals("Europe/Vienna", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring all-day event (with DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart<Temporal>("20251010"),
            Duration(Period.ofDays(3))
        ))
        builder.build(event, event, result)
        assertEquals(1760313600000, result.entityValues.get(Events.DTEND))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring all-day event (with negative DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart<Temporal>("20251010"),
            Duration(Period.ofDays(-3))     // invalid negative DURATION will be treated as positive
        ))
        builder.build(event, event, result)
        assertEquals(1760313600000, result.entityValues.get(Events.DTEND))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring non-all-day event (with DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(LocalDateTime.parse("2025-10-10T01:02:03").atZone(tzVienna.toZoneId())),
            Duration(java.time.Duration.ofMinutes(90))
        ))
        builder.build(event, event, result)
        assertEquals(1760056323000, result.entityValues.get(Events.DTEND))
        assertEquals("Europe/Vienna", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring non-all-day event (with negative DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(LocalDateTime.parse("2025-10-10T01:02:03").atZone(tzVienna.toZoneId())),
            Duration(java.time.Duration.ofMinutes(-90))     // invalid negative DURATION will be treated as positive
        ))
        builder.build(event, event, result)
        assertEquals(1760056323000, result.entityValues.get(Events.DTEND))
        assertEquals("Europe/Vienna", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring all-day event (neither DTEND nor DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart<Temporal>("20251010")
        ))
        builder.build(event, event, result)
        // default duration 1 day
        assertEquals(1760140800000, result.entityValues.get(Events.DTEND))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring non-all-day event (neither DTEND nor DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(LocalDateTime.parse("2025-10-10T01:02:03").atZone(tzVienna.toZoneId()))
        ))
        builder.build(event, event, result)
        // default duration 0 seconds
        assertEquals(1760050923000, result.entityValues.get(Events.DTEND))
        assertEquals("Europe/Vienna", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }


    @Test
    fun `alignWithDtStart(dtEnd=DATE, dtStart=DATE)`() {
        val result = builder.alignWithDtStart(
            DtEnd<Temporal>("20251007"),
            DtStart<Temporal>("20250101")
        )
        assertEquals(DtEnd<Temporal>("20251007"), result)
    }

    @Test
    fun `alignWithDtStart(dtEnd=DATE, dtStart=DATE-TIME`() {
        val result = builder.alignWithDtStart(
            DtEnd<Temporal>("20251007"),
            DtStart(LocalDateTime.parse("2025-01-01T00:56:23").atZone(tzVienna.toZoneId()))
        )
        assertEquals(DtEnd(LocalDateTime.parse("2025-10-07T00:56:23").atZone(tzVienna.toZoneId())), result)
    }

    @Test
    fun `alignWithDtStart(dtEnd=DATE-TIME, dtStart=DATE)`() {
        val result = builder.alignWithDtStart(
            DtEnd<Temporal>("20251007T010203Z"),
            DtStart<Temporal>("20250101")
        )
        assertEquals(DtEnd(LocalDate.parse("2025-10-07")), result)
    }

    @Test
    fun `alignWithDtStart(dtEnd=DATE-TIME, dtStart=DATE-TIME)`() {
        val result = builder.alignWithDtStart(
            DtEnd<Temporal>("20251007T010203Z"),
            DtStart(LocalDateTime.parse("2025-01-01T04:56:23").atZone(tzVienna.toZoneId()))
        )
        assertEquals(DtEnd<Temporal>("20251007T010203Z"), result)
    }


    @Test
    fun `calculateFromDefault (DATE)`() {
        assertEquals(
            DtEnd(LocalDate.parse("2025-11-01")),
            builder.calculateFromDefault(DtStart<Temporal>("20251031"))
        )
    }

    @Test
    fun `calculateFromDefault (DATE-TIME)`() {
        val time = Instant.parse("2025-10-31T12:34:56Z")
        assertEquals(
            DtEnd(time),
            builder.calculateFromDefault(DtStart(time))
        )
    }


    @Test
    fun `calculateFromDuration (dtStart=DATE, duration is date-based)`() {
        val result = builder.calculateFromDuration(
            DtStart<Temporal>("20240228"),
            java.time.Duration.ofDays(1)
        )
        assertEquals(
            DtEnd(LocalDate.parse("2024-02-29")),    // leap day
            result
        )
    }

    @Test
    fun `calculateFromDuration (dtStart=DATE, duration is time-based)`() {
        val result = builder.calculateFromDuration(
            DtStart<Temporal>("20241231"),
            java.time.Duration.ofHours(25)
        )
        assertEquals(
            DtEnd(LocalDate.parse("2025-01-01")),
            result
        )
    }

    @Test
    fun `calculateFromDuration (dtStart=DATE-TIME, duration is date-based)`() {
        val result = builder.calculateFromDuration(
            DtStart(LocalDateTime.parse("2025-01-01T04:56:23").atZone(tzVienna.toZoneId())),
            java.time.Duration.ofDays(1)
        )
        assertEquals(
            DtEnd(LocalDateTime.parse("2025-01-02T04:56:23").atZone(tzVienna.toZoneId())),
            result
        )
    }

    @Test
    fun `calculateFromDuration (dtStart=DATE-TIME, duration is time-based)`() {
        val result = builder.calculateFromDuration(
            DtStart(LocalDateTime.parse("2025-01-01T04:56:23").atZone(tzVienna.toZoneId())),
            java.time.Duration.ofHours(25)
        )
        assertEquals(
            DtEnd(LocalDateTime.parse("2025-01-02T05:56:23").atZone(tzVienna.toZoneId())),
            result
        )
    }

    @Test
    fun `calculateFromDuration (dtStart=DATE-TIME, duration is time-based and negative)`() {
        val result = builder.calculateFromDuration(
            DtStart(LocalDateTime.parse("2025-01-01T04:56:23").atZone(tzVienna.toZoneId())),
            java.time.Duration.ofHours(-25)
        )
        assertEquals(
            DtEnd(LocalDateTime.parse("2025-01-02T05:56:23").atZone(tzVienna.toZoneId())),
            result
        )
    }

}