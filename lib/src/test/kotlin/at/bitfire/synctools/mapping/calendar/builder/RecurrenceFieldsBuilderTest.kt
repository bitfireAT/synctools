/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecurrenceFieldsBuilderTest {

    private val builder = RecurrenceFieldsBuilder()

    @Test
    fun `Exception event`() {
        // Exceptions (of recurring events) must never have recurrence properties themselves.
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(
                DtStart(),
                RRule("FREQ=DAILY;COUNT=1"),
                RDate(),
                ExDate()
            )),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to null,
            Events.RDATE to null,
            Events.EXRULE to null,
            Events.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `EXDATE for non-recurring event`() {
        val main = VEvent(propertyListOf(
            DtStart(),
            ExDate()
        ))
        val result = Entity(ContentValues())
        builder.build(
            from = main,
            main = main,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to null,
            Events.RDATE to null,
            Events.EXRULE to null,
            Events.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `Single RRULE`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(),
            RRule("FREQ=DAILY;COUNT=10")
        ))
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to "FREQ=DAILY;COUNT=10",
            Events.RDATE to null,
            Events.EXRULE to null,
            Events.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `Multiple RRULEs`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(),
            RRule("FREQ=YEARLY;BYMONTH=4;BYDAY=-1SU"),
            RRule("FREQ=YEARLY;BYMONTH=10;BYDAY=1SU")
        ))
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to "FREQ=YEARLY;BYMONTH=4;BYDAY=-1SU\nFREQ=YEARLY;BYMONTH=10;BYDAY=1SU",
            Events.RDATE to null,
            Events.EXRULE to null,
            Events.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `Single RDATE`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(Date("20250917")),
            RDate(DateList().apply {
                add(Date("20250918"))
            })
        ))
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to null,
            Events.RDATE to "20250917T000000Z,20250918T000000Z",
            Events.EXRULE to null,
            Events.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `RDATE with infinite RRULE present`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(Date("20250917")),
            RRule("FREQ=DAILY"),
            RDate(DateList().apply {
                add(Date("20250918"))
            })
        ))
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to "FREQ=DAILY",
            Events.RDATE to null,
            Events.EXRULE to null,
            Events.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `Single EXRULE`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(),
            RRule("FREQ=DAILY"),
            ExRule(ParameterList(), "FREQ=WEEKLY")
        ))
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to "FREQ=DAILY",
            Events.RDATE to null,
            Events.EXRULE to "FREQ=WEEKLY",
            Events.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `Single EXDATE`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(Date("20250918")),
            RRule("FREQ=DAILY"),
            ExDate(DateList("20250920", Value.DATE))
        ))
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to "FREQ=DAILY",
            Events.RDATE to null,
            Events.EXRULE to null,
            Events.EXDATE to "20250920T000000Z"
        ), result.entityValues)
    }

}