/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.synctools.icalendar.Css3Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ColorProcessorTest {

    private val processor = ColorProcessor()

    @Test
    fun `No color`() {
        val result = Event()
        val entity = Entity(ContentValues())
        processor.process(entity, entity, result)
        assertNull(result.color)
    }

    @Test
    fun `Color from index`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.EVENT_COLOR_KEY to Css3Color.silver.name
        ))
        processor.process(entity, entity, result)
        assertEquals(Css3Color.silver, result.color)
    }

    @Test
    fun `Color from value`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.EVENT_COLOR to Css3Color.silver.argb
        ))
        processor.process(entity, entity, result)
        assertEquals(Css3Color.silver, result.color)
    }

}