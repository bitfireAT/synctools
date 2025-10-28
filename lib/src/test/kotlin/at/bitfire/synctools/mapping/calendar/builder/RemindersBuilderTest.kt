/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Reminders
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Period

@RunWith(RobolectricTestRunner::class)
class RemindersBuilderTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzShanghai = tzRegistry.getTimeZone("Asia/Shanghai")
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")

    private val builder = RemindersBuilder()

    @Test
    fun `No trigger`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent().apply {
                components += VAlarm()
            },
            main = VEvent(),
            to = result
        )
        assertReminder(result,
            Reminders.METHOD to Reminders.METHOD_DEFAULT,
            Reminders.MINUTES to Reminders.MINUTES_DEFAULT
        )
    }

    @Test
    fun `Trigger TYPE is AUDIO`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent().apply {
                components += VAlarm(java.time.Duration.ofMinutes(-10)).apply {
                    properties += Action.AUDIO
                }
            },
            main = VEvent(),
            to = result
        )
        assertReminder(result,
            Reminders.METHOD to Reminders.METHOD_ALERT,
            Reminders.MINUTES to 10
        )
    }

    @Test
    fun `Trigger TYPE is DISPLAY`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent().apply {
                components += VAlarm(java.time.Duration.ofMinutes(-10)).apply {
                    properties += Action.DISPLAY
                }
            },
            main = VEvent(),
            to = result
        )
        assertReminder(result,
            Reminders.METHOD to Reminders.METHOD_ALERT,
            Reminders.MINUTES to 10
        )
    }

    @Test
    fun `Trigger TYPE is EMAIL`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent().apply {
                components += VAlarm(java.time.Duration.ofSeconds(-120)).apply {
                    properties += Action.EMAIL
                }
            },
            main = VEvent(),
            to = result
        )
        assertReminder(result,
            Reminders.METHOD to Reminders.METHOD_EMAIL,
            Reminders.MINUTES to 2
        )
    }

    @Test
    fun `Trigger TYPE is custom`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent().apply {
                components += VAlarm(java.time.Duration.ofSeconds(-120)).apply {
                    properties += Action("X-CUSTOM")
                }
            },
            main = VEvent(),
            to = result
        )
        assertReminder(result,
            Reminders.METHOD to Reminders.METHOD_DEFAULT,
            Reminders.MINUTES to 2
        )
    }

    @Test
    fun `Trigger is relative to start and a DURATION`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent().apply {
                components += VAlarm(Period.ofDays(-1))
            },
            main = VEvent(),
            to = result
        )
        assertReminder(result, Reminders.MINUTES to 1440)
    }

    @Test
    fun `Trigger is relative to start and a DURATION less than one minute`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent().apply {
                components += VAlarm(java.time.Duration.ofSeconds(-10))
            },
            main = VEvent(),
            to = result
        )
        assertReminder(result, Reminders.MINUTES to 0)
    }

    @Test
    fun `Trigger is relative to start and a positive DURATION`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent().apply {
                components += VAlarm(java.time.Duration.ofMinutes(10))
            },
            main = VEvent(),
            to = result
        )
        // positive duration -> reminder is AFTER reference time -> negative minutes field
        assertReminder(result, Reminders.MINUTES to -10)
    }

    @Test
    fun `Trigger is relative to end and a DURATION`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20200621T120000", tzVienna)),
                DtEnd(DateTime("20200621T140000", tzVienna))
            ), ComponentList(listOf(
                VAlarm(Period.ofDays(-1)).apply {
                    trigger.parameters.add(Related.END)
                }
            ))),
            main = VEvent(),
            to = result
        )
        assertReminder(result, Reminders.MINUTES to 1320)
    }

    @Test
    fun `Trigger is relative to end and a DURATION less than one minute`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20200621T120000", tzVienna)),
                DtEnd(DateTime("20200621T140000", tzVienna))
            ), ComponentList(listOf(
                VAlarm(java.time.Duration.ofSeconds(-7240)).apply {
                    trigger.parameters.add(Related.END)
                }
            ))),
            main = VEvent(),
            to = result
        )
        assertReminder(result, Reminders.MINUTES to 0)
    }

    @Test
    fun `Trigger is relative to end and a positive DURATION`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20200621T120000", tzVienna)),
                DtEnd(DateTime("20200621T140000", tzVienna))
            ), ComponentList(listOf(
                VAlarm(java.time.Duration.ofMinutes(10)).apply {
                    trigger.parameters.add(Related.END)
                }
            ))),
            main = VEvent(),
            to = result
        )
        // positive duration -> reminder is AFTER reference time -> negative minutes field
        assertReminder(result, Reminders.MINUTES to -130)
    }

    @Test
    fun `Trigger is absolute`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20200621T120000", tzVienna))
            ), ComponentList(listOf(
                VAlarm(DateTime("20200621T110000", tzVienna))
            ))),
            main = VEvent(),
            to = result
        )
        // positive duration -> reminder is AFTER reference time -> negative minutes field
        assertReminder(result, Reminders.MINUTES to 60)
    }

    @Test
    fun `Trigger is absolute and in other time zone`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20200621T120000", tzVienna))
            ), ComponentList(listOf(
                VAlarm(DateTime("20200621T110000", tzShanghai))
            ))),
            main = VEvent(),
            to = result
        )
        assertReminder(result, Reminders.MINUTES to 420)
    }


    // helpers

    private fun assertReminder(result: Entity, vararg values: Pair<String, Any?>) {
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(
            contentValuesOf(*values),
            result.subValues.first { it.uri == Reminders.CONTENT_URI }.values,
            onlyFieldsInExpected = true
        )
    }

}