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
import junit.framework.TestCase.assertEquals
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.util.TimeZones
import org.junit.Assert.assertNull
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class EndTimeProcessorTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")!!

    private val processor = EndTimeProcessor(tzRegistry)

    // Note: When the calendar provider sets a non-null DTEND, it implies that the event is not recurring.

    @Test
    fun `All-day event`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 1,
            Events.DTSTART to 1592697500000L,   // DTSTART is required for DTEND to be processed
            Events.DTEND to 1592697600000L,     // 21/06/2020
        ))
        processor.process(entity, entity, result)
        assertEquals(DtEnd(Date("20200621")), result.dtEnd)
    }

    @Test
    fun `Non-all-day event with end timezone`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 0,
            Events.DTSTART to 1592733500000L,   // DTSTART is required for DTEND to be processed
            Events.EVENT_TIMEZONE to "Asia/Shanghai",
            Events.DTEND to 1592733600000L,     // 21/06/2020 12:00 +0200
            Events.EVENT_END_TIMEZONE to "Europe/Vienna"
        ))
        processor.process(entity, entity, result)
        assertEquals(DtEnd(DateTime("20200621T120000", tzVienna)), result.dtEnd)
    }

    @Test
    fun `Non-all-day event without end timezone`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 0,
            Events.DTSTART to 1592733500000L,   // DTSTART is required for DTEND to be processed
            Events.EVENT_TIMEZONE to "Europe/Vienna",   // required in Android; will be used as end time zone, if end time zone is missing
            Events.DTEND to 1592733600000L      // 21/06/2020 12:00 +0200
        ))
        processor.process(entity, entity, result)
        assertEquals(DtEnd(DateTime("20200621T120000", tzVienna)), result.dtEnd)
    }

    @Test
    fun `Non-all-day event without start or end timezone`() {
        val defaultTz = tzRegistry.getTimeZone(ZoneId.systemDefault().id)
        Assume.assumeTrue(defaultTz.id != TimeZones.UTC_ID)     // would cause UTC DATE-TIME
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 0,
            Events.DTSTART to 1592733500000L,   // DTSTART is required for DTEND to be processed
            Events.EVENT_TIMEZONE to null,      // required in Android; if it's not available against all expectations, we use UTC as fallback
            Events.DTEND to 1592733600000L      // 21/06/2020 12:00 +0200
        ))
        processor.process(entity, entity, result)
        assertEquals(1592733600000L, result.dtEnd?.date?.time)
        assertEquals(defaultTz, (result.dtEnd?.date as? DateTime)?.timeZone)
    }


    // skip conditions

    @Test
    fun `Skip if DTEND is not after DTSTART`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1592733500000L,
            Events.DTEND to 1592733500000L
        ))
        processor.process(entity, entity, result)
        assertNull(result.dtEnd)
    }

    @Test
    fun `Skip if DTEND is not set`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1592733500000L
        ))
        processor.process(entity, entity, result)
        assertNull(result.dtEnd)
    }

    @Test
    fun `Skip if DTSTART is not set`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.DTEND to 1592733500000L
        ))
        processor.process(entity, entity, result)
        assertNull(result.dtEnd)
    }

}