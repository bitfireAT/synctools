/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.provider.CalendarContract.Reminders
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.componentListOf
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.storage.emptyEntity
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Period

@RunWith(RobolectricTestRunner::class)
class RemindersBuilderTest {

    private val builder = RemindersBuilder()
    private val tzVienna = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone("Europe/Vienna")

    @Test
    fun `VALARM with absolute TRIGGER (DTSTART is DATE)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(Date("20250812"))
            ), componentListOf(
                VAlarm(DateTime("20250811T230000"))     // system default time zone
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(
            contentValuesOf(
                Reminders.MINUTES to 60
            ),
            result.subValues.first { it.uri == Reminders.CONTENT_URI }.values,
            onlyFieldsInExpected = true
        )
    }

    @Test
    fun `VALARM with absolute TRIGGER (DTSTART is DATE-TIME)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20250812T173000", tzVienna))
            ), componentListOf(
                VAlarm(DateTime("20250812T170000", tzVienna))
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(
            contentValuesOf(
                Reminders.MINUTES to 30
            ),
            result.subValues.first { it.uri == Reminders.CONTENT_URI }.values,
            onlyFieldsInExpected = true
        )
    }

    @Test
    fun `VALARM with relative TRIGGER related to DTSTART (exact number of seconds)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(), componentListOf(
                VAlarm(Duration.ofHours(-1))
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(
            contentValuesOf(
                Reminders.MINUTES to 60
            ),
            result.subValues.first { it.uri == Reminders.CONTENT_URI }.values,
            onlyFieldsInExpected = true
        )
    }

    @Test
    fun `VALARM with relative TRIGGER related to DTSTART (number of weeks)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20250812T173000", tzVienna))
            ), componentListOf(
                VAlarm(Period.ofWeeks(-1))
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(
            contentValuesOf(
                Reminders.MINUTES to 7*24*60
            ),
            result.subValues.first { it.uri == Reminders.CONTENT_URI }.values,
            onlyFieldsInExpected = true
        )
    }

    @Test
    fun `VALARM with relative TRIGGER related to DTEND (exact number of seconds)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20250812T173000", tzVienna)),
                DtEnd(DateTime("20250812T180000", tzVienna))
            ), componentListOf(
                VAlarm().apply {
                    properties += Trigger(
                        ParameterList().apply {
                            add(Related.END)
                        },
                        Duration.ofHours(-1)
                    )
                }
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(
            contentValuesOf(
                Reminders.MINUTES to 30
            ),
            result.subValues.first { it.uri == Reminders.CONTENT_URI }.values,
            onlyFieldsInExpected = true
        )
    }

    @Test
    fun `VALARM with relative TRIGGER related to DTEND (number of days)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                DtStart(DateTime("20250812T173000Z")),
                DtEnd(DateTime("20250812T180000Z"))
            ), componentListOf(
                VAlarm().apply {
                    properties += Trigger(
                        ParameterList().apply {
                            add(Related.END)
                        },
                        Period.ofDays(-1)
                    )
                }
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(
            contentValuesOf(
                Reminders.MINUTES to 24*60 - 30
            ),
            result.subValues.first { it.uri == Reminders.CONTENT_URI }.values,
            onlyFieldsInExpected = true
        )
    }


    @Test
    fun `No VALARM`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        ))
        assertTrue(result.subValues.isEmpty())
    }

}