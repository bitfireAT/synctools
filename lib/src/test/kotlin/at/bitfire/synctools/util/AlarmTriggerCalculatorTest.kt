/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.util

import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.bitfire.synctools.util.AlarmTriggerCalculator.alarmTriggerToMinutes
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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import java.time.Duration as JavaDuration

class AlarmTriggerCalculatorTest {

    // current time stamp
    private val currentTime = ZonedDateTime.now()

    @Test
    fun `negative trigger duration`() {
        // TRIGGER;REL=START:-P1DT1H1M29S
        val alarm = VAlarm(JavaDuration.parse("-P1DT1H1M29S"))
        val refStart = DtStart<Temporal>()

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            refDuration = null,
            allowRelEnd = false
        )!!

        assertEquals(Related.START, ref)
        assertEquals((1.days + 1.hours + 1.minutes).toMinutes(), min)
    }

    @Test
    fun `trigger duration in seconds`() {
        // TRIGGER;REL=START:-PT3600S
        val alarm = VAlarm(JavaDuration.parse("-PT3600S"))
        val refStart = DtStart<Temporal>()

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            refDuration = null,
            allowRelEnd = false
        )!!

        assertEquals(Related.START, ref)
        assertEquals(3600.seconds.toMinutes(), min)
    }

    @Test
    fun `positive trigger duration`() {
        // TRIGGER;REL=START:P1DT1H1M30S (alarm *after* start)
        val alarm = VAlarm(JavaDuration.parse("P1DT1H1M30S"))
        val refStart = DtStart<Temporal>()

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            refDuration = null,
            allowRelEnd = false
        )!!

        assertEquals(Related.START, ref)
        assertEquals(-(1.days + 1.hours + 1.minutes).toMinutes(), min)
    }

    @Test
    fun `trigger relative to end with allowRelEnd=true`() {
        // TRIGGER;REL=END:-P1DT1H1M30S
        val alarm = VAlarm(JavaDuration.parse("-P1DT1H1M30S")).apply {
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }
        val refStart = DtStart<Temporal>()
        val allowRelEnd = true

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            refDuration = null,
            allowRelEnd = allowRelEnd
        )!!

        assertEquals(Related.END, ref)
        assertEquals(60 * 24 + 60 + 1, min)
    }

    @Test
    fun `trigger relative to end with allowRelEnd=false`() {
        // TRIGGER;REL=END:-PT30S
        val alarm = VAlarm(JavaDuration.parse("-PT65S")).apply {
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }
        val refStart = DtStart(currentTime)
        val refEnd = DtEnd(currentTime.plusSeconds(180))
        val allowRelEnd = false

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = refEnd,
            refDuration = null,
            allowRelEnd = allowRelEnd
        )!!

        assertEquals(Related.START, ref)
        // duration of event: 180 s (3 min), 65 s before that -> alarm 1:55 min before start
        assertEquals(-1, min)
    }

    @Test
    fun `trigger relative to end without start time and with allowRelEnd=false`() {
        // TRIGGER;REL=END:-PT30S
        val alarm = VAlarm(JavaDuration.parse("-PT65S")).apply {
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }
        val refStart = DtStart<Temporal>()
        val refEnd = DtEnd(currentTime)
        val allowRelEnd = false

        val result = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = refEnd,
            refDuration = null,
            allowRelEnd = allowRelEnd
        )

        assertNull(result)
    }

    @Test
    fun `trigger relative to end without end time or duration and with allowRelEnd=false`() {
        // TRIGGER;REL=END:-PT30S
        val alarm = VAlarm(JavaDuration.parse("-PT65S")).apply {
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }
        val refStart = DtStart(currentTime)
        val allowRelEnd = false

        val result = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            refDuration = null,
            allowRelEnd = allowRelEnd
        )

        assertNull(result)
    }

    @Test
    fun `trigger relative to end and after end date with allowRelEnd=false`() {
        // TRIGGER;REL=END:-P1DT1H1M30S
        val alarm = VAlarm(JavaDuration.parse("P1DT1H1M30S")).apply {
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }
        val refStart = DtStart(currentTime)
        // 90 sec (should be rounded down to 1 min) later
        val refEnd = Due(currentTime.plusSeconds(90))
        val allowRelEnd = false

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = refEnd,
            refDuration = null,
            allowRelEnd = allowRelEnd
        )!!

        assertEquals(Related.START, ref)
        assertEquals(-(1.days.toMinutes() + 1.hours.toMinutes() + 1 + 1) /* duration of event: */ - 1, min)
    }

    @Test
    fun `trigger with Period instance`() {
        val alarm = VAlarm(Period.parse("-P1W1D"))
        //FIXME: Use fixed date, otherwise test might fail close to DST changes
        val refStart = DtStart(currentTime)

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            refDuration = null,
            allowRelEnd = false
        )!!

        assertEquals(Related.START, ref)
        assertEquals(8.days.toMinutes(), min)
    }

    @Test
    fun `trigger with DATE-TIME value`() {
        // TRIGGER;VALUE=DATE-TIME:<xxxx>
        // 89 sec (should be cut off to 1 min) before event
        val alarm = VAlarm(currentTime.minusSeconds(89).toInstant()).apply {
            // not useful for DATE-TIME values, should be ignored
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = DtStart(currentTime),
            refEnd = null,
            refDuration = null,
            allowRelEnd = true
        )!!

        assertEquals(Related.START, ref)
        assertEquals(1, min)
    }

    @Test
    fun `refStart has DATE value and refDuration is Duration`() {
        val alarm = VAlarm(JavaDuration.parse("-PT5M"))
        val refStart = DtStart(dateValue("20260407"))
        val refDuration = Duration("PT1H")

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            refDuration = refDuration,
            allowRelEnd = true
        )!!

        assertEquals(Related.START, ref)
        assertEquals(5, min)
    }

    @Test
    fun `refStart has DATE value and refDuration is Period`() {
        val alarm = VAlarm(JavaDuration.parse("-PT5M"))
        val refStart = DtStart(dateValue("20260407"))
        val refDuration = Duration("P1D")

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            refDuration = refDuration,
            allowRelEnd = true
        )!!

        assertEquals(Related.START, ref)
        assertEquals(5, min)
    }

    @Test
    fun `trigger related to end with refStart and refDate having DATE values and allowRelEnd=false`() {
        val alarm = VAlarm(Duration("-PT5M").duration).apply {
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }
        val refStart = DtStart(dateValue("20260407"))
        val refEnd = DtStart(dateValue("20260408"))
        val allowRelEnd = false

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = refEnd,
            refDuration = null,
            allowRelEnd = allowRelEnd
        )!!

        assertEquals(Related.START, ref)
        assertEquals(-(1.days - 5.minutes).toMinutes(), min)
    }

    @Test
    fun `trigger with DATE-TIME vale and refStart with DATE value`() {
        val alarm = VAlarm(dateTimeValue("20260406T120000", ZoneOffset.UTC).toInstant())
        val refStart = DtStart(dateValue("20260407"))

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            refDuration = null,
            allowRelEnd = true
        )!!

        assertEquals(Related.START, ref)
        assertEquals(12.hours.toMinutes(), min)
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

private fun kotlin.time.Duration.toMinutes(): Int = inWholeMinutes.toInt()
