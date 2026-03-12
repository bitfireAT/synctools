/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.property.DtStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.Temporal

class DatePropertyTzMapperTest {

    @Test
    fun `normalizedDate with TZID known to system`() {
        val dtStart = DtStart<ZonedDateTime>(
            ParameterList(listOf<Parameter>(TzId("Europe/Vienna"))),
            "20260311T224734"
        )

        // ical4j returns ZonedDatetime with timezone from ical4j database
        val ical4jDate = dtStart.date as ZonedDateTime
        assertTrue(ical4jDate.zone.id.startsWith("ical4j~"))

        // normalizedDate returns ZonedDatetime (at same timestamp) with system time zone
        val normalizedDate = dtStart.normalizedDate() as ZonedDateTime
        assertEquals("Europe/Vienna", normalizedDate.zone.id)
        assertEquals(ical4jDate.toInstant(), normalizedDate.toInstant())
    }

    @Test
    fun `normalizedDate with TZID unknown to system`() {
        val cal = CalendarBuilder().build(StringReader("BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Etc/ABC\n" +
                "BEGIN:STANDARD\n" +
                "TZNAME:-03\n" +
                "TZOFFSETFROM:-0300\n" +
                "TZOFFSETTO:-0300\n" +
                "DTSTART:19700101T000000\n" +
                "END:STANDARD\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "SUMMARY:Test Timezones\n" +
                "DTSTART;TZID=Etc/ABC:20250828T130000\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR"
        ))
        val vEvent = cal.getComponent<VEvent>(Component.VEVENT).get()
        val dtStart = vEvent.requireDtStart<Temporal>()

        // ical4j returns ZonedDatetime with custom timezone from VTIMEZONE
        val ical4jDate = dtStart.date as ZonedDateTime
        assertTrue(ical4jDate.zone.id.startsWith("ical4j-local-"))

        val timestamp = Instant.ofEpochMilli(
            /* 20250828T130000Z */ 1756386000000
            /* offset -0300 */ + 3*3600000
        )
        assertEquals(timestamp, ical4jDate.toInstant())

        // normalizedDate returns ZonedDatetime (at same timestamp) with system time zone
        val normalizedDate = dtStart.normalizedDate() as ZonedDateTime
        assertEquals(ZoneId.systemDefault(), normalizedDate.zone)
        assertEquals(timestamp, normalizedDate.toInstant())

        // We could NOT just generate the DTSTART from the time string and the system time zone
        assertNotEquals(
            timestamp,
            ZonedDateTime.of(2025, 8, 28, 13, 0, 0, 0, ZoneId.systemDefault()).toInstant()
        )
    }

    @Test
    fun `normalizedDate with Instant remains unchanged`() {
        // Test that Instant dates remain unchanged
        val dtStart = DtStart(Instant.now())

        val originalDate = dtStart.date
        val normalizedDate = dtStart.normalizedDate()

        // Should be the same instance since Instant doesn't have timezone
        assertEquals(originalDate, normalizedDate)
        assertTrue(normalizedDate is Instant)
    }

    @Test
    fun `normalizedDate with LocalDate remains unchanged`() {
        // Test that LocalDate dates remain unchanged
        val dtStart = DtStart<Temporal>("20260311")

        val originalDate = dtStart.date
        val normalizedDate = dtStart.normalizedDate()

        // Should be the same instance since LocalDate doesn't have timezone
        assertEquals(originalDate, normalizedDate)
        assertTrue(normalizedDate is LocalDate)
    }

    @Test
    fun `normalizedDate with OffsetDateTime becomes an Instant`() {
        // Test that OffsetDateTime dates remain unchanged
        val dtStart = DtStart<Temporal>("20260311T224734Z")

        val originalDate = dtStart.date                 // OffsetDateTime
        val normalizedDate = dtStart.normalizedDate()   // Instant

        // Should be the same timestamp
        assertEquals((originalDate as OffsetDateTime).toInstant(), normalizedDate)
    }


    @Test
    fun `systemTzId with exact match`() {
        val result = DatePropertyTzMapper.systemTzId("Europe/Vienna")
        assertEquals("Europe/Vienna", result)
    }

    @Test
    fun `systemTzId with case insensitive match`() {
        val result = DatePropertyTzMapper.systemTzId("europe/vienna")
        assertEquals("Europe/Vienna", result)
    }

    @Test
    fun `systemTzId with partial match`() {
        val result = DatePropertyTzMapper.systemTzId("/freeassociation.sourceforge.net/Tzfile/Europe/Vienna")
        assertEquals("Europe/Vienna", result)
    }

    @Test
    fun `systemTzId with null input`() {
        val result = DatePropertyTzMapper.systemTzId(null)
        assertEquals(null, result)
    }

    @Test
    fun `systemTzId with unknown timezone`() {
        val result = DatePropertyTzMapper.systemTzId("Unknown/Timezone")
        assertEquals(null, result)
    }


}