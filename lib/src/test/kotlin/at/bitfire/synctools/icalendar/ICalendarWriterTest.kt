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
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.util.TimeZones
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringWriter
import java.time.Duration

class ICalendarWriterTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzBerlin = tzRegistry.getTimeZone("Europe/Berlin")!!
    private val tzLondon = tzRegistry.getTimeZone("Europe/London")!!
    private val tzUTC = tzRegistry.getTimeZone(TimeZones.UTC_ID)!!

    private val userAgent = "TestUA/1.0"
    private val writer = ICalendarWriter()

    @Test
    fun `Write event with exceptions and various timezones`() {
        val iCal = StringWriter()
        writer.write(AssociatedEvents(
            main =
                VEvent(propertyListOf(
                    Uid("SAMPLEUID"),
                    DtStart("20190101T100000", tzBerlin),
                    DtEnd("20190101T160000Z"),
                    DtStamp("20251028T185101Z"),
                    RRule("FREQ=DAILY;COUNT=5")
                ), ComponentList(listOf(
                    VAlarm(Duration.ofHours(-1))
                ))),
            exceptions = listOf(
                VEvent(propertyListOf(
                    Uid("SAMPLEUID"),
                    RecurrenceId("20190102T100000", tzBerlin),
                    DtStart("20190101T110000", tzLondon),
                    DtEnd("20190101T170000Z"),
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

}