/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import junit.framework.TestCase.assertEquals
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecurrenceFieldProcessorTest {

    private val processor = RecurrenceFieldsProcessor()

    @Test
    fun `Recurring exception`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to System.currentTimeMillis(),
            Events.RRULE to "FREQ=DAILY;COUNT=10",
            Events.RDATE to "20251010T010203Z",
            Events.EXRULE to "FREQ=WEEKLY;COUNT=1",
            Events.EXDATE to "20260201T010203Z"
        ))
        processor.process(entity, Entity(ContentValues()), result)
        // exceptions must never have recurrence properties
        assertTrue(result.rRules.isEmpty())
        assertTrue(result.rDates.isEmpty())
        assertTrue(result.exRules.isEmpty())
        assertTrue(result.exDates.isEmpty())
    }

    @Test
    fun `Non-recurring main event`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to System.currentTimeMillis(),
            Events.EXRULE to "FREQ=WEEKLY;COUNT=1",
            Events.EXDATE to "20260201T010203Z"
        ))
        processor.process(entity, entity, result)
        // non-recurring events must never have recurrence properties
        assertTrue(result.rRules.isEmpty())
        assertTrue(result.rDates.isEmpty())
        assertTrue(result.exRules.isEmpty())
        assertTrue(result.exDates.isEmpty())
    }

    @Test
    fun `Recurring main event`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to System.currentTimeMillis(),
            Events.RRULE to "FREQ=DAILY;COUNT=10",
            Events.RDATE to "20251010T010203Z",
            Events.EXRULE to "FREQ=WEEKLY;COUNT=1",
            Events.EXDATE to "20260201T010203Z"
        ))
        processor.process(entity, entity, result)
        assertEquals(listOf(RRule("FREQ=DAILY;COUNT=10")), result.rRules)
        assertEquals(listOf(RDate(ParameterList(), "20251010T010203Z")), result.rDates)
        assertEquals("FREQ=WEEKLY;COUNT=1", result.exRules.joinToString { it.value })
        assertEquals("20260201T010203Z", result.exDates.joinToString { it.value })
    }


    @Test
    fun `RRULE with UNTIL before DTSTART`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1759403653000,    // Thu Oct 02 2025 11:14:13 GMT+0000
            Events.RRULE to "FREQ=DAILY;UNTIL=20251002T111300Z"
        ))
        processor.process(entity, entity, result)
        assertTrue(result.rRules.isEmpty())
    }

    @Test
    fun `EXRULE with UNTIL before DTSTART`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1759403653000,    // Thu Oct 02 2025 11:14:13 GMT+0000
            Events.EXRULE to "FREQ=DAILY;UNTIL=20251002T111300Z"
        ))
        processor.process(entity, entity, result)
        assertTrue(result.exRules.isEmpty())
    }

}