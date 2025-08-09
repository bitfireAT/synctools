/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.Status
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.nio.charset.Charset
import java.time.Duration

class TaskTest {

    val testProdId = ProdId(javaClass.name)

    val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()!!
    val tzBerlin: TimeZone = tzRegistry.getTimeZone("Europe/Berlin")!!
    val tzVienna: TimeZone = tzRegistry.getTimeZone("Europe/Vienna")!!


    /* public interface tests */

    @Test
    fun testCharsets() {
        var t = parseCalendarFile("latin1.ics", Charsets.ISO_8859_1)
        Assert.assertEquals("äöüß", t.summary)

        t = parseCalendarFile("utf8.ics")
        Assert.assertEquals("© äö — üß", t.summary)
        Assert.assertEquals("中华人民共和国", t.location)
    }

    @Test
    fun testDtStartDate_DueDateTime() {
        val t = parseCalendar("BEGIN:VCALENDAR\r\n" +
                "VERSION 2:0\r\n" +
                "BEGIN:VTODO\r\n" +
                "SUMMARY:DTSTART is DATE, but DUE is DATE-TIME\r\n" +
                "DTSTART;VALUE=DATE:20200731\r\n" +
                "DUE;TZID=Europe/Vienna:20200731T234600\r\n" +
                "END:VTODO\r\n" +
                "END:VCALENDAR\r\n")
        Assert.assertEquals("DTSTART is DATE, but DUE is DATE-TIME", t.summary)
        // rewrite DTSTART to DATE-TIME, too
        Assert.assertEquals(DtStart(DateTime("20200731T000000", tzVienna)), t.dtStart)
        Assert.assertEquals(Due(DateTime("20200731T234600", tzVienna)), t.due)
    }

    @Test
    fun testDtStartDateTime_DueDate() {
        val t = parseCalendar("BEGIN:VCALENDAR\r\n" +
                "VERSION 2:0\r\n" +
                "BEGIN:VTODO\r\n" +
                "SUMMARY:DTSTART is DATE-TIME, but DUE is DATE\r\n" +
                "DTSTART;TZID=Europe/Vienna:20200731T235510\r\n" +
                "DUE;VALUE=DATE:20200801\r\n" +
                "END:VTODO\r\n" +
                "END:VCALENDAR\r\n")
        Assert.assertEquals("DTSTART is DATE-TIME, but DUE is DATE", t.summary)
        // rewrite DTSTART to DATE-TIME, too
        Assert.assertEquals(DtStart(DateTime("20200731T235510", tzVienna)), t.dtStart)
        Assert.assertEquals(Due(DateTime("20200801T000000", tzVienna)), t.due)
    }

    @Test
    fun testDueBeforeDtStart() {
        val t = parseCalendar("BEGIN:VCALENDAR\r\n" +
                "VERSION 2:0\r\n" +
                "BEGIN:VTODO\r\n" +
                "SUMMARY:DUE before DTSTART\r\n" +
                "DTSTART;TZID=Europe/Vienna:20200731T234600\r\n" +
                "DUE;TZID=Europe/Vienna:20200731T123000\r\n" +
                "END:VTODO\r\n" +
                "END:VCALENDAR\r\n")
        Assert.assertEquals("DUE before DTSTART", t.summary)
        // invalid tasks with DUE before DTSTART: DTSTART should be set to null
        Assert.assertNull(t.dtStart)
        Assert.assertEquals(Due(DateTime("20200731T123000", tzVienna)), t.due)
    }

    @Test
    fun testDurationWithoutDtStart() {
        val t = parseCalendar("BEGIN:VCALENDAR\r\n" +
                "VERSION 2:0\r\n" +
                "BEGIN:VTODO\r\n" +
                "SUMMARY:DURATION without DTSTART\r\n" +
                "DURATION:PT1H\r\n" +
                "END:VTODO\r\n" +
                "END:VCALENDAR\r\n")
        Assert.assertEquals("DURATION without DTSTART", t.summary)
        Assert.assertNull(t.dtStart)
        Assert.assertNull(t.duration)
    }

    @Test
    fun testEmptyPriority() {
        val t = parseCalendar("BEGIN:VCALENDAR\r\n" +
                "VERSION 2:0\r\n" +
                "BEGIN:VTODO\r\n" +
                "SUMMARY:Empty PRIORITY\r\n" +
                "PRIORITY:\r\n" +
                "END:VTODO\r\n" +
                "END:VCALENDAR\r\n")
        Assert.assertEquals("Empty PRIORITY", t.summary)
        Assert.assertEquals(0, t.priority)
    }

    @Test
    fun testSamples() {
        val t = regenerate(parseCalendarFile("rfc5545-sample1.ics"))
        Assert.assertEquals(2, t.sequence)
        Assert.assertEquals("uid4@example.com", t.uid)
        Assert.assertEquals("mailto:unclesam@example.com", t.organizer!!.value)
        Assert.assertEquals(Due("19980415T000000"), t.due)
        Assert.assertFalse(t.isAllDay())
        Assert.assertEquals(Status.VTODO_NEEDS_ACTION, t.status)
        Assert.assertEquals("Submit Income Taxes", t.summary)
    }

