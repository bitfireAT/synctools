/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.util

import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.TimeZone

class DateUtilsTest {

    @Test
    fun testFindAndroidTimezoneID() {
        assertEquals("Europe/Vienna", DateUtils.findAndroidTimezoneID("Europe/Vienna"))
        assertEquals("Europe/Vienna", DateUtils.findAndroidTimezoneID("Vienna"))
        assertEquals("Europe/Vienna", DateUtils.findAndroidTimezoneID("Something with Europe/Vienna in between"))
        assertEquals(TimeZone.getDefault().id, DateUtils.findAndroidTimezoneID(null))
        assertEquals(TimeZone.getDefault().id, DateUtils.findAndroidTimezoneID("nothing-to-be-found"))
    }


    @Test
    fun testGetZoneId() {
        assertNull(DateUtils.getZoneId(null))
        assertNull(DateUtils.getZoneId("not/available"))
        assertEquals(ZoneId.of("Europe/Vienna"), DateUtils.getZoneId("Europe/Vienna"))
    }


    @Test
    fun testIsDate() {
        assertTrue(DateUtils.isDate(DtStart(Date("20200101"))))
        assertFalse(DateUtils.isDate(DtStart(DateTime("20200101T010203Z"))))
        assertFalse(DateUtils.isDate(null))
    }

    @Test
    fun testIsDateTime() {
        assertFalse(DateUtils.isDateTime(DtEnd(Date("20200101"))))
        assertTrue(DateUtils.isDateTime(DtEnd(DateTime("20200101T010203Z"))))
        assertFalse(DateUtils.isDateTime(null))
    }


    @Test
    fun testParseVTimeZone() {
        val vtz = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:DAVx5
            BEGIN:VTIMEZONE
            TZID:Asia/Shanghai
            END:VTIMEZONE
            END:VCALENDAR""".trimIndent()
        assertEquals("Asia/Shanghai", DateUtils.parseVTimeZone(vtz)?.timeZoneId?.value)
    }

    @Test
    fun testParseVTimeZone_Invalid() {
        assertNull(DateUtils.parseVTimeZone("Invalid"))
    }

}
