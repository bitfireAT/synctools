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
class DescriptionBuilderTest {

    private val builder = DescriptionBuilder()

    @Test
    fun `No DESCRIPTION`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(),
            main = Event(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Events.DESCRIPTION))
        assertNull(result.entityValues.get(Events.DESCRIPTION))
    }

    @Test
    fun `DESCRIPTION is blank`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(description = ""),
            main = Event(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Events.DESCRIPTION))
        assertNull(result.entityValues.get(Events.DESCRIPTION))
    }

    @Test
    fun `DESCRIPTION is text`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(description = "Event Details"),
            main = Event(),
            to = result
        )
        assertEquals("Event Details", result.entityValues.getAsString(Events.DESCRIPTION))
    }

}