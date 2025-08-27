/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.storage.emptyEntity
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.RRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExRuleBuilderTest {

    private val builder = ExRuleBuilder()

    @Test
    fun `Two EXRULE properties`() {
        val result = emptyEntity()
        val main = VEvent(propertyListOf(
            RRule("FREQ=DAILY;COUNT=3"),
            ExRule(Recur("FREQ=DAILY;COUNT=3")),
            ExRule(Recur("FREQ=WEEKLY;COUNT=3"))
        ))
        assertTrue(builder.build(from = main, main = main, to = result))
        assertEquals(
            "FREQ=DAILY;COUNT=3\nFREQ=WEEKLY;COUNT=3",
            result.entityValues.getAsString(Events.EXRULE)
        )
    }

    @Test
    fun `No EXRULE`() {
        val main = VEvent()
        val result = emptyEntity()
        assertTrue(builder.build(from = main, main = main, to = result))
        assertTrue(result.entityValues.containsKey(Events.EXRULE))
        assertNull(result.entityValues.get(Events.EXRULE))
    }

    @Test
    fun `No EXRULE for exception`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        ))
        assertTrue(result.entityValues.containsKey(Events.EXRULE))
        assertNull(result.entityValues.get(Events.EXRULE))
    }

    @Test
    fun `No EXRULE for non-recurring event`() {
        val main = VEvent(propertyListOf(
            ExRule(Recur("FREQ=DAILY;COUNT=3"))
        ))
        val result = emptyEntity()
        assertTrue(builder.build(from = main, main = main, to = result))
        assertTrue(result.entityValues.containsKey(Events.EXRULE))
        assertNull(result.entityValues.get(Events.EXRULE))
    }

}