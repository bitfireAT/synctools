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
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
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
import java.time.Period
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class EndTimeBuilderTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzDefault = tzRegistry.getTimeZone(ZoneId.systemDefault().id)
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")

    private val builder = EndTimeBuilder()

    @Test
    fun `Recurring event`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(Date("20251010")),
            DtEnd(Date("20251011")),
            RRule("FREQ=DAILY;COUNT=5")
        ))
        builder.build(event, event, result)
        assertTrue(result.entityValues.containsKey(Events.DTEND))
        assertNull(result.entityValues.get(Events.DTEND))
    }


    @Test
    fun `Non-recurring all-day event (with DTEND)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(Date("20251010")),
            DtEnd(Date("20251011"))
        ))
        builder.build(event, event, result)
        assertEquals(1760140800000, result.entityValues.get(Events.DTEND))
    }

    @Test
    fun `Non-recurring non-all-day event (with floating DTEND)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(DateTime("20251010T010203", tzVienna)),
            DtEnd(DateTime("20251011T040506"))
        ))
        builder.build(event, event, result)
        assertEquals(DateTime("20251011T040506", tzDefault).time, result.entityValues.get(Events.DTEND))
    }

    @Test
    fun `Non-recurring non-all-day event (with UTC DTEND)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(DateTime("20251010T010203", tzVienna)),
            DtEnd(DateTime("20251011T040506Z"))
        ))
        builder.build(event, event, result)
        assertEquals(1760155506000, result.entityValues.get(Events.DTEND))
    }

    @Test
    fun `Non-recurring non-all-day event (with zoned DTEND)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(DateTime("20251010T010203", tzVienna)),
            DtEnd(DateTime("20251011T040506", tzVienna))
        ))
        builder.build(event, event, result)
        assertEquals(1760148306000, result.entityValues.get(Events.DTEND))
    }

    @Test
    fun `Non-recurring all-day event (with DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(Date("20251010")),
            Duration(Period.ofDays(3))
        ))
        builder.build(event, event, result)
        assertEquals(1760313600000, result.entityValues.get(Events.DTEND))
    }

    @Test
    fun `Non-recurring non-all-day event (with DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(DateTime("20251010T010203", tzVienna)),
            Duration(java.time.Duration.ofMinutes(90))
        ))
        builder.build(event, event, result)
        assertEquals(1760056323000, result.entityValues.get(Events.DTEND))
    }

    @Test
    fun `Non-recurring all-day event (neither DTEND nor DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(Date("20251010"))
        ))
        builder.build(event, event, result)
        // default duration 1 day
        assertEquals(1760140800000, result.entityValues.get(Events.DTEND))
    }

    @Test
    fun `Non-recurring non-all-day event (neither DTEND nor DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(DateTime("20251010T010203", tzVienna))
        ))
        builder.build(event, event, result)
        // default duration 0 seconds
        assertEquals(1760050923000, result.entityValues.get(Events.DTEND))
    }


    @Test
    fun `alignWithDtStart(dtEnd=DATE, dtStart=DATE)`() {
        val result = builder.alignWithDtStart(
            DtEnd(Date("20251007")),
            DtStart(Date("20250101"))
        )
        assertEquals(DtEnd(Date("20251007")), result)
    }

    @Test
    fun `alignWithDtStart(dtEnd=DATE, dtStart=DATE-TIME`() {
        val result = builder.alignWithDtStart(
            DtEnd(Date("20251007")),
            DtStart(DateTime("20250101T005623", tzVienna))
        )
        assertEquals(DtEnd(DateTime("20251007T005623", tzVienna)), result)
    }

    @Test
    fun `alignWithDtStart(dtEnd=DATE-TIME, dtStart=DATE)`() {
        val result = builder.alignWithDtStart(
            DtEnd(DateTime("20251007T010203Z")),
            DtStart(Date("20250101"))
        )
        assertEquals(DtEnd(Date("20251007")), result)
    }

    @Test
    fun `alignWithDtStart(dtEnd=DATE-TIME, dtStart=DATE-TIME)`() {
        val result = builder.alignWithDtStart(
            DtEnd(DateTime("20251007T010203Z")),
            DtStart(DateTime("20250101T045623", tzVienna))
        )
        assertEquals(DtEnd(DateTime("20251007T010203Z")), result)
    }


    @Test
    fun `calculateFromDefault (DATE)`() {
        assertEquals(
            DtEnd(Date("20251101")),
            builder.calculateFromDefault(DtStart(Date("20251031")))
        )
    }

    @Test
    fun `calculateFromDefault (DATE-TIME)`() {
        val time = DateTime("20251031T123466Z")
        assertEquals(
            DtEnd(time),
            builder.calculateFromDefault(DtStart(time))
        )
    }


    @Test
    fun `calculateFromDuration (dtStart=DATE, duration is date-based)`() {
        val result = builder.calculateFromDuration(
            DtStart(Date("20240228")),
            Duration(null, "P1D")
        )
        assertEquals(
            DtEnd(Date("20240229")),    // leap day
            result
        )
    }

    @Test
    fun `calculateFromDuration (dtStart=DATE, duration is time-based)`() {
        val result = builder.calculateFromDuration(
            DtStart(Date("20241231")),
            Duration(null, "PT25H")
        )
        assertEquals(
            DtEnd(Date("20250101")),
            result
        )
    }

    @Test
    fun `calculateFromDuration (dtStart=DATE-TIME, duration is date-based)`() {
        val result = builder.calculateFromDuration(
            DtStart(DateTime("20250101T045623", tzVienna)),
            Duration(null, "P1D")
        )
        assertEquals(
            DtEnd(DateTime("20250102T045623", tzVienna)),
            result
        )
    }

    @Test
    fun `calculateFromDuration (dtStart=DATE-TIME, duration is time-based)`() {
        val result = builder.calculateFromDuration(
            DtStart(DateTime("20250101T045623", tzVienna)),
            Duration(null, "PT25H")
        )
        assertEquals(
            DtEnd(DateTime("20250102T055623", tzVienna)),
            result
        )
    }

}