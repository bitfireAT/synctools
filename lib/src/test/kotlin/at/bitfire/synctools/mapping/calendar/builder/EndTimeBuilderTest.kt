/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Duration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EndTimeBuilderTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")

    private val builder = EndTimeBuilder()

    @Test
    fun `alignWithDtStart(dtEnd=DATE, dtStart=DATE)`() {
        val result = builder.alignWithDtStart(
            DtEnd(Date("20251007")),
            DtStart(Date("20250101"))
        )
        assertEquals(DtEnd(Date("20251007")), result)
    }

    @Test
    fun `alignWithDtStart(dtEnd=DATE, dtStart=DATE-TIME`() {
        val result = builder.alignWithDtStart(
            DtEnd(Date("20251007")),
            DtStart(DateTime("20250101T005623", tzVienna))
        )
        assertEquals(DtEnd(DateTime("20251007T005623", tzVienna)), result)
    }

    @Test
    fun `alignWithDtStart(dtEnd=DATE-TIME, dtStart=DATE)`() {
        val result = builder.alignWithDtStart(
            DtEnd(DateTime("20251007T010203Z")),
            DtStart(Date("20250101"))
        )
        assertEquals(DtEnd(Date("20251007")), result)
    }

    @Test
    fun `alignWithDtStart(dtEnd=DATE-TIME, dtStart=DATE-TIME)`() {
        val result = builder.alignWithDtStart(
            DtEnd(DateTime("20251007T010203Z")),
            DtStart(DateTime("20250101T045623", tzVienna))
        )
        assertEquals(DtEnd(DateTime("20251007T010203Z")), result)
    }


    @Test
    fun `calculateDtEndFromDefault (DATE)`() {
        assertEquals(
            DtEnd(Date("20251101")),
            builder.calculateDtEndFromDefault(DtStart(Date("20251031")))
        )
    }

    @Test
    fun `calculateDtEndFromDefault (DATE-TIME)`() {
        val time = DateTime("20251031T123466Z")
        assertEquals(
            DtEnd(time),
            builder.calculateDtEndFromDefault(DtStart(time))
        )
    }


    @Test
    fun `calculateDtEndFromDuration (dtStart=DATE, duration is date-based)`() {
        val result = builder.calculateDtEndFromDuration(
            DtStart(Date("20240228")),
            Duration(null, "P1D")
        )
        assertEquals(
            DtEnd(Date("20240229")),    // leap day
            result
        )
    }

    @Test
    fun `calculateDtEndFromDuration (dtStart=DATE, duration is time-based)`() {
        val result = builder.calculateDtEndFromDuration(
            DtStart(Date("20241231")),
            Duration(null, "PT25H")
        )
        assertEquals(
            DtEnd(Date("20250101")),
            result
        )
    }

    @Test
    fun `calculateDtEndFromDuration (dtStart=DATE-TIME, duration is date-based)`() {
        val result = builder.calculateDtEndFromDuration(
            DtStart(DateTime("20250101T045623", tzVienna)),
            Duration(null, "P1D")
        )
        assertEquals(
            DtEnd(DateTime("20250102T045623", tzVienna)),
            result
        )
    }

    @Test
    fun `calculateDtEndFromDuration (dtStart=DATE-TIME, duration is time-based)`() {
        val result = builder.calculateDtEndFromDuration(
            DtStart(DateTime("20250101T045623", tzVienna)),
            Duration(null, "PT25H")
        )
        assertEquals(
            DtEnd(DateTime("20250102T055623", tzVienna)),
            result
        )
    }

}