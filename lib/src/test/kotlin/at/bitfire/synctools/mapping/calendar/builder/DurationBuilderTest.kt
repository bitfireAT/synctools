/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
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
import java.util.LinkedList

@RunWith(RobolectricTestRunner::class)
class DurationBuilderTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")

    private val builder = DurationBuilder()

    @Test
    fun `Not a main event`() {
        val result = Entity(ContentValues())
        builder.build(Event(
            dtStart = DtStart(Date("20251010")),
            dtEnd = DtEnd(Date("20251011")),
            rRules = LinkedList<RRule>().apply {
                add(RRule("FREQ=DAILY;COUNT=5"))
            }
        ), Event(), result)
        assertTrue(result.entityValues.containsKey(Events.DURATION))
        assertNull(result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Not a recurring event`() {
        val result = Entity(ContentValues())
        val event = Event(
            dtStart = DtStart(Date("20251010")),
            duration = Duration(Period.ofDays(2))
        )
        builder.build(event, event, result)
        assertTrue(result.entityValues.containsKey(Events.DURATION))
        assertNull(result.entityValues.get(Events.DURATION))
    }


    @Test
    fun `Recurring all-day event (with DURATION)`() {
        val result = Entity(ContentValues())
        val event = Event(
            dtStart = DtStart(Date("20251010")),
            duration = Duration(Period.ofDays(3)),
            rRules = LinkedList<RRule>().apply {
                add(RRule("FREQ=DAILY;COUNT=5"))
            }
        )
        builder.build(event, event, result)
        assertEquals("P3D", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring non-all-day event (with DURATION)`() {
        val result = Entity(ContentValues())
        val event = Event(
            dtStart = DtStart(DateTime("20251010T010203", tzVienna)),
            duration = Duration(java.time.Duration.ofMinutes(90)),
            rRules = LinkedList<RRule>().apply {
                add(RRule("FREQ=DAILY;COUNT=5"))
            }
        )
        builder.build(event, event, result)
        assertEquals("PT1H30M", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring all-day event (with DTEND)`() {
        val result = Entity(ContentValues())
        val event = Event(
            dtStart = DtStart(Date("20251010")),
            dtEnd = DtEnd(Date("20251017")),
            rRules = LinkedList<RRule>().apply {
                add(RRule("FREQ=DAILY;COUNT=5"))
            }
        )
        builder.build(event, event, result)
        assertEquals("P1W", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring non-all-day event (with DTEND)`() {
        val result = Entity(ContentValues())
        val event = Event(
            dtStart = DtStart(DateTime("20251010T010203", tzVienna)),
            dtEnd = DtEnd(DateTime("20251011T020304", tzVienna)),
            rRules = LinkedList<RRule>().apply {
                add(RRule("FREQ=DAILY;COUNT=5"))
            }
        )
        builder.build(event, event, result)
        assertEquals("P1DT1H1M1S", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring all-day event (neither DURATION nor DTEND)`() {
        val result = Entity(ContentValues())
        val event = Event(
            dtStart = DtStart(Date("20251010")),
            rRules = LinkedList<RRule>().apply {
                add(RRule("FREQ=DAILY;COUNT=5"))
            }
        )
        builder.build(event, event, result)
        assertEquals("P1D", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring non-all-day event (neither DURATION nor DTEND)`() {
        val result = Entity(ContentValues())
        val event = Event(
            dtStart = DtStart(DateTime("20251010T010203", tzVienna)),
            rRules = LinkedList<RRule>().apply {
                add(RRule("FREQ=DAILY;COUNT=5"))
            }
        )
        builder.build(event, event, result)
        assertEquals("PT0S", result.entityValues.get(Events.DURATION))
    }


    @Test
    fun `calculateFromDtEnd (dtStart=DATE, DtEnd=DATE)`() {
        val result = builder.calculateFromDtEnd(
            DtStart(Date("20240328")),
            DtEnd(Date("20240330"))
        )
        assertEquals(
            Duration(Period.ofDays(2)),
            result
        )
    }

    @Test
    fun `calculateFromDtEnd (dtStart=DATE, DtEnd=DATE-TIME)`() {
        val result = builder.calculateFromDtEnd(
            DtStart(Date("20240328")),
            DtEnd(DateTime("20240330T123412", tzVienna))
        )
        assertEquals(
            Duration(Period.ofDays(2)),
            result
        )
    }

    @Test
    fun `calculateFromDtEnd (dtStart=DATE-TIME, DtEnd=DATE)`() {
        val result = builder.calculateFromDtEnd(
            DtStart(DateTime("20240328T010203", tzVienna)),
            DtEnd(Date("20240330"))
        )
        assertEquals(
            Duration(Period.ofDays(2)),
            result
        )
    }

    @Test
    fun `calculateFromDtEnd (dtStart=DATE-TIME, DtEnd=DATE-TIME)`() {
        val result = builder.calculateFromDtEnd(
            DtStart(DateTime("20240728T010203", tzVienna)),
            DtEnd(DateTime("20240728T010203Z"))     // GMT+1 with DST → 2 hours difference
        )
        assertEquals(
            Duration(java.time.Duration.ofHours(2)),
            result
        )
    }

}