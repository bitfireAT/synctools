/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.ical4android.util.TimeApiExtensions.toZoneIdCompat
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Summary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class Ical4j4Test {

    @Test
    fun testParseCustomZimezone() {
        val tzReg = TimeZoneRegistryFactory.getInstance().createRegistry()
        val iCalWithTzDef = "BEGIN:VCALENDAR\n" +
                "VERSION:2.0\n" +
                "BEGIN:VEVENT\n" +
                "DTSTART;TZID=Custom ABC:20260225T131934\n" +
                "SUMMARY:Test Event\n" +
                "END:VEVENT\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Custom ABC\n" +
                "BEGIN:DAYLIGHT\n" +
                "DTSTART:16010101T020000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=1SU;BYMONTH=3\n" +
                "TZOFFSETFROM:+0100\n" +
                "TZOFFSETTO:+0200\n" +
                "END:DAYLIGHT\n" +
                "BEGIN:STANDARD\n" +
                "DTSTART:16010101T030000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10\n" +
                "TZOFFSETFROM:+0200\n" +
                "TZOFFSETTO:+0100\n" +
                "END:STANDARD\n" +
                "END:VTIMEZONE\n" +
                "END:VCALENDAR\n"
        val calTz = CalendarBuilder(tzReg)
            .build(StringReader(iCalWithTzDef))
        val event = calTz.getComponent<VEvent>(Component.VEVENT).get()
        assertEquals("Test Event", event.getRequiredProperty<Summary>(Property.SUMMARY).value)

        val dtStart = event.getRequiredProperty<DtStart<*>>(Property.DTSTART)
        System.err.println("DTSTART = $dtStart")
        val zdt = dtStart.date as ZonedDateTime
        System.err.println("DTSTART.date = (ZonedDateTime) $zdt")
        val customZone = zdt.zone
        System.err.println("DTSTART.date.zone = $customZone")
        System.err.println("DTSTART.date.zone.rules = ${customZone.rules.transitionRules}")

        // get from registry
        val customTz = tzReg.getTimeZone("Custom ABC")
        assertNotNull(customTz)

        // get from system – it's registered there! (with a random ID like ical4j-local-648)
        val systemCustomZone = ZoneId.of(customZone.id)
        assertEquals(customZone, systemCustomZone)

        // However we can't convert get it from the system by the custom ID – THROWS EXCEPTION!!
        // It's really only registered with the ical4j-local-... name.
        // See ical4j DefaultZoneRulesProvider
        //val tzid = customTz.toZoneIdCompat()
    }

    @Test(expected = Exception::class)
    fun testCreateCustomTimezone() {
        val iCalWithTzDef = "BEGIN:VCALENDAR\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Custom/ABC\n" +
                "BEGIN:DAYLIGHT\n" +
                "DTSTART:16010101T020000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=1SU;BYMONTH=3\n" +
                "TZOFFSETFROM:+0100\n" +
                "TZOFFSETTO:+0200\n" +
                "END:DAYLIGHT\n" +
                "BEGIN:STANDARD\n" +
                "DTSTART:16010101T030000\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10\n" +
                "TZOFFSETFROM:+0200\n" +
                "TZOFFSETTO:+0100\n" +
                "END:STANDARD\n" +
                "END:VTIMEZONE\n" +
                "END:VCALENDAR\n"
        val calTz = CalendarBuilder()
            .build(StringReader(iCalWithTzDef))
        val customVTz = calTz.getComponent<VTimeZone>(Component.VTIMEZONE).get()

        val tzReg = TimeZoneRegistryFactory.getInstance().createRegistry()
        tzReg.register(TimeZone(customVTz))
        val vtz: VTimeZone = tzReg.getTimeZone("EuropeVienna").vTimeZone

        val dt = ZonedDateTime.of(
            /* date = */ LocalDate.of(2026, 2, 25),
            /* time = */ LocalTime.of(12, 43),
            /* zone = */ tzReg.getTimeZone("Custom/ABC").toZoneId()
        )
        val dtStart = DtStart(dt)

        val event = VEvent(false)
        event.add<VEvent>(dtStart)

        val cal = Calendar()
        cal.add<Calendar>(event)

        val writer = StringWriter()
        CalendarOutputter(false).output(cal, writer)
        val iCal = writer.toString()

        assertEquals("BEGIN:VCALENDAR\r\n" +
                "BEGIN:VEVENT\r\n" +
                "DTSTART;TZID=Europe/Vienna:20260225T124300\r\n" +
                "END:VEVENT\r\n" +
                "END:VCALENDAR\r\n", iCal)
    }

    @Test
    fun testCreateSystemTimezone() {
        val dt = ZonedDateTime.of(
            /* date = */ LocalDate.of(2026, 2, 25),
            /* time = */ LocalTime.of(12, 43),
            /* zone = */ ZoneId.of("")
        )
        val dtStart = DtStart(dt)

        val event = VEvent(false)
        event.add<VEvent>(dtStart)

        val cal = Calendar()
        cal.add<Calendar>(event)

        val writer = StringWriter()
        CalendarOutputter(false).output(cal, writer)
        val iCal = writer.toString()

        assertEquals("BEGIN:VCALENDAR\r\n" +
                "BEGIN:VEVENT\r\n" +
                "DTSTART;TZID=Europe/Vienna:20260225T124300\r\n" +
                "END:VEVENT\r\n" +
                "END:VCALENDAR\r\n", iCal)
    }

}