/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.util

import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.bitfire.synctools.util.AlarmTriggerCalculator.vAlarmToMin
import net.fortuna.ical4j.model.Property.TRIGGER
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Period
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import kotlin.jvm.optionals.getOrNull

class AlarmTriggerCalculatorTest {

    // current time stamp
    private val currentTime = ZonedDateTime.now()

    @Test
    fun testVAlarmToMin_TriggerDuration_Negative() {
        // TRIGGER;REL=START:-P1DT1H1M29S
        val (ref, min) = vAlarmToMin(
            VAlarm(Duration("-P1DT1H1M29S").duration),
            DtStart<Temporal>(), null, null, false
        )!!
        assertEquals(Related.START, ref)
        assertEquals(60 * 24 + 60 + 1, min)
    }

    @Test
    fun testVAlarmToMin_TriggerDuration_OnlySeconds() {
        // TRIGGER;REL=START:-PT3600S
        val (ref, min) = vAlarmToMin(
            VAlarm(Duration("-PT3600S").duration),
            DtStart<Temporal>(), null, null, false
        )!!
        assertEquals(Related.START, ref)
        assertEquals(60, min)
    }

    @Test
    fun testVAlarmToMin_TriggerDuration_Positive() {
        // TRIGGER;REL=START:P1DT1H1M30S (alarm *after* start)
        val (ref, min) = vAlarmToMin(
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
        val (ref, min) = vAlarmToMin(alarm, DtStart<Temporal>(), null, null, true)!!
        assertEquals(Related.END, ref)
        assertEquals(60 * 24 + 60 + 1, min)
    }

    @Test
    fun testVAlarmToMin_TriggerDuration_RelEndNotAllowed() {
        // event with TRIGGER;REL=END:-PT30S (caller doesn't accept Related.END)
        val alarm = VAlarm(Duration("-PT65S").duration)
        alarm.getProperty<Trigger>(TRIGGER).getOrNull()?.add<Trigger>(Related.END)
        val (ref, min) = vAlarmToMin(
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
        assertNull(vAlarmToMin(alarm, DtStart<Temporal>(), DtEnd(currentTime), null, false))
    }

    @Test
    fun testVAlarmToMin_TriggerDuration_RelEndNotAllowed_NoDuration() {
        // event with TRIGGER;REL=END:-PT30S (caller doesn't accept Related.END)
        val alarm = VAlarm(Duration("-PT65S").duration)
        alarm.getProperty<Trigger>(TRIGGER).getOrNull()?.add<Trigger>(Related.END)
        assertNull(vAlarmToMin(alarm, DtStart(currentTime), null, null, false))
    }

    @Test
    fun testVAlarmToMin_TriggerDuration_RelEndNotAllowed_AfterEnd() {
        // task with TRIGGER;REL=END:-P1DT1H1M30S (caller doesn't accept Related.END; alarm *after* end)
        val alarm = VAlarm(Duration("P1DT1H1M30S").duration)
        alarm.getProperty<Trigger>(TRIGGER).getOrNull()?.add<Trigger>(Related.END)
        val (ref, min) = vAlarmToMin(
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
        val (ref, min) = vAlarmToMin(
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
        val (ref, min) = vAlarmToMin(alarm, DtStart(currentTime), null, null, false)!!
        assertEquals(Related.START, ref)
        assertEquals(1, min)
    }

    @Test
    fun `vAlarmToMin with trigger duration, DtStart is DATE, Duration is java_time_Duration`() {
        val alarm = VAlarm(Duration("-PT5M").duration)
        val dtStart = DtStart(dateValue("20260407"))
        val duration = Duration("PT1H")

        val (ref, min) = vAlarmToMin(
            alarm = alarm,
            refStart = dtStart,
            refEnd = null,
            refDuration = duration,
            allowRelEnd = true
        )!!

        assertEquals(Related.START, ref)
        assertEquals(5, min)
    }

    @Test
    fun `vAlarmToMin with trigger duration, DtStart is DATE, Duration is java_time_Period`() {
        val alarm = VAlarm(Duration("-PT5M").duration)
        val dtStart = DtStart(dateValue("20260407"))
        val duration = Duration("P1D")

        val (ref, min) = vAlarmToMin(
            alarm = alarm,
            refStart = dtStart,
            refEnd = null,
            refDuration = duration,
            allowRelEnd = true
        )!!

        assertEquals(Related.START, ref)
        assertEquals(5, min)
    }

    @Test
    fun `vAlarmToMin with trigger duration and Related=END, DtStart and DtEnd are DATE, allowRelEnd=false`() {
        val alarm = VAlarm(Duration("-PT5M").duration).apply {
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }
        val dtStart = DtStart(dateValue("20260407"))
        val dtEnd = DtStart(dateValue("20260408"))

        val (ref, min) = vAlarmToMin(
            alarm = alarm,
            refStart = dtStart,
            refEnd = dtEnd,
            refDuration = null,
            allowRelEnd = false
        )!!

        assertEquals(Related.START, ref)
        assertEquals(-(24 * 60 - 5), min)
    }

    @Test
    fun `vAlarmToMin with DATE-TIME trigger, DtStart is DATE`() {
        val alarm = VAlarm(dateTimeValue("20260406T120000", ZoneOffset.UTC).toInstant())
        val dtStart = DtStart(dateValue("20260407"))

        val (ref, min) = vAlarmToMin(
            alarm = alarm,
            refStart = dtStart,
            refEnd = null,
            refDuration = null,
            allowRelEnd = true
        )!!

        assertEquals(Related.START, ref)
        assertEquals(12 * 60, min)
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