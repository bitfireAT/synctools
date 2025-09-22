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
import at.bitfire.ical4android.Event
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.property.DtStart
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AllDayBuilderTest {

    private val builder = AllDayBuilder()

    @Test
    fun `No DTSTART`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(),
            main = Event(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ALL_DAY to 0
        ), result.entityValues)
    }

    @Test
    fun `DTSTART is DATE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(dtStart = DtStart(Date())),
            main = Event(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ALL_DAY to 1
        ), result.entityValues)
    }

    @Test
    fun `DTSTART is DATE-TIME`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(dtStart = DtStart(DateTime())),
            main = Event(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ALL_DAY to 0
        ), result.entityValues)
    }

}