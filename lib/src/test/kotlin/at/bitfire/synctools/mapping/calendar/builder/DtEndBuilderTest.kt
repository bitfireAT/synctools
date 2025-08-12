/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.storage.emptyEntity
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

@RunWith(RobolectricTestRunner::class)
class DtEndBuilderTest {

    private val builder = DtEndBuilder()
    private val tzVienna = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone("Europe/Vienna")

    @Test
    fun `DTEND is DATE (DTSTART is DATE)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(Date()),
                DtEnd(Date(1754919693000)),     // Mon Aug 11 2025 13:41:33 GMT+0000
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(
            1754870400000,  // Mon Aug 11 2025 00:00:00 GMT+0000
            result.entityValues.getAsLong(Events.DTEND))
    }

    @Test
    fun `DTEND is DATE (DTSTART is DATE-TIME)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime(1754919693000)),   // Mon Aug 11 2025 13:41:33 GMT+0000
                DtEnd(Date(1754870400000)),         // Mon Aug 11 2025 00:00:00 GMT+0000
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(
            1754919693000,  // time is amended from DTSTART
            result.entityValues.getAsLong(Events.DTEND))
    }

    @Test
    fun `DTEND is DATE-TIME (DTSTART is DATE)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(Date()),
                DtEnd(DateTime(1754919693000),  // Mon Aug 11 2025 13:41:33 GMT+0000
                ))),
            main = VEvent(),
            to = result
        ))
        assertEquals(
            1754870400000,  // Mon Aug 11 2025 00:00:00 GMT+0000
            result.entityValues.getAsLong(Events.DTEND))
    }

    @Test
    fun `DTEND is DATE-TIME (DTSTART is DATE-TIME)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime()),
                DtEnd(DateTime(1754919693000),  // Mon Aug 11 2025 13:41:33 GMT+0000
            ))),
            main = VEvent(),
            to = result
        ))
        assertEquals(
            1754919693000,
            result.entityValues.getAsLong(Events.DTEND))
    }

    @Test
    fun `DTEND is null, DTSTART (DATE) and DURATION present`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(Date("20250811")),
                Duration(java.time.Duration.parse("PT25H"))
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(
            1754870400000 + 24*3600000,     // 25 hours added but granularity of days is 24 hours
            result.entityValues.getAsLong(Events.DTEND))
    }

    @Test
    fun `DTEND is null, DTSTART (DATE-TIME) and DURATION present`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20250811T154133", tzVienna)),   // Mon Aug 11 2025 13:41:33 GMT+0000
                Duration(java.time.Duration.parse("PT2H"))
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(
            1754919693000 + 2*3600000,
            result.entityValues.getAsLong(Events.DTEND))
    }

    @Test
    fun `DTEND is null, DTSTART (DATE) present but DURATION is null`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(Date("20250811")),
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(
            1754956800000,      // DTSTART + 1 day
            result.entityValues.getAsLong(Events.DTEND))
    }

    @Test
    fun `DTEND is null, DTSTART (DATE-TIME) present but DURATION is null`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20250811T154133", tzVienna)),
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(
            1754919693000,      // same as DTSTART
            result.entityValues.getAsLong(Events.DTEND))
    }


    @Test
    fun `DTEND null for recurring event`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtEnd(),
                RRule("FREQ=DAILY;COUNT=5")
            )),
            main = VEvent(),
            to = result
        ))
        assertTrue(result.entityValues.containsKey(Events.DTEND))
        assertNull(result.entityValues.getAsLong(Events.DTEND))
    }

}