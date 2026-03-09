/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Property.TRIGGER
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.Trigger
import net.fortuna.ical4j.util.TimeZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Test
import java.io.StringReader
import java.time.LocalDateTime
import java.time.Period
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import kotlin.jvm.optionals.getOrNull

class ICalendarTest {

	// UTC timezone
	private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
	val tzUTC = tzRegistry.getTimeZone(TimeZones.UTC_ID)!!
	private val vtzUTC = tzUTC.vTimeZone

	// Austria (Europa/Vienna) uses DST regularly
	private val vtzVienna = readTimeZone("Vienna.ics")

	// Pakistan (Asia/Karachi) used DST only in 2002, 2008 and 2009; no known future occurrences
	private val vtzKarachi = readTimeZone("Karachi.ics")

	// Somalia (Africa/Mogadishu) has never used DST
	private val vtzMogadishu = readTimeZone("Mogadishu.ics")

	// current time stamp
	private val currentTime = ZonedDateTime.now()


	private fun readTimeZone(fileName: String): VTimeZone {
		javaClass.classLoader!!.getResourceAsStream("tz/$fileName").use { tzStream ->
			val cal = CalendarBuilder().build(tzStream)
			val vTimeZone = cal.getComponent<VTimeZone>(Component.VTIMEZONE).get()
			return vTimeZone
		}
	}

	@Ignore("ical4j 4.x")
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
		TODO("ical4j 4.x")
        /*assertEquals("Some Calendar", calendar.getProperty<Property>(ICalendar.CALENDAR_NAME).value)
        assertEquals("darkred", calendar.getProperty<Property>(Color.PROPERTY_NAME).value)
        assertEquals("#123456", calendar.getProperty<Property>(ICalendar.CALENDAR_COLOR).value)*/
	}

	@Ignore("ical4j 4.x")
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
	fun testMinifyVTimezone_UTC() {
		// Keep the only observance for UTC.
		// DATE-TIME values in UTC are usually noted with ...Z and don't have a VTIMEZONE,
		// but it is allowed to write them as TZID=Etc/UTC.
        assertEquals(1, vtzUTC.observances.size)

		val minified = ICalendar.minifyVTimeZone(vtzUTC, vtzUTC.zonedDateTime("2020-06-12T00:00"))

		assertEquals(1, minified.observances.size)
	}

	@Test
	fun testMinifyVTimezone_removeObsoleteDstObservances() {
		// Remove obsolete observances when DST is used.
        assertEquals(6, vtzVienna.observances.size)
		// By default, the earliest observance is in 1893. We can drop that for events in 2020.
        assertEquals(LocalDateTime.parse("1893-04-01T00:00:00"), vtzVienna.observances.minOfOrNull { it.startDate.date })

		val minified = ICalendar.minifyVTimeZone(vtzVienna, vtzVienna.zonedDateTime("2020-01-01"))

		assertEquals(2, minified.observances.size)
		// now earliest observance for STANDARD/DAYLIGHT is 1996/1981
		assertEquals(LocalDateTime.parse("1996-10-27T03:00:00"), minified.observances[0].startDate.date)
		assertEquals(LocalDateTime.parse("1981-03-29T02:00:00"), minified.observances[1].startDate.date)
	}

	@Test
	fun testMinifyVTimezone_removeObsoleteObservances() {
		// Remove obsolete observances when DST is not used. Mogadishu had several time zone changes,
		// but now there is a simple offest without DST.
        assertEquals(4, vtzMogadishu.observances.size)

		val minified = ICalendar.minifyVTimeZone(vtzMogadishu, vtzMogadishu.zonedDateTime("1961-10-01"))

		assertEquals(1, minified.observances.size)
	}

	@Test
	fun testMinifyVTimezone_keepFutureObservances() {
		// Keep future observances.
		ICalendar.minifyVTimeZone(vtzVienna, vtzVienna.zonedDateTime("1975-10-01")).let { minified ->
			val sortedStartDates = minified.observances
				.map { it.startDate.date }
				.sorted()
				.map { it.toString() }

			assertEquals(
				listOf("1916-04-30T23:00", "1916-10-01T01:00", "1981-03-29T02:00", "1996-10-27T03:00"),
				sortedStartDates
			)
		}

		ICalendar.minifyVTimeZone(vtzKarachi, vtzKarachi.zonedDateTime("1961-10-01")).let { minified ->
            assertEquals(4, minified.observances.size)
		}

		ICalendar.minifyVTimeZone(vtzKarachi, vtzKarachi.zonedDateTime("1975-10-01")).let { minified ->
            assertEquals(3, minified.observances.size)
		}

		ICalendar.minifyVTimeZone(vtzMogadishu, vtzMogadishu.zonedDateTime("1931-10-01")).let { minified ->
            assertEquals(3, minified.observances.size)
		}
	}

	@Test
	fun testMinifyVTimezone_keepDstWhenStartInDst() {
		// Keep DST when there are no obsolete observances, but start time is in DST.
		ICalendar.minifyVTimeZone(vtzKarachi, vtzKarachi.zonedDateTime("2009-10-31")).let { minified ->
            assertEquals(2, minified.observances.size)
		}
	}

	@Test
	fun testMinifyVTimezone_removeDstWhenNotUsedAnymore() {
		// Remove obsolete observances (including DST) when DST is not used anymore.
		ICalendar.minifyVTimeZone(vtzKarachi, vtzKarachi.zonedDateTime("2010-01-01")).let { minified ->
            assertEquals(1, minified.observances.size)
		}
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

private fun VTimeZone.zonedDateTime(text: String): ZonedDateTime {
	val dateTimeText = if ('T' in text) text else "${text}T00:00:00"
	return LocalDateTime.parse(dateTimeText).atZone(TimeZone(this).toZoneId())
}