/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Uid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.Temporal

class ICalendarGeneratorTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzBerlin = tzRegistry.getTimeZone("Europe/Berlin").toZoneId()
    private val tzLondon = tzRegistry.getTimeZone("Europe/London").toZoneId()

    private val userAgent = ProdId("TestUA/1.0")
    private val writer = ICalendarGenerator()

    @Test
    fun `Write event with exceptions and various timezones`() {
        val iCal = StringWriter()
        writer.write(AssociatedEvents(
            main =
                VEvent(propertyListOf(
                    Uid("SAMPLEUID"),
                    DtStart(ZonedDateTime.of(LocalDateTime.parse("2019-01-01T10:00:00"), tzBerlin)),
                    DtEnd(Instant.parse("2019-01-01T16:00:00Z")),
                    DtStamp("20251028T185101Z"),
                    RRule<Temporal>("FREQ=DAILY;COUNT=5")
                ), ComponentList(listOf(
                    VAlarm(Duration.ofHours(-1))
                ))),
            exceptions = listOf(
                VEvent(propertyListOf(
                    Uid("SAMPLEUID"),
                    RecurrenceId(ZonedDateTime.of(LocalDateTime.parse("2019-01-02T10:00:00"), tzBerlin)),
                    DtStart(ZonedDateTime.of(LocalDateTime.parse("2019-01-01T11:00:00"), tzLondon)),
                    DtEnd(Instant.parse("2019-01-01T17:00:00Z")),
                    DtStamp("20251028T185101Z")
                ))
            ),
            prodId = userAgent
        ), iCal)

        assertEquals("BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "PRODID:TestUA/1.0\r\n" +
                // main event
                "BEGIN:VEVENT\r\n" +
                "UID:SAMPLEUID\r\n" +
                "DTSTART;TZID=Europe/Berlin:20190101T100000\r\n" +
                "DTEND:20190101T160000Z\r\n" +
                "DTSTAMP:20251028T185101Z\r\n" +
                "RRULE:FREQ=DAILY;COUNT=5\r\n" +
                "BEGIN:VALARM\r\n" +
                "TRIGGER:-PT1H\r\n" +
                "END:VALARM\r\n" +
                "END:VEVENT\r\n" +
                // exception
                "BEGIN:VEVENT\r\n" +
                "UID:SAMPLEUID\r\n" +
                "RECURRENCE-ID;TZID=Europe/Berlin:20190102T100000\r\n" +
                "DTSTART;TZID=Europe/London:20190101T110000\r\n" +
                "DTEND:20190101T170000Z\r\n" +
                "DTSTAMP:20251028T185101Z\r\n" +
                "END:VEVENT\r\n" +
                // time zone: Europe/Berlin
                "BEGIN:VTIMEZONE\r\n" +
                "TZID:Europe/Berlin\r\n" +
                "BEGIN:STANDARD\r\n" +
                "TZNAME:CET\r\n" +
                "TZOFFSETFROM:+0200\r\n" +
                "TZOFFSETTO:+0100\r\n" +
                "DTSTART:19961027T030000\r\n" +
                "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU\r\n" +
                "END:STANDARD\r\n" +
                "BEGIN:DAYLIGHT\r\n" +
                "TZNAME:CEST\r\n" +
                "TZOFFSETFROM:+0100\r\n" +
                "TZOFFSETTO:+0200\r\n" +
                "DTSTART:19810329T020000\r\n" +
                "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU\r\n" +
                "END:DAYLIGHT\r\n" +
                "END:VTIMEZONE\r\n" +
                "BEGIN:VTIMEZONE\r\n" +
                // time zone: Europe/London
                "TZID:Europe/London\r\n" +
                "BEGIN:STANDARD\r\n" +
                "TZNAME:GMT\r\n" +
                "TZOFFSETFROM:+0100\r\n" +
                "TZOFFSETTO:+0000\r\n" +
                "DTSTART:19961027T020000\r\n" +
                "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU\r\n" +
                "END:STANDARD\r\n" +
                "BEGIN:DAYLIGHT\r\n" +
                "TZNAME:BST\r\n" +
                "TZOFFSETFROM:+0000\r\n" +
                "TZOFFSETTO:+0100\r\n" +
                "DTSTART:19810329T010000\r\n" +
                "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU\r\n" +
                "END:DAYLIGHT\r\n" +
                "END:VTIMEZONE\r\n" +
                "END:VCALENDAR\r\n", iCal.toString())
    }

    @Test
    fun `Write event that uses old Kiev timezone`() {
        // Test the special case where Android uses "Europe/Kiev" but ical4j uses "Europe/Kyiv".
        // The output should preserve the original Android timezone name.

        // We will provide Europe/Kiev for ICalendarGenerator
        val tzKiev = ZoneId.of("Europe/Kiev")
        assertEquals("Europe/Kiev", tzKiev.id)

        // Verify that ical4j returns a VTIMEZONE with the new Europe/Kyiv TZID (by alias)
        val tzReg = TimeZoneRegistryFactory.getInstance().createRegistry()
        // We call getTimeZone(Europe/Kiev), but we get VTIMEZONE(Europe/Kyiv):
        assertEquals("Europe/Kyiv", tzReg.getTimeZone(tzKiev.id).id)

        // Generate the iCalendar (must NOT map Europe/Kiev to Europe/Kyiv silently)
        val iCal = StringWriter()
        writer.write(AssociatedEvents(
            main = VEvent(propertyListOf(
                Uid("KIEVTEST"),
                DtStart(ZonedDateTime.of(LocalDateTime.parse("2023-01-01T12:00:00"), tzKiev)),
                DtEnd(ZonedDateTime.of(LocalDateTime.parse("2023-01-01T14:00:00"), tzKiev)),
                DtStamp("20230101T120000Z")
            )),
            prodId = userAgent,
            exceptions = listOf()
        ), iCal)

        // Check TZID of generated VTIMEZONE (must match original timezone ID)
        assertTrue(iCal.toString().contains("BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "PRODID:TestUA/1.0\r\n" +
                "BEGIN:VEVENT\r\n" +
                "UID:KIEVTEST\r\n" +
                "DTSTART;TZID=Europe/Kiev:20230101T120000\r\n" +
                "DTEND;TZID=Europe/Kiev:20230101T140000\r\n" +
                "DTSTAMP:20230101T120000Z\r\n" +
                "END:VEVENT\r\n" +
                "BEGIN:VTIMEZONE\r\n" +
                "TZID:Europe/Kiev\r\n"))     // not Europe/Kyiv!
    }

    // TODO: tests for timeZonesOf

}