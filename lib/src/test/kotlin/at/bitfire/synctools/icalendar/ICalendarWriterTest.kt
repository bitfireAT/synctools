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
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.util.TimeZones
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.time.Duration

class ICalendarWriterTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzBerlin = tzRegistry.getTimeZone("Europe/Berlin")!!
    private val tzUTC = tzRegistry.getTimeZone(TimeZones.UTC_ID)!!

    private val writer = ICalendarWriter(prodId = javaClass.name)

    @Test
    fun testGenerateEtcUTC() {
        val e = VEvent().apply {
            properties += Uid("etc-utc-test@example.com")
            properties += DtStart("20200926T080000", tzUTC)
            properties += DtEnd("20200926T100000", tzUTC)
            properties += Attendee("mailto:test@example.com")
            components += VAlarm(Duration.ofMinutes(-30))
        }
        val ical = StringWriter()
        writer.write(ComponentList(listOf(e)), ical)

        assertTrue(
            "BEGIN:VTIMEZONE.+BEGIN:STANDARD.+END:STANDARD.+END:VTIMEZONE"
                .toRegex(RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(ical.toString())
        )
    }

    @Test
    fun testWrite() {
        val e = VEvent().apply {
            properties += Uid("SAMPLEUID")
            properties += DtStart("20190101T100000", tzBerlin)
            components += VAlarm(Duration.ofHours(-1))
        }

        val iCal = StringWriter()
        writer.write(ComponentList(listOf(e)), iCal)
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