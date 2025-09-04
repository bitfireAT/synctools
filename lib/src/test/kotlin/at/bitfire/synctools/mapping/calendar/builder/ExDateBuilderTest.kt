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
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExDateBuilderTest {

    private val builder = ExDateBuilder()
    private val tzVienna = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone("Europe/Vienna")

    @Test
    fun `Two EXDATE properties (DTSTART is DATE)`() {
        val result = emptyEntity()
        val main = VEvent(propertyListOf(
            DtStart(Date()),
            ExDate(DateList("20250812", Value.DATE)),
            ExDate(DateList("20250813T185504", Value.DATE_TIME, tzVienna)),
            RRule("FREQ=DAILY")
        ))
        assertTrue(builder.build(from = main, main = main, to = result))
        assertEquals(
            "20250812T000000Z,20250813T000000Z",
            result.entityValues.getAsString(Events.EXDATE)
        )
    }

    @Test
    fun `Two EXDATE properties (DTSTART is DATE-TIME)`() {
        val result = emptyEntity()
        val main = VEvent(propertyListOf(
            DtStart(DateTime("20250811T022345", tzVienna)),
            ExDate(DateList("20250813T185504", Value.DATE_TIME, tzVienna)),
            ExDate(DateList("20250812", Value.DATE)),
            RRule("FREQ=DAILY")
        ))
        assertTrue(builder.build(from = main, main = main, to = result))
        assertEquals(
            "Europe/Vienna;20250813T185504,20250812T002345Z",
            result.entityValues.getAsString(Events.EXDATE)
        )
    }

    @Test
    fun `No EXDATE`() {
        val main = VEvent()
        val result = emptyEntity()
        assertTrue(builder.build(from = main, main = main, to = result))
        assertTrue(result.entityValues.containsKey(Events.EXDATE))
        assertNull(result.entityValues.get(Events.EXDATE))
    }

    @Test
    fun `No EXDATE for exception`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20250811T022345", tzVienna)),
                ExDate(DateList("20250813T185504", Value.DATE_TIME, tzVienna)),
            )),
            main = VEvent(propertyListOf(
                DtStart(DateTime("20250811T022345", tzVienna)),
            )),
            to = result
        ))
        assertTrue(result.entityValues.containsKey(Events.EXDATE))
        assertNull(result.entityValues.get(Events.EXDATE))
    }

    @Test
    fun `No EXDATE for non-recurring`() {
        val result = emptyEntity()
        val main = VEvent(propertyListOf(
            DtStart(DateTime("20250811T022345", tzVienna)),
            ExDate(DateList("20250813T185504", Value.DATE_TIME, tzVienna)),
        ))
        assertTrue(builder.build(from = main, main = main, to = result))
        assertTrue(result.entityValues.containsKey(Events.EXDATE))
        assertNull(result.entityValues.get(Events.EXDATE))
    }

}