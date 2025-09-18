/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TitleBuilderTest {

    private val builder = TitleBuilder()

    @Test
    fun `No SUMMARY`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(),
            main = Event(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Events.TITLE))
        assertNull(result.entityValues.get(Events.TITLE))
    }

    @Test
    fun `SUMMARY is blank`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(summary = ""),
            main = Event(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Events.TITLE))
        assertNull(result.entityValues.get(Events.TITLE))
    }

    @Test
    fun `SUMMARY is text`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(summary = "Event Summary"),
            main = Event(),
            to = result
        )
        assertEquals("Event Summary", result.entityValues.getAsString(Events.TITLE))
    }

}