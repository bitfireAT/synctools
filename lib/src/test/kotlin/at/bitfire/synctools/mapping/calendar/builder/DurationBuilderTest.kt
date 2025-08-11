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
class DurationBuilderTest {

    private val builder = DurationBuilder()
    private val tzVienna = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone("Europe/Vienna")

    @Test
    fun `Standard DURATION`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                Duration(java.time.Duration.ofHours(1)),
                RRule("FREQ=DAILY;COUNT=5")
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals("PT1H", result.entityValues.getAsString(Events.DURATION))
    }


    @Test
    fun `DURATION null, DTSTART is DATE, DTEND is DATE`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(Date("20250811")),
                DtEnd(Date("20250813")),
                RRule("FREQ=DAILY;COUNT=5")
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals("PT48H", result.entityValues.getAsString(Events.DURATION))
    }

    @Test
    fun `DURATION null, DTSTART is DATE, DTEND is DATE-TIME`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(Date("20250811")),
                DtEnd(DateTime("20250813T043000", tzVienna)),
                RRule("FREQ=DAILY;COUNT=5")
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(
            "PT48H",    // DTEND treated as DATE; then it's two days difference
            result.entityValues.getAsString(Events.DURATION)
        )
    }

    @Test
    fun `DURATION null, DTSTART is DATE, DTEND is null`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(Date("20250811")),
                RRule("FREQ=DAILY;COUNT=5")
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(
            "PT24H",    // default period day for all-day events
            result.entityValues.getAsString(Events.DURATION)
        )
    }

    @Test
    fun `DURATION null, DTSTART is DATE-TIME, DTEND is DATE`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20250811T021500", tzVienna)),
                DtEnd(Date("20250813")),
                RRule("FREQ=DAILY;COUNT=5")
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(
            "PT0S",     // default fallback for non-all-day events
            result.entityValues.getAsString(Events.DURATION)
        )
    }

    @Test
    fun `DURATION null, DTSTART is DATE-TIME, DTEND is DATE-TIME`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20250811T021500", tzVienna)),
                DtEnd(DateTime("20250811T043000", tzVienna)),
                RRule("FREQ=DAILY;COUNT=5")
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals("PT2H15M", result.entityValues.getAsString(Events.DURATION))
    }

    @Test
    fun `DURATION null, DTSTART is DATE-TIME, DTEND is null`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20250811T021500", tzVienna)),
                RRule("FREQ=DAILY;COUNT=5")
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(
            "PT0S",     // default fallback for non-all-day events
            result.entityValues.getAsString(Events.DURATION)
        )
    }

    @Test
    fun `DURATION null, DTSTART is null`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                RRule("FREQ=DAILY;COUNT=5")
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(
            "PT0S",     // default fallback for non-all-day events
            result.entityValues.getAsString(Events.DURATION)
        )
    }


    @Test
    fun `DURATION not set for non-recurring event`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        ))
        assertTrue(result.entityValues.containsKey(Events.DURATION))
        assertNull(result.entityValues.getAsLong(Events.DURATION))
    }

}