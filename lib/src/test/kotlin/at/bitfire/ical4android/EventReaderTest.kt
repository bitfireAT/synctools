/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.ical4android.util.DateUtils
import at.bitfire.synctools.icalendar.Css3Color
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.parameter.Email
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.nio.charset.Charset

class EventReaderTest {

    val reader = EventReader()

    @Test
    fun testCalendarProperties() {
        javaClass.getResourceAsStream("/events/multiple.ics").use { stream ->
            val properties = mutableMapOf<String, String>()
            reader.readEvents(InputStreamReader(stream, Charsets.UTF_8), properties)
            assertEquals(1, properties.size)
            assertEquals("Test-Kalender", properties[ICalendar.CALENDAR_NAME])
        }
    }

    @Test
    fun testCharsets() {
        var e = parseCalendar("latin1.ics", Charsets.ISO_8859_1)[0]
        assertEquals("äöüß", e.summary)

        e = parseCalendar("utf8.ics").first()
        assertEquals("© äö — üß", e.summary)
        assertEquals("中华人民共和国", e.location)
    }

    @Test
    fun testDstOnlyVtimezone() {
        // see https://github.com/ical4j/ical4j/issues/230
        val events = parseCalendar("dst-only-vtimezone.ics")
        assertEquals(1, events.size)
        val e = events.first()
        assertEquals("only-dst@example.com", e.uid)
        val dtStart = e.dtStart!!
        assertEquals("Europe/Berlin", dtStart.timeZone.id)
        assertEquals(1522738800000L, dtStart.date.time)
    }

    @Test
    fun testGrouping() {
        val events = parseCalendar("multiple.ics")
        assertEquals(3, events.size)

        var e = findEvent(events, "multiple-0@ical4android.EventTest")
        assertEquals("Event 0", e.summary)
        assertEquals(0, e.exceptions.size)

        e = findEvent(events, "multiple-1@ical4android.EventTest")
        assertEquals("Event 1", e.summary)
        assertEquals(1, e.exceptions.size)
        assertEquals("Event 1 Exception", e.exceptions.first().summary)

        e = findEvent(events, "multiple-2@ical4android.EventTest")
        assertEquals("Event 2", e.summary)
        assertEquals(2, e.exceptions.size)
        assertTrue("Event 2 Updated Exception 1" == e.exceptions.first().summary || "Event 2 Updated Exception 1" == e.exceptions[1].summary)
        assertTrue("Event 2 Exception 2" == e.exceptions.first().summary || "Event 2 Exception 2" == e.exceptions[1].summary)
    }


    @Test
    fun testParse() {
        val event = parseCalendar("utf8.ics").first()
        assertEquals("utf8@ical4android.EventTest", event.uid)
        assertEquals("© äö — üß", event.summary)
        assertEquals("Test Description", event.description)
        assertEquals("中华人民共和国", event.location)
        assertEquals(Css3Color.aliceblue, event.color)
        assertEquals("cyrus@example.com", event.attendees.first().parameters.getParameter<Email>("EMAIL").value)

        val (unknown) = event.unknownProperties
        assertEquals("X-UNKNOWN-PROP", unknown.name)
        assertEquals("xxx", unknown.getParameter<Parameter>("param1").value)
        assertEquals("Unknown Value", unknown.value)
    }

    @Test
    fun testRecurringWithException() {
        val event = parseCalendar("recurring-with-exception1.ics").first()
        assertTrue(DateUtils.isDate(event.dtStart))

        assertEquals(1, event.exceptions.size)
        val (exception) = event.exceptions
        assertEquals("20150503", exception.recurrenceId!!.value)
        assertEquals("Another summary for the third day", exception.summary)
    }

    @Test
    fun testRecurringOnlyException() {
        val event = parseCalendar("recurring-only-exception.ics").first()

        assertEquals(1, event.exceptions.size)
        val (exception) = event.exceptions
        assertEquals("20150503T010203Z", exception.recurrenceId!!.value)
        assertEquals("This is an exception", exception.summary)

        // fake main event
        assertEquals(event.summary, exception.summary)
        assertEquals(event.dtStart, exception.dtStart)
        assertEquals(event.dtEnd, exception.dtEnd)
    }

