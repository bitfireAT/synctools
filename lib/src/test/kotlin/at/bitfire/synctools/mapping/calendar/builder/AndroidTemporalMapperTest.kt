/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import at.bitfire.DefaultTimezoneRule
import at.bitfire.synctools.icalendar.requireDtStart
import at.bitfire.synctools.mapping.calendar.builder.AndroidTemporalMapper.androidTimezoneId
import at.bitfire.synctools.mapping.calendar.builder.AndroidTemporalMapper.toTimestamp
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.chrono.JapaneseDate
import java.time.temporal.Temporal

class AndroidTemporalMapperTest {

    @get:Rule
    val tzRule = DefaultTimezoneRule("Europe/Vienna")

    @Test
    fun `toTimestamp on LocalDate should use start of UTC day`() {
        val date = LocalDate.of(2026, 3, 12)

        val timestamp = date.toTimestamp()

        assertEquals(1773273600000L, timestamp)
    }

    @Test
    fun `toTimestamp on LocalDateTime should use system default time zone`() {
        val date = LocalDateTime.of(2026, 3, 12, 12, 34, 56)

        val timestamp = date.toTimestamp()

        assertEquals(1773315296000L, timestamp)
    }

    @Test
    fun `toTimestamp on OffsetDateTime`() {
        val date = OffsetDateTime.of(2026, 3, 12, 12, 0, 0, 0, ZoneOffset.ofHours(3))

        val timestamp = date.toTimestamp()

        assertEquals(1773306000000L, timestamp)
    }

    @Test
    fun `toTimestamp on ZonedDateTime`() {
        val date = ZonedDateTime.of(2026, 3, 12, 12, 0, 0, 0, ZoneId.of("Europe/Helsinki"))

        val timestamp = date.toTimestamp()

        assertEquals(1773309600000L, timestamp)
    }

    @Test
    fun `toTimestamp on Instant`() {
        val inputTimestamp = 1773273600000L
        val date = Instant.ofEpochMilli(inputTimestamp)

        val timestamp = date.toTimestamp()

        assertEquals(inputTimestamp, timestamp)
    }

    @Test
    fun `toTimestamp on unsupported type`() {
        try {
            JapaneseDate.now().toTimestamp()

            fail("Expected exception")
        } catch (e: IllegalStateException) {
            assertEquals("Unsupported Temporal type: java.time.chrono.JapaneseDate", e.message)
        }
    }


    @Test
    fun `androidTimezoneId on LocalDate`() {
        val date = LocalDate.now()

        val timezoneId = date.androidTimezoneId()

        assertEquals("UTC", timezoneId)
    }

    @Test
    fun `androidTimezoneId on LocalDateTime`() {
        val date = LocalDateTime.now()

        val timezoneId = date.androidTimezoneId()

        assertEquals(tzRule.defaultZoneId.id, timezoneId)
    }

    @Test
    fun `androidTimezoneId on ZonedDateTime`() {
        val date = LocalDateTime.now().atZone(ZoneId.of("Europe/Dublin"))

        val timezoneId = date.androidTimezoneId()

        assertEquals("Europe/Dublin", timezoneId)
    }

    @Test
    fun `androidTimezoneId on Instant`() {
        val date = Instant.now()

        val timezoneId = date.androidTimezoneId()

        assertEquals("UTC", timezoneId)
    }

    @Test
    fun `androidTimezoneId on OffsetDateTime`() {
        try {
            OffsetDateTime.now().androidTimezoneId()

            fail("Expected exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("Non-floating date-time must be a ZonedDateTime", e.message)
        }
    }

    @Test
    fun `androidTimezoneId on ZonedDateTime from ical4j`() {
        val cal = CalendarBuilder().build(StringReader(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VTIMEZONE
            TZID:Etc/ABC
            BEGIN:STANDARD
            TZNAME:-03
            TZOFFSETFROM:-0300
            TZOFFSETTO:-0300
            DTSTART:19700101T000000
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            SUMMARY:Test Timezones
            DTSTART;TZID=Etc/ABC:20250828T130000
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        ))
        val vEvent = cal.getComponent<VEvent>(Component.VEVENT).get()
        val date = vEvent.requireDtStart<Temporal>().date

        try {
            date.androidTimezoneId()

            fail("Expected exception")
        } catch (e: IllegalArgumentException) {
            assertEquals(
                "ical4j ZoneIds are not supported. Call DatePropertyTzMapper.normalizedDate() " +
                        "before passing a date to this function.",
                e.message
            )
        }
    }

}