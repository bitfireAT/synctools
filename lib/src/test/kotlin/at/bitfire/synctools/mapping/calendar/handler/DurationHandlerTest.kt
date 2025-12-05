/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import junit.framework.TestCase.assertEquals
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DurationHandlerTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")!!

    private val handler = DurationHandler(tzRegistry)

    // Note: When the calendar provider sets a non-null DURATION, it implies that the event is recurring.

    @Test
    fun `All-day event with all-day duration`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 1,
            Events.DTSTART to 1592733600000L,   // 21/06/2020 10:00 UTC
            Events.DURATION to "P4D"
        ))
        handler.process(entity, entity, result)
        assertEquals(DtEnd(Date("20200625")), result.endDate)
        assertNull(result.duration)
    }

    @Test
    fun `All-day event with negative all-day duration`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 1,
            Events.DTSTART to 1592733600000L,   // 21/06/2020 10:00 UTC
            Events.DURATION to "P-4D"
        ))
        handler.process(entity, entity, result)
        assertEquals(DtEnd(Date("20200625")), result.endDate)
        assertNull(result.duration)
    }

    @Test
    fun `All-day event with non-all-day duration`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 1,
            Events.DTSTART to 1760486400000L,   // Wed Oct 15 2025 00:00:00 GMT+0000
            Events.DURATION to "PT24H"
        ))
        handler.process(entity, entity, result)
        assertEquals(DtEnd(Date("20251016")), result.endDate)
        assertNull(result.duration)
    }

    @Test
    fun `All-day event with negative non-all-day duration`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 1,
            Events.DTSTART to 1760486400000L,   // Wed Oct 15 2025 00:00:00 GMT+0000
            Events.DURATION to "PT-24H"
        ))
        handler.process(entity, entity, result)
        assertEquals(DtEnd(Date("20251016")), result.endDate)
        assertNull(result.duration)
    }

    @Test
    fun `Non-all-day event with all-day duration`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 0,
            Events.DTSTART to 1761433200000L,  // Sun Oct 26 2025 01:00:00 GMT+0200
            Events.EVENT_TIMEZONE to "Europe/Vienna",
            Events.DURATION to "P1D",
        ))
        // DST transition at 03:00, clock is set back to 02:00 → P1D = PT25H
        handler.process(entity, entity, result)
        assertEquals(DtEnd(DateTime("20251027T010000", tzVienna)), result.endDate)
        assertNull(result.duration)
    }

    @Test
    fun `Non-all-day event with negative all-day duration`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 0,
            Events.DTSTART to 1761433200000L,  // Sun Oct 26 2025 01:00:00 GMT+0200
            Events.EVENT_TIMEZONE to "Europe/Vienna",
            Events.DURATION to "P-1D",
        ))
        // DST transition at 03:00, clock is set back to 02:00 → P1D = PT25H
        handler.process(entity, entity, result)
        assertEquals(DtEnd(DateTime("20251027T010000", tzVienna)), result.endDate)
        assertNull(result.duration)
    }

    @Test
    fun `Non-all-day event with non-all-day duration`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 0,
            Events.DTSTART to 1761433200000L,  // Sun Oct 26 2025 01:00:00 GMT+0200
            Events.EVENT_TIMEZONE to "Europe/Vienna",
            Events.DURATION to "PT24H"
        ))
        // DST transition at 03:00, clock is set back to 02:00 → PT24H goes one hour back
        handler.process(entity, entity, result)
        assertEquals(DtEnd(DateTime("20251027T000000", tzVienna)), result.endDate)
        assertNull(result.duration)
    }

    @Test
    fun `Non-all-day event with negative non-all-day duration`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 0,
            Events.DTSTART to 1761433200000L,  // Sun Oct 26 2025 01:00:00 GMT+0200
            Events.EVENT_TIMEZONE to "Europe/Vienna",
            Events.DURATION to "PT-24H"     // will be converted to PT24H
        ))
        // DST transition at 03:00, clock is set back to 02:00 → PT24H goes one hour back
        handler.process(entity, entity, result)
        assertEquals(DtEnd(DateTime("20251027T000000", tzVienna)), result.endDate)
        assertNull(result.duration)
    }


    // skip conditions

    @Test
    fun `Skip if DTSTART is not set`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DURATION to "PT1H"
        ))
        handler.process(entity, entity, result)
        assertNull(result.duration)
    }

    @Test
    fun `Skip if DTEND and DURATION are set`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1761433200000L,
            Events.DTEND to 1761433200000L,
            Events.DURATION to "P1D"
        ))
        handler.process(entity, entity, result)
        assertNull(result.duration)
    }

    @Test
    fun `Skip if DURATION is not set`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1761433200000L,  // Sun Oct 26 2025 01:00:00 GMT+0200
            Events.EVENT_TIMEZONE to "Europe/Vienna"
        ))
        handler.process(entity, entity, result)
        assertNull(result.endDate)
        assertNull(result.duration)
    }

}