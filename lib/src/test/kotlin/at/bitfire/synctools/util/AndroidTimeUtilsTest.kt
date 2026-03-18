/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.util

import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.ExDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Optional

class AndroidTimeUtilsTest {

    val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()!!
    val tzBerlin: TimeZone = tzRegistry.getTimeZone("Europe/Berlin")!!
    val tzToronto: TimeZone = tzRegistry.getTimeZone("America/Toronto")!!

    val tzCustom: TimeZone by lazy {
        val builder = CalendarBuilder(tzRegistry)
        val cal = builder.build(
            StringReader(
                "BEGIN:VCALENDAR\n" +
                        "BEGIN:VTIMEZONE\n" +
                        "TZID:CustomTime\n" +
                        "BEGIN:STANDARD\n" +
                        "TZOFFSETFROM:+0310\n" +
                        "TZOFFSETTO:+0310\n" +
                        "DTSTART:19600101T000000\n" +
                        "END:STANDARD\n" +
                        "END:VTIMEZONE\n" +
                        "END:VCALENDAR"
            )
        )
        TODO("ical4j 4.x")
        //TimeZone(cal.getComponent(VTimeZone.VTIMEZONE) as VTimeZone)
    }

    val tzIdDefault = java.util.TimeZone.getDefault().id!!
    val tzDefault = tzRegistry.getTimeZone(tzIdDefault)!!

    val exDateGenerator: ((DateList<*>) -> ExDate<*>) = { dateList ->
        val parameters = buildList {
            val firstTemporal = dateList.dates.firstOrNull()
            if (firstTemporal is ZonedDateTime) {
                add(TzId(firstTemporal.zone.id))
            }
            if (firstTemporal is LocalDate) {
                add(Value.DATE)
            }
        }

        ExDate(ParameterList(parameters), dateList)
    }

    // androidifyTimeZone
    // DateListProperty - date

