/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.Property.TRIGGER
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.StringReader
import java.time.Period
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import kotlin.jvm.optionals.getOrNull

class ICalendarTest {

	// current time stamp
	private val currentTime = ZonedDateTime.now()


	@Test
	fun testFromReader_calendarProperties() {
		val calendar = ICalendar.fromReader(
            StringReader(
                "BEGIN:VCALENDAR\n" +
                        "VERSION:2.0\n" +
                        "METHOD:PUBLISH\n" +
                        "PRODID:something\n" +
                        "X-WR-CALNAME:Some Calendar\n" +
                        "COLOR:darkred\n" +
                        "X-APPLE-CALENDAR-COLOR:#123456\n" +
                        "END:VCALENDAR"
            )
		)
		assertEquals("Some Calendar", calendar.getProperty<Property>(ICalendar.CALENDAR_NAME).getOrNull()?.value)
        assertEquals("darkred", calendar.getProperty<Property>(Color.PROPERTY_NAME).getOrNull()?.value)
        assertEquals("#123456", calendar.getProperty<Property>(ICalendar.CALENDAR_COLOR).getOrNull()?.value)
	}

	@Test
	fun testFromReader_invalidProperty() {
		// The GEO property is invalid and should be ignored.
		// The calendar is however parsed without exception.
        assertNotNull(
            ICalendar.fromReader(
                StringReader(
                    "BEGIN:VCALENDAR\n" +
                            "PRODID:something\n" +
                            "VERSION:2.0\n" +
                            "BEGIN:VEVENT\n" +
                            "UID:xxx@example.com\n" +
                            "SUMMARY:Example Event with invalid GEO property\n" +
                            "GEO:37.7957246371765\n" +
                            "END:VEVENT\n" +
                            "END:VCALENDAR"
                )
            )
        )
	}


    @Test
    fun testTimezoneDefToTzId_Valid() {
        assertEquals(
            "US-Eastern", ICalendar.timezoneDefToTzId(
                "BEGIN:VCALENDAR\n" +
                        "PRODID:-//Example Corp.//CalDAV Client//EN\n" +
                        "VERSION:2.0\n" +
                        "BEGIN:VTIMEZONE\n" +
                        "TZID:US-Eastern\n" +
                        "LAST-MODIFIED:19870101T000000Z\n" +
                        "BEGIN:STANDARD\n" +
                        "DTSTART:19671029T020000\n" +
                        "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10\n" +
                        "TZOFFSETFROM:-0400\n" +
                        "TZOFFSETTO:-0500\n" +
                        "TZNAME:Eastern Standard Time (US &amp; Canada)\n" +
                        "END:STANDARD\n" +
                        "BEGIN:DAYLIGHT\n" +
                        "DTSTART:19870405T020000\n" +
                        "RRULE:FREQ=YEARLY;BYDAY=1SU;BYMONTH=4\n" +
                        "TZOFFSETFROM:-0500\n" +
                        "TZOFFSETTO:-0400\n" +
                        "TZNAME:Eastern Daylight Time (US &amp; Canada)\n" +
                        "END:DAYLIGHT\n" +
                        "END:VTIMEZONE\n" +
                        "END:VCALENDAR"
            )
        )
	}

	@Test
	fun testTimezoneDefToTzId_Invalid() {
		// invalid time zone
        assertNull(ICalendar.timezoneDefToTzId("/* invalid content */"))

        // time zone without TZID
        assertNull(
            ICalendar.timezoneDefToTzId(
                "BEGIN:VCALENDAR\n" +
                        "PRODID:-//Inverse inc./SOGo 2.2.10//EN\n" +
                        "VERSION:2.0\n" +
                        "END:VCALENDAR"
            )
        )
    }


	@Test
	fun testVAlarmToMin_TriggerDuration_Negative() {
		// TRIGGER;REL=START:-P1DT1H1M29S
		val (ref, min) = ICalendar.vAlarmToMin(
			VAlarm(Duration("-P1DT1H1M29S").duration),
			DtStart<Temporal>(), null, null, false
		)!!
        assertEquals(Related.START, ref)
        assertEquals(60 * 24 + 60 + 1, min)
	}

	@Test
	fun testVAlarmToMin_TriggerDuration_OnlySeconds() {
		// TRIGGER;REL=START:-PT3600S
		val (ref, min) = ICalendar.vAlarmToMin(
            VAlarm(Duration("-PT3600S").duration),
			DtStart<Temporal>(), null, null, false
		)!!
        assertEquals(Related.START, ref)
        assertEquals(60, min)
	}

	@Test
	fun testVAlarmToMin_TriggerDuration_Positive() {
		// TRIGGER;REL=START:P1DT1H1M30S (alarm *after* start)
		val (ref, min) = ICalendar.vAlarmToMin(
            VAlarm(Duration("P1DT1H1M30S").duration),
			DtStart<Temporal>(), null, null, false
		)!!
        assertEquals(Related.START, ref)
        assertEquals(-(60 * 24 + 60 + 1), min)
	}