    @Test
    fun testAllFields() {
        // 1. parse the VTODO file
        // 2. generate a new VTODO file from the parsed code
        // 3. parse it again – so we can test parsing and generating at once
        var t = regenerate(parseCalendarFile("most-fields1.ics"))
        Assert.assertEquals(1, t.sequence)
        Assert.assertEquals("most-fields1@example.com", t.uid)
        Assert.assertEquals("Conference Room - F123, Bldg. 002", t.location)
        Assert.assertEquals("37.386013", t.geoPosition!!.latitude.toPlainString())
        Assert.assertEquals("-122.082932", t.geoPosition!!.longitude.toPlainString())
        Assert.assertEquals(
            "Meeting to provide technical review for \"Phoenix\" design.\nHappy Face Conference Room. Phoenix design team MUST attend this meeting.\nRSVP to team leader.",
            t.description
        )
        Assert.assertEquals("http://example.com/principals/jsmith", t.organizer!!.value)
        Assert.assertEquals("http://example.com/pub/calendars/jsmith/mytime.ics", t.url)
        Assert.assertEquals(1, t.priority)
        Assert.assertEquals(Clazz.CONFIDENTIAL, t.classification)
        Assert.assertEquals(Status.VTODO_IN_PROCESS, t.status)
        Assert.assertEquals(25, t.percentComplete)
        Assert.assertEquals(DtStart(Date("20100101")), t.dtStart)
        Assert.assertEquals(Due(Date("20101001")), t.due)
        Assert.assertTrue(t.isAllDay())

        Assert.assertEquals(RRule("FREQ=YEARLY;INTERVAL=2"), t.rRule)
        Assert.assertEquals(2, t.exDates.size)
        Assert.assertTrue(t.exDates.contains(ExDate(DateList("20120101", Value.DATE))))
        Assert.assertTrue(t.exDates.contains(ExDate(DateList("20140101,20180101", Value.DATE))))
        Assert.assertEquals(2, t.rDates.size)
        Assert.assertTrue(t.rDates.contains(RDate(DateList("20100310,20100315", Value.DATE))))
        Assert.assertTrue(t.rDates.contains(RDate(DateList("20100810", Value.DATE))))

        Assert.assertEquals(828106200000L, t.createdAt)
        Assert.assertEquals(840288600000L, t.lastModified)

        Assert.assertArrayEquals(arrayOf("Test", "Sample"), t.categories.toArray())

        val (sibling) = t.relatedTo
        Assert.assertEquals("most-fields2@example.com", sibling.value)
        Assert.assertEquals(RelType.SIBLING, (sibling.getParameter(Parameter.RELTYPE) as RelType))

        val (unknown) = t.unknownProperties
        Assert.assertEquals("X-UNKNOWN-PROP", unknown.name)
        Assert.assertEquals("xxx", unknown.getParameter<Parameter>("param1").value)
        Assert.assertEquals("Unknown Value", unknown.value)

        // other file
        t = regenerate(parseCalendarFile("most-fields2.ics"))
        Assert.assertEquals("most-fields2@example.com", t.uid)
        Assert.assertEquals(DtStart(DateTime("20100101T101010Z")), t.dtStart)
        Assert.assertEquals(
            net.fortuna.ical4j.model.property.Duration(Duration.ofSeconds(4 * 86400 + 3 * 3600 + 2 * 60 + 1) /*Dur(4, 3, 2, 1)*/),
            t.duration
        )
        Assert.assertTrue(t.unknownProperties.isEmpty())
    }


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

        Assert.assertTrue(raw.contains("PRODID:${testProdId.value}"))
        Assert.assertTrue(raw.contains("UID:SAMPLEUID"))
        Assert.assertTrue(raw.contains("DTSTAMP:"))
        Assert.assertTrue(raw.contains("DTSTART;TZID=Europe/Berlin:20190101T100000"))
        Assert.assertTrue(
            raw.contains(
                "BEGIN:VALARM\r\n" +
                        "TRIGGER:-PT1H\r\n" +
                        "ACTION:AUDIO\r\n" +
                        "END:VALARM\r\n"
            )
        )
        Assert.assertTrue(raw.contains("BEGIN:VTIMEZONE"))
    }


    /* other methods */

    @Test
    fun testAllDay() {
        Assert.assertTrue(Task().isAllDay())

        // DTSTART has priority
        Assert.assertFalse(Task().apply {
            dtStart = DtStart(DateTime())
        }.isAllDay())
        Assert.assertFalse(Task().apply {
            dtStart = DtStart(DateTime())
            due = Due(Date())
        }.isAllDay())
        Assert.assertTrue(Task().apply {
            dtStart = DtStart(Date())
        }.isAllDay())
        Assert.assertTrue(Task().apply {
            dtStart = DtStart(Date())
            due = Due(DateTime())
        }.isAllDay())

        // if DTSTART is missing, DUE decides
        Assert.assertFalse(Task().apply {
            due = Due(DateTime())
        }.isAllDay())
        Assert.assertTrue(Task().apply {
            due = Due(Date())
        }.isAllDay())
    }


    /* helpers */

    private fun parseCalendar(iCalendar: String): Task =
            Task.tasksFromReader(StringReader(iCalendar)).first()

    private fun parseCalendarFile(fname: String, charset: Charset = Charsets.UTF_8): Task {
        javaClass.classLoader!!.getResourceAsStream("tasks/$fname").use { stream ->
            return Task.tasksFromReader(InputStreamReader(stream, charset)).first()
        }
    }

    private fun regenerate(t: Task): Task {
        val os = ByteArrayOutputStream()
        t.write(os, testProdId)
        return Task.tasksFromReader(InputStreamReader(ByteArrayInputStream(os.toByteArray()), Charsets.UTF_8)).first()
    }

}