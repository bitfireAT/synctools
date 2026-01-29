/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.ProdId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Duration

class TaskTest {

    val testProdId = ProdId(javaClass.name)

    val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()!!
    val tzBerlin: TimeZone = tzRegistry.getTimeZone("Europe/Berlin")!!


    /* generating */

    @Test
    fun testWrite() {
        val t = Task()
        t.uid = "SAMPLEUID"
        t.dtStart = DtStart("20190101T100000", tzBerlin)

        val alarm = VAlarm(Duration.ofHours(-1) /*Dur(0, -1, 0, 0)*/)
        alarm.properties += Action.AUDIO
        t.alarms += alarm

        val os = ByteArrayOutputStream()
        t.write(os, testProdId)
        val raw = os.toString(Charsets.UTF_8.name())

        assertTrue(raw.contains("PRODID:${testProdId.value}"))
        assertTrue(raw.contains("UID:SAMPLEUID"))
        assertTrue(raw.contains("DTSTAMP:"))
        assertTrue(raw.contains("DTSTART;TZID=Europe/Berlin:20190101T100000"))
        assertTrue(
            raw.contains(
                "BEGIN:VALARM\r\n" +
                        "TRIGGER:-PT1H\r\n" +
                        "ACTION:AUDIO\r\n" +
                        "END:VALARM\r\n"
            )
        )
        assertTrue(raw.contains("BEGIN:VTIMEZONE"))
    }


    /* other methods */

    @Test
    fun testAllDay() {
        assertTrue(Task().isAllDay())

        // DTSTART has priority
        assertFalse(Task().apply {
            dtStart = DtStart(DateTime())
        }.isAllDay())
        assertFalse(Task().apply {
            dtStart = DtStart(DateTime())
            due = Due(Date())
        }.isAllDay())
        assertTrue(Task().apply {
            dtStart = DtStart(Date())
        }.isAllDay())
        assertTrue(Task().apply {
            dtStart = DtStart(Date())
            due = Due(DateTime())
        }.isAllDay())

        // if DTSTART is missing, DUE decides
        assertFalse(Task().apply {
            due = Due(DateTime())
        }.isAllDay())
        assertTrue(Task().apply {
            due = Due(Date())
        }.isAllDay())
    }

}