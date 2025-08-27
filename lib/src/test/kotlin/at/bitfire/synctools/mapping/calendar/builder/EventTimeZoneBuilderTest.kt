/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.provider.CalendarContract.Events
import at.bitfire.synctools.storage.emptyEntity
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventTimeZoneBuilderTest {

    private val builder = EventTimeZoneBuilder()
    private val tzVienna = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone("Europe/Vienna")

    @Test
    fun `DTSTART is DATE`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(
                /* start = */ Date(),
                /* summary = */ "Some Event"
            ),
            main = VEvent(),
            to = result
        ))
        assertEquals("UTC", result.entityValues.getAsString(Events.EVENT_TIMEZONE))
    }

    @Test
    fun `DTSTART is DATE-TIME (UTC)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(
                /* start = */ DateTime(/*utc = */ true),
                /* summary = */ "Some Event"
            ),
            main = VEvent(),
            to = result
        ))
        assertEquals("Etc/UTC", result.entityValues.getAsString(Events.EVENT_TIMEZONE))
    }

    @Test
    fun `DTSTART is DATE-TIME with TZID`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(
                /* start = */ DateTime("20250811T154133", tzVienna),    // +0200 with DST
                /* summary = */ "Some Event"
            ),
            main = VEvent(),
            to = result
        ))
        assertEquals("Europe/Vienna", result.entityValues.getAsString(Events.EVENT_TIMEZONE))
    }

    @Test
    fun `No DTSTART`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        ))
        assertEquals(
            TimeZone.getDefault().id,
            result.entityValues.getAsString(Events.EVENT_TIMEZONE)
        )
    }

}