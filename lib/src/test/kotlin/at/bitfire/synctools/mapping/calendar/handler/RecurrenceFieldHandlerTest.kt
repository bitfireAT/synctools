/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecurrenceFieldHandlerTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")

    private val handler = RecurrenceFieldsHandler(tzRegistry)

    @Test
    fun `Recurring exception`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to System.currentTimeMillis(),
            Events.RRULE to "FREQ=DAILY;COUNT=10",
            Events.RDATE to "20251010T010203Z",
            Events.EXRULE to "FREQ=WEEKLY;COUNT=1",
            Events.EXDATE to "20260201T010203Z"
        ))
        handler.process(entity, Entity(ContentValues()), result)
        // exceptions must never have recurrence properties
        assertNull(result.getProperty<RRule>(Property.RRULE))
        assertNull(result.getProperty<RDate>(Property.RDATE))
        assertNull(result.getProperty<ExRule>(Property.EXRULE))
        assertNull(result.getProperty<ExDate>(Property.EXDATE))
    }

    @Test
    fun `Non-recurring main event`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to System.currentTimeMillis(),
            Events.EXRULE to "FREQ=WEEKLY;COUNT=1",
            Events.EXDATE to "20260201T010203Z"
        ))
        handler.process(entity, entity, result)
        // non-recurring events must never have recurrence properties
        assertNull(result.getProperty<RRule>(Property.RRULE))
        assertNull(result.getProperty<RDate>(Property.RDATE))
        assertNull(result.getProperty<ExRule>(Property.EXRULE))
        assertNull(result.getProperty<ExDate>(Property.EXDATE))
    }

    @Test
    fun `Recurring main event`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to System.currentTimeMillis(),
            Events.RRULE to "FREQ=DAILY;COUNT=10",
            Events.RDATE to "20251010T010203Z",
            Events.EXRULE to "FREQ=WEEKLY;COUNT=1",
            Events.EXDATE to "20260201T010203Z"
        ))
        handler.process(entity, entity, result)
        assertEquals(
            listOf(RRule("FREQ=DAILY;COUNT=10")),
            result.getProperties<RRule>(Property.RRULE)
        )
        assertEquals(
            listOf(RDate(ParameterList(), "20251010T010203Z")),
            result.getProperties<RDate>(Property.RDATE)
        )
        assertEquals(
            "FREQ=WEEKLY;COUNT=1",
            result.getProperties<ExRule>(Property.EXRULE).joinToString { it.value }
        )
        assertEquals(
            "20260201T010203Z",
            result.getProperties<ExDate>(Property.EXDATE).joinToString { it.value }
        )
    }


    @Test
    fun `RRULE with UNTIL before DTSTART`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1759403653000,    // Thu Oct 02 2025 11:14:13 GMT+0000
            Events.RRULE to "FREQ=DAILY;UNTIL=20251002T111300Z",
            Events.EXDATE to "1759403653000"            // should be removed because the only RRULE is invalid and discarded,
                                                        // so the whole event isn't recurring anymore
        ))
        handler.process(entity, entity, result)
        assertNull(result.getProperty<RRule>(Property.RRULE))
        assertNull(result.getProperty<ExDate>(Property.EXDATE))
    }

    @Test
    fun `EXRULE with UNTIL before DTSTART`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1759403653000,    // Thu Oct 02 2025 11:14:13 GMT+0000,
            Events.RRULE to "FREQ=DAILY;COUNT=10",      // EXRULE is only processed for recurring events
            Events.EXRULE to "FREQ=DAILY;UNTIL=20251002T111300Z"
        ))
        handler.process(entity, entity, result)
        assertNull(result.getProperty<ExRule>(Property.EXRULE))
    }


    @Test
    fun `alignUntil(recurUntil=null)`() {
        val recur = Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .build()
        val result = handler.alignUntil(
            recur = recur,
            startDate = mockk()
        )
        assertSame(recur, result)
    }

    @Test
    fun `alignUntil(recurUntil=DATE, startDate=DATE)`() {
        val recur = Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(Date("20251015"))
            .build()
        val result = handler.alignUntil(
            recur = recur,
            startDate = Date()
        )
        assertSame(recur, result)
    }

    @Test
    fun `alignUntil(recurUntil=DATE, startDate=DATE-TIME)`() {
        val result = handler.alignUntil(
            recur = Recur.Builder()
                .frequency(Recur.Frequency.DAILY)
                .until(Date("20251015"))
                .build(),
            startDate = DateTime("20250101T010203", tzVienna)
        )
        assertEquals(
            Recur.Builder()
                .frequency(Recur.Frequency.DAILY)
                .until(DateTime("20251014T230203Z"))
                .build(),
            result
        )
    }

    @Test
    fun `alignUntil(recurUntil=DATE-TIME, startDate=DATE)`() {
        val result = handler.alignUntil(
            recur = Recur.Builder()
                .frequency(Recur.Frequency.DAILY)
                .until(DateTime("20251015T153118", tzVienna))
                .build(),
            startDate = Date()
        )
        assertEquals(
            Recur.Builder()
                .frequency(Recur.Frequency.DAILY)
                .until(Date("20251015"))
                .build(),
            result
        )
    }

    @Test
    fun `alignUntil(recurUntil=DATE-TIME, startDate=DATE-TIME)`() {
        val result = handler.alignUntil(
            recur = Recur.Builder()
                .frequency(Recur.Frequency.DAILY)
                .until(DateTime("20251015T153118", tzVienna))
                .build(),
            startDate = DateTime()
        )
        assertEquals(
            Recur.Builder()
                .frequency(Recur.Frequency.DAILY)
                .until(DateTime("20251015T133118Z"))
                .build(),
            result
        )
    }

}