    /*@Test
    fun testAndroidifyTimeZone_DateListProperty_Date() {
        // dates (without time) should be ignored
        val rDate = RDate(DateList("20150101,20150102", Value.DATE))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(1420070400000L, rDate.dates[0].time)
        assertEquals(1420156800000L, rDate.dates[1].time)
        assertNull(rDate.timeZone)
        assertEquals(Value.DATE, rDate.dates.type)
        assertNull(rDate.dates.timeZone)
        assertFalse(rDate.dates.isUtc)
    }

    // androidifyTimeZone
    // DateListProperty - date-time

    @Test
    fun testAndroidifyTimeZone_DateListProperty_KnownTimeZone() {
        // times with known time zone should be unchanged
        val rDate = RDate(DateList("20150101T150000,20150102T150000", Value.DATE_TIME, tzToronto))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(1420142400000L, rDate.dates[0].time)
        assertEquals(1420228800000L, rDate.dates[1].time)
        assertEquals(tzToronto, rDate.timeZone)
        assertEquals(Value.DATE_TIME, rDate.dates.type)
        assertEquals(tzToronto, rDate.dates.timeZone)
        assertFalse(rDate.dates.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateListProperty_UnknownTimeZone() {
        // time zone that is not available on Android systems should be rewritten to system default
        val rDate = RDate(DateList("20150101T031000,20150102T031000", Value.DATE_TIME, tzCustom))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(DateTime("20150101T031000", tzCustom).time, rDate.dates[0].time)
        assertEquals(DateTime("20150102T031000", tzCustom).time, rDate.dates[1].time)
        assertEquals(tzIdDefault, rDate.timeZone.id)
        assertEquals(Value.DATE_TIME, rDate.dates.type)
        assertEquals(tzIdDefault, rDate.dates.timeZone.id)
        assertFalse(rDate.dates.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateListProperty_FloatingTime() {
        // times with floating time should be treated as system default time zone
        val rDate = RDate(DateList("20150101T031000,20150102T031000", Value.DATE_TIME))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(DateTime("20150101T031000", tzDefault).time, rDate.dates[0].time)
        assertEquals(DateTime("20150102T031000", tzDefault).time, rDate.dates[1].time)
        assertEquals(tzIdDefault, rDate.timeZone.id)
        assertEquals(Value.DATE_TIME, rDate.dates.type)
        assertEquals(tzIdDefault, rDate.dates.timeZone.id)
        assertFalse(rDate.dates.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateListProperty_UTC() {
        // times with UTC should be unchanged
        val rDate = RDate(DateList("20150101T031000Z,20150102T031000Z", Value.DATE_TIME))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(DateTime("20150101T031000Z").time, rDate.dates[0].time)
        assertEquals(DateTime("20150102T031000Z").time, rDate.dates[1].time)
        assertNull(rDate.timeZone)
        assertEquals(Value.DATE_TIME, rDate.dates.type)
        assertNull(rDate.dates.timeZone)
        assertTrue(rDate.dates.isUtc)
    }

    // androidifyTimeZone
    // DateListProperty - period-explicit

    @Test
    fun testAndroidifyTimeZone_DateListProperty_Period_FloatingTime() {
        // times with floating time should be treated as system default time zone
        val rDate = RDate(PeriodList("19970101T180000/19970102T070000,20220103T000000/20220108T000000"))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(
            setOf(
                Period(DateTime("19970101T18000000"), DateTime("19970102T07000000")),
                Period(DateTime("20220103T000000"), DateTime("20220108T000000"))
            ),
            rDate.periods
        )
        assertNull(rDate.timeZone)
        assertNull(rDate.periods.timeZone)
        assertTrue(rDate.periods.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateListProperty_Period_KnownTimezone() {
        // periods with known time zone should be unchanged
        val rDate = RDate(PeriodList("19970101T180000/19970102T070000,19970102T180000/19970108T090000"))
        rDate.periods.timeZone = tzToronto
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(
            setOf(Period("19970101T180000/19970102T070000"), Period("19970102T180000/19970108T090000")),
            mutableSetOf<Period>().also { it.addAll(rDate.periods) }
        )
        assertEquals(tzToronto, rDate.periods.timeZone)
        assertNull(rDate.timeZone)
        assertFalse(rDate.dates.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateListProperty_Periods_UnknownTimeZone() {
        // time zone that is not available on Android systems should be rewritten to system default
        val rDate = RDate(PeriodList("19970101T180000/19970102T070000,19970102T180000/19970108T090000"))
        rDate.periods.timeZone = tzCustom
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(
            setOf(Period("19970101T180000/19970102T070000"), Period("19970102T180000/19970108T090000")),
            mutableSetOf<Period>().also { it.addAll(rDate.periods) }
        )
        assertEquals(tzIdDefault, rDate.periods.timeZone.id)
        assertNull(rDate.timeZone)
        assertFalse(rDate.dates.isUtc)
    }

    @Test
    fun testAndroidifyTimeZone_DateListProperty_Period_UTC() {
        // times with UTC should be unchanged
        val rDate = RDate(PeriodList("19970101T180000Z/19970102T070000Z,20220103T0000Z/20220108T0000Z"))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(
            setOf(
                Period(DateTime("19970101T180000Z"), DateTime("19970102T070000Z")),
                Period(DateTime("20220103T0000Z"), DateTime("20220108T0000Z"))
            ),
            rDate.periods
        )
        assertTrue(rDate.periods.isUtc)
    }

    // androidifyTimeZone
    // DateListProperty - period-start

    @Test
    fun testAndroidifyTimeZone_DateListProperty_PeriodStart_UTC() {
        // times with UTC should be unchanged
        val rDate = RDate(PeriodList("19970101T180000Z/PT5H30M,20220103T0000Z/PT2H30M10S"))
        AndroidTimeUtils.androidifyTimeZone(rDate)
        assertEquals(
            setOf(
                Period(DateTime("19970101T180000Z"), Duration.parse("PT5H30M")),
                Period(DateTime("20220103T0000Z"), Duration.parse("PT2H30M10S"))
            ),
            rDate.periods
        )
        assertTrue(rDate.periods.isUtc)
    }

    // storageTzId

    @Test
    fun testStorageTzId_Date() =
        assertEquals(AndroidTimeUtils.TZID_UTC, AndroidTimeUtils.storageTzId(DtStart(Date("20150101"))))

    @Test
    fun testStorageTzId_FloatingTime() =
        assertEquals(TimeZone.getDefault().id, AndroidTimeUtils.storageTzId(DtStart(DateTime("20150101T000000"))))
*/

