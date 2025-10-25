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
import at.bitfire.synctools.exception.InvalidLocalResourceException
import at.bitfire.synctools.util.AndroidTimeUtils
import junit.framework.TestCase.assertEquals
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtStart
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StartTimeProcessorTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")!!

    private val processor = StartTimeProcessor(tzRegistry)

    @Test
    fun `All-day event`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 1,
            Events.DTSTART to 1592697600000L,   // 21/06/2020
            Events.EVENT_TIMEZONE to AndroidTimeUtils.TZID_UTC
        ))
        processor.process(entity, entity, result)
        assertEquals(DtStart(Date("20200621")), result.startDate)
    }

    @Test
    fun `Non-all-day event`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1592733600000L,   // 21/06/2020 12:00 +0200
            Events.EVENT_TIMEZONE to "Europe/Vienna"
        ))
        processor.process(entity, entity, result)
        assertEquals(DtStart(DateTime("20200621T120000", tzVienna)), result.startDate)
    }

    @Test(expected = InvalidLocalResourceException::class)
    fun `No start time`() {
        val entity = Entity(ContentValues())
        processor.process(entity, entity, VEvent())
    }

}