    @Test
    fun testStartEndTimes() {
        // event with start+end date-time
        val eViennaEvolution = parseCalendar("vienna-evolution.ics").first()
        assertEquals(1381330800000L, eViennaEvolution.dtStart!!.date.time)
        assertEquals("Europe/Vienna", eViennaEvolution.dtStart!!.timeZone.id)
        assertEquals(1381334400000L, eViennaEvolution.dtEnd!!.date.time)
        assertEquals("Europe/Vienna", eViennaEvolution.dtEnd!!.timeZone.id)
    }

    @Test
    fun testStartEndTimesAllDay() {
        // event with start date only
        val eOnThatDay = parseCalendar("event-on-that-day.ics").first()
        assertEquals(868838400000L, eOnThatDay.dtStart!!.date.time)
        assertNull(eOnThatDay.dtStart!!.timeZone)

        // event with start+end date for all-day event (one day)
        val eAllDay1Day = parseCalendar("all-day-1day.ics").first()
        assertEquals(868838400000L, eAllDay1Day.dtStart!!.date.time)
        assertNull(eAllDay1Day.dtStart!!.timeZone)
        assertEquals(868838400000L + 86400000, eAllDay1Day.dtEnd!!.date.time)
        assertNull(eAllDay1Day.dtEnd!!.timeZone)

        // event with start+end date for all-day event (ten days)
        val eAllDay10Days = parseCalendar("all-day-10days.ics").first()
        assertEquals(868838400000L, eAllDay10Days.dtStart!!.date.time)
        assertNull(eAllDay10Days.dtStart!!.timeZone)
        assertEquals(868838400000L + 10 * 86400000, eAllDay10Days.dtEnd!!.date.time)
        assertNull(eAllDay10Days.dtEnd!!.timeZone)

        // event with start+end date on some day (0 sec-event)
        val eAllDay0Sec = parseCalendar("all-day-0sec.ics").first()
        assertEquals(868838400000L, eAllDay0Sec.dtStart!!.date.time)
        assertNull(eAllDay0Sec.dtStart!!.timeZone)
        // DTEND is same as DTSTART which is not valid for Android – but this will be handled by AndroidEvent, not Event
        assertEquals(868838400000L, eAllDay0Sec.dtEnd!!.date.time)
        assertNull(eAllDay0Sec.dtEnd!!.timeZone)
    }

    @Test
    fun testUnfolding() {
        val e = parseCalendar("two-line-description-without-crlf.ics").first()
        assertEquals("http://www.tgbornheim.de/index.php?sessionid=&page=&id=&sportcentergroup=&day=6", e.description)
    }

    @Test
    fun testFindMainEventsAndExceptions() {
        // two single events
        var events = parseCalendar("two-events-without-exceptions.ics")
        assertEquals(2, events.size)
        for (event in events)
            assertTrue(event.exceptions.isEmpty())

        // one event with exception, another single event
        events = parseCalendar("one-event-with-exception-one-without.ics")
        assertEquals(2, events.size)
        for (event in events) {
            val uid = event.uid
            if ("event1" == uid)
                assertEquals(1, event.exceptions.size)
            else
                assertTrue(event.exceptions.isEmpty())
        }

        // one event two exceptions (thereof one updated two times) and updated exception, another single event
        events = parseCalendar("one-event-with-multiple-exceptions-one-without.ics")
        assertEquals(2, events.size)
        for (event in events) {
            val uid = event.uid
            if ("event1" == uid) {
                assertEquals(2, event.exceptions.size)
                for (exception in event.exceptions)
                    if ("20150503" == exception.recurrenceId!!.value) {
                        assertEquals(2, exception.sequence)
                        assertEquals("Final summary", exception.summary)
                    }
            } else
                assertTrue(event.exceptions.isEmpty())
        }
    }


    // helpers

    private fun findEvent(events: Iterable<Event>, uid: String): Event {
        for (event in events)
            if (uid == event.uid)
                return event
        throw FileNotFoundException()
    }

    private fun parseCalendar(fname: String, charset: Charset = Charsets.UTF_8): List<Event> =
        javaClass.getResourceAsStream("/events/$fname").use { stream ->
            return reader.readEvents(InputStreamReader(stream, charset))
        }

}