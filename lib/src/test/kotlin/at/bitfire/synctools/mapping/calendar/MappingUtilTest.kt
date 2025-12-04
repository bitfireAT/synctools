/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import at.bitfire.synctools.mapping.calendar.MappingUtil.dtEndFromDefault
import at.bitfire.synctools.mapping.calendar.MappingUtil.dtEndFromDuration
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import org.junit.Assert.assertEquals
import org.junit.Test

class MappingUtilTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")


    // dtEndFromDefault

    @Test
    fun `dtEndFromDefault (DATE)`() {
        assertEquals(
            DtEnd(Date("20251101")),
            dtEndFromDefault(DtStart(Date("20251031")))
        )
    }

    @Test
    fun `dtEndFromDefault (DATE-TIME)`() {
        val time = DateTime("20251031T123466Z")
        assertEquals(
            DtEnd(time),
            dtEndFromDefault(DtStart(time))
        )
    }


    // dtEndFromDuration

    @Test
    fun `dtEndFromDuration (dtStart=DATE, duration is date-based)`() {
        val result = dtEndFromDuration(
            DtStart(Date("20240228")),
            java.time.Duration.ofDays(1)
        )
        assertEquals(
            DtEnd(Date("20240229")),    // leap day
            result
        )
    }

    @Test
    fun `dtEndFromDuration (dtStart=DATE, duration is time-based)`() {
        val result = dtEndFromDuration(
            DtStart(Date("20241231")),
            java.time.Duration.ofHours(25)
        )
        assertEquals(
            DtEnd(Date("20250101")),
            result
        )
    }

    @Test
    fun `dtEndFromDuration (dtStart=DATE-TIME, duration is date-based)`() {
        val result = dtEndFromDuration(
            DtStart(DateTime("20250101T045623", tzVienna)),
            java.time.Duration.ofDays(1)
        )
        assertEquals(
            DtEnd(DateTime("20250102T045623", tzVienna)),
            result
        )
    }

    @Test
    fun `dtEndFromDuration (dtStart=DATE-TIME, duration is time-based)`() {
        val result = dtEndFromDuration(
            DtStart(DateTime("20250101T045623", tzVienna)),
            java.time.Duration.ofHours(25)
        )
        assertEquals(
            DtEnd(DateTime("20250102T055623", tzVienna)),
            result
        )
    }

    @Test
    fun `dtEndFromDuration (dtStart=DATE-TIME, duration is time-based and negative)`() {
        val result = dtEndFromDuration(
            DtStart(DateTime("20250101T045623", tzVienna)),
            java.time.Duration.ofHours(-25)
        )
        assertEquals(
            DtEnd(DateTime("20250102T055623", tzVienna)),
            result
        )
    }

}