/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar.validation

import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import io.mockk.verify
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.InputStreamReader
import java.io.StringReader

class ICalPreprocessorTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    val processor = ICalPreprocessor()


    @Test
    fun testPreprocessStream_appliesStreamProcessors() {
        val preprocessors = processor.streamPreprocessors
        assertTrue(preprocessors.isNotEmpty())
        processor.streamPreprocessors.forEach {
            mockkObject(it)
        }

        processor.preprocessStream(StringReader(""))

        // verify that the required stream processors have been called
        verify {
            processor.streamPreprocessors.forEach {
                it.fixString(any())
            }
        }
    }


    @Test
    fun testPreprocessCalendar_MsTimeZones() {
        javaClass.getResourceAsStream("/events/outlook1.ics").use { stream ->
            val reader = InputStreamReader(stream, Charsets.UTF_8)
            val calendar = CalendarBuilder().build(reader)
            val vEvent = calendar.getComponent(Component.VEVENT) as VEvent

            assertEquals("W. Europe Standard Time", vEvent.startDate.timeZone.id)
            processor.preprocessCalendar(calendar)
            assertEquals("Europe/Vienna", vEvent.startDate.timeZone.id)
        }
    }

}