    // androidStringToRecurrenceSets

    @Test
    fun testAndroidStringToRecurrenceSets_UtcTimes() {
        // list of UTC times
        val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(
            "20150101T103010Z,20150702T103020Z",
            tzRegistry,
            allDay = false,
            generator = exDateGenerator,
        )

        assertEquals(Optional.empty<TzId>(), exDate.getParameter<TzId>(Parameter.TZID))
        assertEquals(2, exDate.dates.size)
        assertEquals(Instant.parse("2015-01-01T10:30:10Z"), exDate.dates[0])
        assertEquals(Instant.parse("2015-07-02T10:30:20Z"), exDate.dates[1])
    }

    @Test
    fun testAndroidStringToRecurrenceSets_ZonedTimes() {
        // list of time zone times
        val tzid = tzToronto.id

        val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(
            "$tzid;20150103T113030,20150704T113040",
            tzRegistry,
            allDay = false,
            generator = exDateGenerator,
        )

        assertEquals(tzid, exDate.getParameter<TzId>(Parameter.TZID).get().value)
        assertEquals(2, exDate.dates.size)
        assertEquals(dateTimeValue("20150103T113030", tzToronto), exDate.dates[0])
        assertEquals(dateTimeValue("20150704T113040", tzToronto), exDate.dates[1])
    }

    @Test
    fun testAndroidStringToRecurrenceSets_Dates() {
        // list of dates
        val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(
            "20150101T103010Z,20150702T103020Z",
            tzRegistry,
            allDay = true,
            generator = exDateGenerator,
        )

        assertEquals(Optional.empty<TzId>(), exDate.getParameter<TzId>(Parameter.TZID))
        assertEquals(Value.DATE, exDate.getParameter<Value>(Parameter.VALUE).get())
        assertEquals(2, exDate.dates.size)
        assertEquals(dateValue("20150101"), exDate.dates[0])
        assertEquals(dateValue("20150702"), exDate.dates[1])
    }

    @Test
    fun testAndroidStringToRecurrenceSets_Exclude() {
        val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(
            "${tzToronto.id};20150103T113030",
            tzRegistry,
            allDay = false,
            exclude = dateTimeValue("20150103T113030", tzToronto),
            generator = exDateGenerator,
        )

        //FIXME: We probably don't want to emit empty ExDate instances. Don't invoke generator.
        assertEquals(0, exDate.dates.size)
    }


    // recurrenceSetsToOpenTasksString

