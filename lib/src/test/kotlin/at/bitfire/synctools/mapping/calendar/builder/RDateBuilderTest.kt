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
import net.fortuna.ical4j.model.property.RDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RDateBuilderTest {

    private val builder = RDateBuilder()
    private val tzVienna = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone("Europe/Vienna")

    @Test
    fun `Two RDATE properties (DTSTART is DATE)`() {
        val result = emptyEntity()
        val main = VEvent(propertyListOf(
            DtStart(Date("20250811")),
            RDate(DateList("20250811", Value.DATE)),
            RDate(DateList("20250812T185504", Value.DATE_TIME, tzVienna))
        ))
        assertTrue(builder.build(from = main, main = main, to = result))
        assertEquals(
            "20250811T000000Z,20250812T000000Z",
            result.entityValues.getAsString(Events.RDATE)
        )
    }

    @Test
    fun `Two RDATE properties (DTSTART is DATE-TIME)`() {
        val result = emptyEntity()
        val main = VEvent(propertyListOf(
            DtStart(DateTime("20250811T022345", tzVienna)),
            RDate(DateList("20250811", Value.DATE)),
            RDate(DateList("20250812T185504", Value.DATE_TIME, tzVienna))
        ))
        assertTrue(builder.build(from = main, main = main, to = result))
        assertEquals(
            "20250811T002345Z,20250812T165504Z",
            result.entityValues.getAsString(Events.RDATE)
        )
    }

    @Test
    fun `No RDATE`() {
        val main = VEvent()
        val result = emptyEntity()
        assertTrue(builder.build(from = main, main = main, to = result))
        assertTrue(result.entityValues.containsKey(Events.RDATE))
        assertNull(result.entityValues.get(Events.RDATE))
    }

    @Test
    fun `No RDATE for exception`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        ))
        assertTrue(result.entityValues.containsKey(Events.RDATE))
        assertNull(result.entityValues.get(Events.RDATE))
    }

}