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
import net.fortuna.ical4j.model.property.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusBuilderTest {

    private val builder = StatusBuilder()

    @Test
    fun `No STATUS`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(),
            main = Event(),
            to = result
        )
        assertTrue(result.entityValues.containsKey(Events.STATUS))
        assertNull(result.entityValues.get(Events.STATUS))
    }

    @Test
    fun `STATUS is CONFIRMED`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(status = Status.VEVENT_CONFIRMED),
            main = Event(),
            to = result
        )
        assertEquals(Events.STATUS_CONFIRMED, result.entityValues.getAsInteger(Events.STATUS))
    }

    @Test
    fun `STATUS is CANCELLED`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(status = Status.VEVENT_CANCELLED),
            main = Event(),
            to = result
        )
        assertEquals(Events.STATUS_CANCELED, result.entityValues.getAsInteger(Events.STATUS))
    }

    @Test
    fun `STATUS is TENTATIVE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(status = Status.VEVENT_TENTATIVE),
            main = Event(),
            to = result
        )
        assertEquals(Events.STATUS_TENTATIVE, result.entityValues.getAsInteger(Events.STATUS))
    }

    @Test
    fun `STATUS is invalid (for VEVENT)`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(status = Status.VTODO_IN_PROCESS),
            main = Event(),
            to = result
        )
        assertEquals(Events.STATUS_TENTATIVE, result.entityValues.getAsInteger(Events.STATUS))
    }

}