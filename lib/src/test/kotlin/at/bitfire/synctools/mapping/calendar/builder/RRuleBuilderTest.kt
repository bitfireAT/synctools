/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.storage.emptyEntity
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.RRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RRuleBuilderTest {

    private val builder = RRuleBuilder()

    @Test
    fun `Two RRULE properties`() {
        val result = emptyEntity()
        val main = VEvent(propertyListOf(
            RRule("FREQ=DAILY;COUNT=3"),
            RRule("FREQ=WEEKLY;COUNT=3"),
        ))
        assertTrue(builder.build(from = main, main = main, to = result))
        assertEquals(
            "FREQ=DAILY;COUNT=3\nFREQ=WEEKLY;COUNT=3",
            result.entityValues.getAsString(Events.RRULE)
        )
    }

    @Test
    fun `No RRULE`() {
        val main = VEvent()
        val result = emptyEntity()
        assertTrue(builder.build(from = main, main = main, to = result))
        assertTrue(result.entityValues.containsKey(Events.RRULE))
        assertNull(result.entityValues.get(Events.RRULE))
    }

    @Test
    fun `No RRULE for exception`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                RRule("FREQ=DAILY;COUNT=3")
            )),
            main = VEvent(),
            to = result
        ))
        assertTrue(result.entityValues.containsKey(Events.RRULE))
        assertNull(result.entityValues.get(Events.RRULE))
    }

}