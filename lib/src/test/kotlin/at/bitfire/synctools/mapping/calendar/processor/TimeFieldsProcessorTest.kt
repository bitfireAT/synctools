/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.util.AndroidTimeUtils
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimeFieldsProcessorTest {

    private val processor = TimeFieldsProcessor()

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzShanghai = tzRegistry.getTimeZone("Asia/Shanghai")!!
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")!!

    @Test
    fun `Non-all-day, non-recurring`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1592733600000L,   // 21/06/2020 12:00 +0200
            Events.EVENT_TIMEZONE to "Europe/Vienna",
            Events.DTEND to 1592742600000L,             // 21/06/2020 14:30 +0200
            Events.EVENT_END_TIMEZONE to "Europe/Vienna"
        ))
        processor.process(entity, entity, result)
        assertEquals(DtStart(DateTime("20200621T120000", tzVienna)), result.dtStart)
        assertEquals(DtEnd(DateTime("20200621T143000", tzVienna)), result.dtEnd)
        assertNull(result.duration)
    }

    @Test
    fun `Non-all-day, non-recurring, DTSTART=DTEND`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1592742600000L,   // 21/06/2020 14:30 +0200
            Events.EVENT_TIMEZONE to "Europe/Vienna",
            Events.DTEND to 1592742600000L,             // 21/06/2020 14:30 +0200
            Events.EVENT_END_TIMEZONE to "Europe/Vienna"
        ))
        processor.process(entity, entity, result)
        assertEquals(DtStart(DateTime("20200621T143000", tzVienna)), result.dtStart)
        assertEquals(result.dtEnd!!.date, result.dtStart!!.date)
        assertNull(result.duration)
    }

    @Test
    fun `Non-all-day, non-recurring, mixed time zones`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1592733600000L,   // 21/06/2020 18:00 +0800
            Events.EVENT_TIMEZONE to "Asia/Shanghai",
            Events.DTEND to 1592742600000L,             // 21/06/2020 14:30 +0200
            Events.EVENT_END_TIMEZONE to "Europe/Vienna"
        ))
        processor.process(entity, entity, result)
        assertEquals(DtStart(DateTime("20200621T180000", tzShanghai)), result.dtStart)
        assertEquals(DtEnd(DateTime("20200621T143000", tzVienna)), result.dtEnd)
        assertNull(result.duration)
    }

    @Test
    fun `Non-all-day, non-recurring, with DURATION`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1592733600000L,   // 21/06/2020 18:00 +0800
            Events.EVENT_TIMEZONE to "Asia/Shanghai",
            Events.DURATION to "PT1H"
        ))
        processor.process(entity, entity, result)
        assertEquals(DtStart(DateTime("20200621T180000", tzShanghai)), result.dtStart)
        assertEquals(DtEnd(DateTime("20200621T190000", tzShanghai)), result.dtEnd)
        assertNull(result.duration)
    }

    @Test
    fun `Non-all-day, recurring, with DURATION, Kiev time zone`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1592733600000L,   // 21/06/2020 18:00 +0800
            Events.EVENT_TIMEZONE to "Europe/Kiev",
            Events.DURATION to "PT1H",
            Events.RRULE to "FREQ=DAILY;COUNT=2"
        ))
        processor.process(entity, entity, result)
        assertEquals(1592733600000L, result.dtStart?.date?.time)
        assertEquals(1592733600000L + 3600000, result.dtEnd?.date?.time)
        assertEquals("Europe/Kiev", result.dtStart?.timeZone?.id)
        assertEquals("Europe/Kiev", result.dtEnd?.timeZone?.id)
    }


    @Test
    fun `All-day, non-recurring, DTSTART=DTEND`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 1,
            Events.DTSTART to 1592697600000L,   // 21/06/2020
            Events.EVENT_TIMEZONE to AndroidTimeUtils.TZID_UTC,
            Events.DTEND to 1592697600000L,     // 21/06/2020
            Events.EVENT_END_TIMEZONE to AndroidTimeUtils.TZID_UTC
        ))
        processor.process(entity, entity, result)
        assertEquals(DtStart(Date("20200621")), result.dtStart)
        assertNull(result.dtEnd)
        assertNull(result.duration)
    }

    @Test
    fun `All-day, non-recurring, one day long`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 1,
            Events.DTSTART to 1592697600000L,   // 21/06/2020
            Events.EVENT_TIMEZONE to AndroidTimeUtils.TZID_UTC,
            Events.DTEND to 1592784000000L,     // 22/06/2020
            Events.EVENT_END_TIMEZONE to AndroidTimeUtils.TZID_UTC
        ))
        processor.process(entity, entity, result)
        assertEquals(DtStart(Date("20200621")), result.dtStart)
        assertEquals(DtEnd(Date("20200622")), result.dtEnd)
        assertNull(result.duration)
    }

    @Test
    fun `All-day, non-recurring, with DURATION`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 1,
            Events.DTSTART to 1592697600000L,   // 21/06/2020
            Events.EVENT_TIMEZONE to AndroidTimeUtils.TZID_UTC,
            Events.DURATION to "P1W"
        ))
        processor.process(entity, entity, result)
        assertEquals(DtStart(Date("20200621")), result.dtStart)
        assertEquals(DtEnd(Date("20200628")), result.dtEnd)
        assertNull(result.duration)
    }

    @Test
    fun `All-day, non-recurring, with DURATION less than one day`() {
        /* This should not happen, because according to the documentation, non-recurring events MUST
        have a dtEnd. However, the calendar provider doesn't enforce this for non-sync-adapters. */
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 1,
            Events.DTSTART to 1592697600000L,   // 21/06/2020
            Events.EVENT_TIMEZONE to AndroidTimeUtils.TZID_UTC,
            Events.DURATION to "PT1H30M"
        ))
        processor.process(entity, entity, result)
        assertEquals(DtStart(Date("20200621")), result.dtStart)
        assertNull(result.dtEnd)
        assertNull(result.duration)
    }

    @Test
    fun `All-day, non-recurring, non-all-day DURATION more than one day`() {
        /* This should not happen, because according to the documentation, non-recurring events MUST
        have a dtEnd. However, the calendar provider doesn't enforce this for non-sync-adapters. */
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 1,
            Events.DTSTART to 1592697600000L,   // 21/06/2020
            Events.EVENT_TIMEZONE to AndroidTimeUtils.TZID_UTC,
            Events.DURATION to "PT49H2M"
        ))
        processor.process(entity, entity, result)
        assertEquals(DtStart(Date("20200621")), result.dtStart)
        assertEquals(DtEnd(Date("20200623")), result.dtEnd)
        assertNull(result.duration)
    }

}