	@Test
	fun testVAlarmToMin_TriggerDuration_RelEndAllowed() {
		// TRIGGER;REL=END:-P1DT1H1M30S (caller accepts Related.END)
		val alarm = VAlarm(Duration("-P1DT1H1M30S").duration)
		alarm.getProperty<Trigger>(TRIGGER).getOrNull()?.add<Trigger>(Related.END)
		val (ref, min) = ICalendar.vAlarmToMin(alarm, DtStart<Temporal>(), null, null, true)!!
        assertEquals(Related.END, ref)
        assertEquals(60 * 24 + 60 + 1, min)
	}

	@Test
	fun testVAlarmToMin_TriggerDuration_RelEndNotAllowed() {
		// event with TRIGGER;REL=END:-PT30S (caller doesn't accept Related.END)
		val alarm = VAlarm(Duration("-PT65S").duration)
		alarm.getProperty<Trigger>(TRIGGER).getOrNull()?.add<Trigger>(Related.END)
		val (ref, min) = ICalendar.vAlarmToMin(
			alarm,
			DtStart(currentTime),
			DtEnd(currentTime.plusSeconds(180)),    // 180 sec later
			null,
			false
		)!!
        assertEquals(Related.START, ref)
		// duration of event: 180 s (3 min), 65 s before that -> alarm 1:55 min before start
        assertEquals(-1, min)
	}

	@Test
	fun testVAlarmToMin_TriggerDuration_RelEndNotAllowed_NoDtStart() {
		// event with TRIGGER;REL=END:-PT30S (caller doesn't accept Related.END)
		val alarm = VAlarm(Duration("-PT65S").duration)
		alarm.getProperty<Trigger>(TRIGGER).getOrNull()?.add<Trigger>(Related.END)
        assertNull(ICalendar.vAlarmToMin(alarm, DtStart<Temporal>(), DtEnd(currentTime), null, false))
	}

	@Test
	fun testVAlarmToMin_TriggerDuration_RelEndNotAllowed_NoDuration() {
		// event with TRIGGER;REL=END:-PT30S (caller doesn't accept Related.END)
		val alarm = VAlarm(Duration("-PT65S").duration)
		alarm.getProperty<Trigger>(TRIGGER).getOrNull()?.add<Trigger>(Related.END)
        assertNull(ICalendar.vAlarmToMin(alarm, DtStart(currentTime), null, null, false))
	}

	@Test
	fun testVAlarmToMin_TriggerDuration_RelEndNotAllowed_AfterEnd() {
		// task with TRIGGER;REL=END:-P1DT1H1M30S (caller doesn't accept Related.END; alarm *after* end)
		val alarm = VAlarm(Duration("P1DT1H1M30S").duration)
		alarm.getProperty<Trigger>(TRIGGER).getOrNull()?.add<Trigger>(Related.END)
		val (ref, min) = ICalendar.vAlarmToMin(
			alarm,
			DtStart(currentTime),
			Due(currentTime.plusSeconds(90)),    // 90 sec (should be rounded down to 1 min) later
			null,
			false
		)!!
        assertEquals(Related.START, ref)
        assertEquals(-(60 * 24 + 60 + 1 + 1) /* duration of event: */ - 1, min)
	}

	@Test
	fun testVAlarm_TriggerPeriod() {
		val (ref, min) = ICalendar.vAlarmToMin(
            VAlarm(Period.parse("-P1W1D")),
			DtStart(currentTime), null, null,
			false
		)!!
        assertEquals(Related.START, ref)
        assertEquals(8 * 24 * 60, min)
	}

	@Test
	fun testVAlarm_TriggerAbsoluteValue() {
		// TRIGGER;VALUE=DATE-TIME:<xxxx>
		val alarm = VAlarm(currentTime.minusSeconds(89).toInstant())    // 89 sec (should be cut off to 1 min) before event
		alarm.getProperty<Trigger>(TRIGGER).getOrNull()?.add<Trigger>(Related.END)	// not useful for DATE-TIME values, should be ignored
		val (ref, min) = ICalendar.vAlarmToMin(alarm, DtStart(currentTime), null, null, false)!!
        assertEquals(Related.START, ref)
        assertEquals(1, min)
	}


	// TODO Note: can we use the following now when we have ical4j 4.x?

	/*
	DOES NOT WORK YET! Will work as soon as Java 8 API is consequently used in ical4j and ical4android.

	@Test
	fun testVAlarm_TriggerPeriod_CrossingDST() {
		// Event start: 2020/04/01 01:00 Vienna, alarm: one day before start of the event
		// DST changes on 2020/03/29 02:00 -> 03:00, so there is one hour less!
		// The alarm has to be set 23 hours before the event so that it is set one day earlier.
		val event = Event()
		event.dtStart = DtStart("20200401T010000", tzVienna)
		val (ref, min) = ICalendar.vAlarmToMin(
				VAlarm(Period.parse("-P1W1D")),
				event, false
		)!!
		assertEquals(Related.START, ref)
		assertEquals(8*24*60, min)
	}*/

}