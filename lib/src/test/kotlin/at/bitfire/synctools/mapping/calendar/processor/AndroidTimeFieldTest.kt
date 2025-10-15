/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import at.bitfire.synctools.util.AndroidTimeUtils
import io.mockk.every
import io.mockk.mockk
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.util.TimeZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class AndroidTimeFieldTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzDefault = tzRegistry.getTimeZone(ZoneId.systemDefault().id)

    @Test
    fun `asIcal4jDate(all-day) returns ical4j Date`() {
        val result = AndroidTimeField(
            1760521619000,      // Wed Oct 15 2025 09:46:59 GMT+0000
            null,
            true,
            tzRegistry
        ).asIcal4jDate()
        assertEquals(Date("20251015"), result)
    }

    @Test
    fun `asIcal4jDate(non-all-day) returns ical4j zoned DateTime`() {
        val result = AndroidTimeField(
            1760521619000,      // Wed Oct 15 2025 09:46:59 GMT+0000
            "Europe/Vienna",
            false,
            tzRegistry
        ).asIcal4jDate()
        assertEquals(
            DateTime("20251015T114659", tzRegistry.getTimeZone("Europe/Vienna")),
            result
        )
    }

    @Test
    fun `asIcal4jDate(non-all-day with Android UTC timezone ID returns ical4j UTC DateTime`() {
        val result = AndroidTimeField(
            1760521619000,      // Wed Oct 15 2025 09:46:59 GMT+0000
            AndroidTimeUtils.TZID_UTC,
            false,
            tzRegistry
        ).asIcal4jDate() as DateTime
        assertEquals(1760521619000, result.time)
        assertTrue(result.isUtc)
    }

    @Test
    fun `asIcal4jDate(non-all-day with JVM UTC timezone ID returns ical4j UTC DateTime`() {
        val result = AndroidTimeField(
            1760521619000,      // Wed Oct 15 2025 09:46:59 GMT+0000
            TimeZones.UTC_ID,
            false,
            tzRegistry
        ).asIcal4jDate() as DateTime
        assertEquals(1760521619000, result.time)
        assertTrue(result.isUtc)
    }

    @Test
    fun `asIcal4jDate(non-all-day without timezone) returns ical4j DateTime in default zone`() {
        val result = AndroidTimeField(
            1760521619000,      // Wed Oct 15 2025 09:46:59 GMT+0000
            null,
            false,
            tzRegistry
        ).asIcal4jDate() as DateTime
        assertEquals(1760521619000, result.time)
        assertEquals(tzDefault, result.timeZone)
    }

    @Test
    fun `asIcal4jDate(non-all-day with unknown timezone) returns ical4j DateTime in default zone`() {
        val result = AndroidTimeField(
            1760521619000,      // Wed Oct 15 2025 09:46:59 GMT+0000
            "absolutely/unknown",
            false,
            tzRegistry
        ).asIcal4jDate() as DateTime
        assertEquals(1760521619000, result.time)
        assertEquals(tzDefault, result.timeZone)
    }

    @Test
    fun `asIcal4jDate(non-all-day with unknown timezone and unknown system timezone) returns ical4j UTC DateTime`() {
        val result = AndroidTimeField(
            1760521619000,      // Wed Oct 15 2025 09:46:59 GMT+0000
            "absolutely/unknown",
            false,
            mockk {
                every { getTimeZone(any()) } returns null
            }
        ).asIcal4jDate() as DateTime
        assertEquals(1760521619000, result.time)
        assertTrue(result.isUtc)
    }

}