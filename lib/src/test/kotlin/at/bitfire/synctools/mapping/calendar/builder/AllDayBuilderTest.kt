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
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AllDayBuilderTest {

    private val builder = AllDayBuilder()
    private val tzVienna = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone("Europe/Vienna")

    @Test
    fun `DTSTART is DATE`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(
                /* start = */ Date(1754919693000),      // Mon Aug 11 2025 13:41:33 GMT+0000
                /* summary = */ "Some Event"
            ),
            main = VEvent(),
            to = result
        ))
        assertEquals(1, result.entityValues.getAsInteger(Events.ALL_DAY))
    }

    @Test
    fun `DTSTART is DATE-TIME`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(
                /* start = */ DateTime("20250811T154133", tzVienna),    // +0200 with DST
                /* summary = */ "Some Event"
            ),
            main = VEvent(),
            to = result
        ))
        assertEquals(0, result.entityValues.getAsInteger(Events.ALL_DAY))
    }

    @Test
    fun `No DTSTART`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        ))
        assertEquals(0, result.entityValues.getAsInteger(Events.ALL_DAY))
    }

}