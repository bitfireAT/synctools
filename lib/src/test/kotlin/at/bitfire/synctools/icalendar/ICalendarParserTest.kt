/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.synctools.exception.InvalidRemoteResourceException
import at.bitfire.synctools.icalendar.validation.ICalPreprocessor
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import java.io.StringReader

class ICalendarParserTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @Test
    fun testParse_AppliesPreProcessing() {
        mockkObject(ICalPreprocessor)

        val reader = StringReader(
            "BEGIN:VCALENDAR\r\n" +
            "BEGIN:VEVENT\r\n" +
            "END:VEVENT\r\n" +
            "END:VCALENDAR\r\n"
        )
        val cal = ICalendarParser().parse(reader)

        verify(exactly = 1) {
            // verify preprocessing was applied to stream
            ICalPreprocessor.preprocessStream(any())

            // verify preprocessing was applied to resulting calendar
            ICalPreprocessor.preprocessCalendar(cal)
        }
    }

    @Test
    fun testParse_SuppressesInvalidProperties() {
        val reader = StringReader(
            "BEGIN:VCALENDAR\r\n" +
                    "BEGIN:VEVENT\r\n" +
                    "DTSTAMP:invalid\r\n" +
                    "END:VEVENT\r\n" +
                    "END:VCALENDAR\r\n"
        )
        ICalendarParser().parse(reader)
    }

    @Test(expected = InvalidRemoteResourceException::class)
    fun testParse_ThrowsExceptionOnInvalidInput() {
        val reader = StringReader("invalid")
        ICalendarParser().parse(reader)
    }

}