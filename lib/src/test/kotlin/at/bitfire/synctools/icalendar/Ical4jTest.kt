/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.synctools.icalendar.validation.ICalPreprocessor
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TemporalAmountAdapter
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.property.Attendee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.StringReader
import java.time.Period

class Ical4jTest {

    val tzReg = TimeZoneRegistryFactory.getInstance().createRegistry()

    @Test
    fun testEmailParameter() {
        // https://github.com/ical4j/ical4j/issues/418
        val event = ICalendarParser().parse(
            StringReader(
                "BEGIN:VCALENDAR\n" +
                        "VERSION:2.0\n" +
                        "BEGIN:VEVENT\n" +
                        "SUMMARY:Test\n" +
                        "DTSTART;VALUE=DATE:20200702\n" +
                        "ATTENDEE;EMAIL=attendee1@example.virtual:sample:attendee1\n" +
                        "END:VEVENT\n" +
                        "END:VCALENDAR"
            )
        ).getComponent<VEvent>(Component.VEVENT)
        val attendee = event.getProperty<Attendee>(Property.ATTENDEE)
        assertEquals("attendee1@example.virtual", attendee.getParameter<Email>(Parameter.EMAIL).value)
    }

    @Test
    fun testTemporalAmountAdapter_durationToString_DropsMinutes() {
        // https://github.com/ical4j/ical4j/issues/420
        assertEquals("P1DT1H4M", TemporalAmountAdapter.parse("P1DT1H4M").toString())
    }

    @Test(expected = AssertionError::class)
    fun testTemporalAmountAdapter_Months() {
        // https://github.com/ical4j/ical4j/issues/419
        // A month usually doesn't have 4 weeks = 4*7 days = 28 days (except February in non-leap years).
        assertNotEquals("P4W", TemporalAmountAdapter(Period.ofMonths(1)).toString())
    }

    @Test(expected = AssertionError::class)
    fun testTemporalAmountAdapter_Year() {
        // https://github.com/ical4j/ical4j/issues/419
        // A year has 365 or 366 days, but never 52 weeks = 52*7 days = 364 days.
        assertNotEquals("P52W", TemporalAmountAdapter(Period.ofYears(1)).toString())
    }

    @Test(expected = AssertionError::class)
    fun testTzDarwin() {
        val darwin = tzReg.getTimeZone("Australia/Darwin")

        val ts1 = 1616720400000
        assertEquals(9.5, darwin.getOffset(ts1) / 3600000.0, .01)

        val dt2 = DateTime("20210326T103000", darwin)
        assertEquals(1616720400000, dt2.time)
    }

    @Test
    fun testTzDublin_negativeDst() {
        // https://github.com/ical4j/ical4j/issues/493
        // fixed by enabling net.fortuna.ical4j.timezone.offset.negative_dst_supported in ical4j.properties
        val vtzFromGoogle = "BEGIN:VCALENDAR\n" +
                "CALSCALE:GREGORIAN\n" +
                "VERSION:2.0\n" +
                "PRODID:-//Google Inc//Google Calendar 70.9054//EN\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Europe/Dublin\n" +
                "BEGIN:STANDARD\n" +
                "TZOFFSETFROM:+0000\n" +
                "TZOFFSETTO:+0100\n" +
                "TZNAME:IST\n" +
                "DTSTART:19700329T010000\n" +
                "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "TZOFFSETFROM:+0100\n" +
                "TZOFFSETTO:+0000\n" +
                "TZNAME:GMT\n" +
                "DTSTART:19701025T020000\n" +
                "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE\n" +
                "END:VCALENDAR"
        val iCalFromGoogle = CalendarBuilder().build(StringReader(vtzFromGoogle))
        val dublinFromGoogle = iCalFromGoogle.getComponent(Component.VTIMEZONE) as VTimeZone
        val dt = DateTime("20210108T151500", TimeZone(dublinFromGoogle))
        assertEquals("20210108T151500", dt.toString())
    }

    @Test
    fun `DTSTART in America_Asuncion from KOrganizer`() {
        // See https://github.com/bitfireAT/synctools/issues/113
        val vtzFromKOrganizer = "BEGIN:VCALENDAR\n" +
                "CALSCALE:GREGORIAN\n" +
                "VERSION:2.0\n" +
                "PRODID:-//K Desktop Environment//NONSGML KOrganizer 6.5.0 (25.08.0)//EN\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:America/Asuncion\n" +
                "BEGIN:STANDARD\n" +
                "TZNAME:-03\n" +
                "TZOFFSETFROM:-0300\n" +
                "TZOFFSETTO:-0300\n" +
                "DTSTART:19700101T000000\n" +
                "END:STANDARD\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "DTSTAMP:20250828T233827Z\n" +
                "CREATED:20250828T233750Z\n" +
                "UID:e5d424b9-d3f6-4ee0-bf95-da7537fca1fe\n" +
                "LAST-MODIFIED:20250828T233827Z\n" +
                "SUMMARY:Test Timezones\n" +
                "RRULE:FREQ=WEEKLY;COUNT=3;BYDAY=TH\n" +
                "DTSTART;TZID=America/Asuncion:20250828T130000\n" +
                "DTEND;TZID=America/Asuncion:20250828T133000\n" +
                "TRANSP:OPAQUE\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR"
        val iCalFromKOrganizer = CalendarBuilder().build(StringReader(vtzFromKOrganizer))
        ICalPreprocessor().preprocessCalendar(iCalFromKOrganizer)
        val vEvent = iCalFromKOrganizer.getComponent<VEvent>(Component.VEVENT)
        val dtStart = vEvent.startDate
        // SHOULD BE UTC -3:
        // assertEquals(1756396800000, dtStart.date.time)
        // However is one hour later: 1756400400000
    }

    @Test
    fun testTzKarachi() {
        // https://github.com/ical4j/ical4j/issues/491
        val karachi = tzReg.getTimeZone("Asia/Karachi")

        val ts1 = 1609945200000
        assertEquals(5, karachi.getOffset(ts1) / 3600000)

        val dt2 = DateTime("20210106T200000", karachi)
        assertEquals(1609945200000, dt2.time)
    }

    @Test(expected = ParserException::class)
    fun `Unparseable event with timezone with RDATE with PERIOD`() {
        CalendarBuilder().build(
            StringReader(
                "BEGIN:VCALENDAR\n" +
                        "VERSION:2.0\n" +
                        "BEGIN:VTIMEZONE\n" +
                        "TZID:Europe/Berlin\n" +
                        "X-TZINFO:Europe/Berlin[2025b]\n" +
                        "BEGIN:STANDARD\n" +
                        "DTSTART:18930401T000000\n" +
                        "RDATE;VALUE=PERIOD:18930401T000000/18930402T000000\n" +
                        "TZNAME:Europe/Berlin(STD)\n" +
                        "TZOFFSETFROM:+005328\n" +
                        "TZOFFSETTO:+0100\n" +
                        "END:STANDARD\n" +
                        "END:VTIMEZONE\n" +
                        "BEGIN:VEVENT\n" +
                        "UID:3b3c1b0e-e74c-48ef-ada8-33afc543648d\n" +
                        "DTSTART;TZID=Europe/Berlin:20250917T122000\n" +
                        "DTEND;TZID=Europe/Berlin:20250917T124500\n" +
                        "END:VEVENT\n" +
                        "END:VCALENDAR"
            )
        )
    }

}