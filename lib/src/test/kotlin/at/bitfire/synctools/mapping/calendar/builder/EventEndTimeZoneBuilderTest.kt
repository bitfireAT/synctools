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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventEndTimeZoneBuilderTest {

    private val builder = EventEndTimeZoneBuilder()
    private val tzVienna = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone("Europe/Vienna")

    @Test
    fun `DTEND is DATE (DTSTART is DATE)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(Date()),
                DtEnd(Date())
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals("UTC", result.entityValues.getAsString(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `DTEND is DATE (DTSTART is DATE-TIME)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20250812T145812", tzVienna)),
                DtEnd(Date())
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals("Europe/Vienna", result.entityValues.getAsString(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `DTEND is DATE-TIME (DTSTART is DATE)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(Date()),
                DtEnd(DateTime())
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals("UTC", result.entityValues.getAsString(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `DTEND is DATE-TIME (DTSTART is DATE-TIME)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime()),
                DtEnd(DateTime("20250812T145812", tzVienna))
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals("Europe/Vienna", result.entityValues.getAsString(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `No DTEND`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime())
            )),
            main = VEvent(),
            to = result
        ))
        assertTrue(result.entityValues.containsKey(Events.EVENT_END_TIMEZONE))
        assertNull(result.entityValues.getAsString(Events.EVENT_END_TIMEZONE))
    }

}