    /*
    @Test
    fun testRecurrenceSetsToOpenTasksString_UtcTimes() {
        val list = ArrayList<DateListProperty<Temporal>>(1)
        list.add(RDate(DateList(
            ZonedDateTime.of(2015, 1, 1, 6, 0, 0, 0, ZoneOffset.UTC),
            ZonedDateTime.of(2015, 7, 2, 6, 0, 0, 0, ZoneOffset.UTC)
        )))
        assertEquals("20150101T060000Z,20150702T060000Z", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, tzBerlin))
    }

    @Test
    fun testRecurrenceSetsToOpenTasksString_ZonedTimes() {
        val list = ArrayList<DateListProperty<Temporal>>(1)
        list.add(RDate(DateList(
            ZonedDateTime.of(2015, 1, 1, 6, 0, 0, 0, tzToronto.toZoneId()),
            ZonedDateTime.of(2015, 7, 2, 6, 0, 0, 0, tzToronto.toZoneId())
        )))
        assertEquals("20150101T120000,20150702T120000", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, tzBerlin))
    }

    @Test
    fun testRecurrenceSetsToOpenTasksString_MixedTimes() {
        val list = ArrayList<DateListProperty<Temporal>>(1)
        list.add(RDate(DateList(
            ZonedDateTime.of(2015, 1, 1, 1, 0, 0, 0, tzToronto.toZoneId()),
            ZonedDateTime.of(2015, 7, 2, 6, 0, 0, 0, tzToronto.toZoneId())
        )))
        assertEquals("20150101T070000,20150702T120000", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, tzBerlin))
    }

    @Test
    fun testRecurrenceSetsToOpenTasksString_TimesAlthougAllDay() {
        val list = ArrayList<DateListProperty<Temporal>>(1)
        list.add(RDate(DateList(
            ZonedDateTime.of(2015, 1, 1, 6, 0, 0, 0, tzToronto.toZoneId()),
            ZonedDateTime.of(2015, 7, 2, 6, 0, 0, 0, tzToronto.toZoneId())
        )))
        assertEquals("20150101,20150702", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, null))
    }

    @Test
    fun testRecurrenceSetsToOpenTasksString_Dates() {
        val list = ArrayList<DateListProperty<Temporal>>(1)
        list.add(RDate(DateList(LocalDate.of(2015, 1, 1), LocalDate.of(2015, 7, 2))))
        assertEquals("20150101,20150702", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, null))
    }

    @Test
    fun testRecurrenceSetsToOpenTasksString_DatesAlthoughTimeZone() {
        val list = ArrayList<DateListProperty<Temporal>>(1)
        list.add(RDate(DateList(LocalDate.of(2015, 1, 1), LocalDate.of(2015, 7, 2))))
        assertEquals("20150101T000000,20150702T000000", AndroidTimeUtils.recurrenceSetsToOpenTasksString(list, tzBerlin))
    }


    @Test
    fun testParseDuration() {
        assertEquals(Duration.parse("PT3600S"), AndroidTimeUtils.parseDuration("3600S"))
        assertEquals(Duration.parse("PT3600S"), AndroidTimeUtils.parseDuration("P3600S"))
        assertEquals(Duration.parse("+PT3600S"), AndroidTimeUtils.parseDuration("+P3600S"))
        assertEquals(Duration.parse("PT3600S"), AndroidTimeUtils.parseDuration("PT3600S"))
        assertEquals(Duration.parse("+PT3600S"), AndroidTimeUtils.parseDuration("+PT3600S"))
        assertEquals(java.time.Period.parse("P10D"), AndroidTimeUtils.parseDuration("P1W3D"))
        assertEquals(java.time.Period.parse("P1D"), AndroidTimeUtils.parseDuration("1DT"))
        assertEquals(Duration.parse("P14DT3600S"), AndroidTimeUtils.parseDuration("P2W3600S"))
        assertEquals(Duration.parse("-P3DT4H5M6S"), AndroidTimeUtils.parseDuration("-P3D4H5M6S"))
        assertEquals(Duration.parse("PT3H2M1S"), AndroidTimeUtils.parseDuration("P1S2M3H"))
        assertEquals(Duration.parse("P4DT3H2M1S"), AndroidTimeUtils.parseDuration("P1S2M3H4D"))
        assertEquals(Duration.parse("P11DT3H2M1S"), AndroidTimeUtils.parseDuration("P1S2M3H4D1W"))
        assertEquals(Duration.parse("PT1H0M10S"), AndroidTimeUtils.parseDuration("1H10S"))
    }
*/

}