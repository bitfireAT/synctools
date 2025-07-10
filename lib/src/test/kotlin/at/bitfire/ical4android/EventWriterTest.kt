/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.util.TimeZones
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.time.Duration

class EventWriterTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzBerlin = tzRegistry.getTimeZone("Europe/Berlin")!!
    private val tzLondon = tzRegistry.getTimeZone("Europe/London")!!
    private val tzUTC = tzRegistry.getTimeZone(TimeZones.UTC_ID)!!

    private val writer = EventWriter(prodId = ProdId(javaClass.name))

    @Test
    fun testGenerateEtcUTC() {
        val e = Event()
        e.uid = "etc-utc-test@example.com"
        e.dtStart = DtStart("20200926T080000", tzUTC)
        e.dtEnd = DtEnd("20200926T100000", tzUTC)
        e.alarms += VAlarm(Duration.ofMinutes(-30))
        e.attendees += Attendee("mailto:test@example.com")
        val ical = StringWriter()
        writer.write(e, ical)

        assertTrue(
            "BEGIN:VTIMEZONE.+BEGIN:STANDARD.+END:STANDARD.+END:VTIMEZONE"
                .toRegex(RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(ical.toString())
        )
    }

    @Test
    fun testRecurringWriteFullDayException() {
        val event = Event().apply {
            uid = "test1"
            dtStart = DtStart("20190117T083000", tzBerlin)
            summary = "Main event"
            rRules += RRule("FREQ=DAILY;COUNT=5")
            exceptions += arrayOf(
                Event().apply {
                    uid = "test2"
                    recurrenceId = RecurrenceId(DateTime("20190118T073000", tzLondon))
                    summary = "Normal exception"
                },
                Event().apply {
                    uid = "test3"
                    recurrenceId = RecurrenceId(Date("20190223"))
                    summary = "Full-day exception"
                }
            )
        }
        val iCal = StringWriter().let {
            writer.write(event, it)
            it.toString()
        }
        assertTrue(iCal.contains("UID:test1\r\n"))
        assertTrue(iCal.contains("DTSTART;TZID=Europe/Berlin:20190117T083000\r\n"))

        // first RECURRENCE-ID has been rewritten
        // - to main event's UID
        // - to time zone Europe/Berlin (with one hour time difference)
        assertTrue(iCal.contains("UID:test1\r\n" +
                "RECURRENCE-ID;TZID=Europe/Berlin:20190118T083000\r\n" +
                "SUMMARY:Normal exception\r\n" +
                "END:VEVENT"))

        // no RECURRENCE-ID;VALUE=DATE:20190223
        assertFalse(iCal.contains(":20190223"))
    }

    @Test
    fun testWrite() {
        val e = Event()
        e.uid = "SAMPLEUID"
        e.dtStart = DtStart("20190101T100000", tzBerlin)
        e.alarms += VAlarm(Duration.ofHours(-1))

        val iCal = StringWriter()
        writer.write(e, iCal)
        val raw = iCal.toString()

        assertTrue(raw.contains("PRODID:${javaClass.name}"))
        assertTrue(raw.contains("UID:SAMPLEUID"))
        assertTrue(raw.contains("DTSTART;TZID=Europe/Berlin:20190101T100000"))
        assertTrue(raw.contains("DTSTAMP:"))
        assertTrue(raw.contains("BEGIN:VALARM\r\n" +
                "TRIGGER:-PT1H\r\n" +
                "END:VALARM\r\n"))
        assertTrue(raw.contains("BEGIN:VTIMEZONE"))
    }

}