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
import net.fortuna.ical4j.model.property.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusProcessorTest {

    private val processor = StatusProcessor()

    @Test
    fun `No status`() {
        val result = Event()
        val entity = Entity(ContentValues())
        processor.process(entity, entity, result)
        assertNull(result.status)
    }

    @Test
    fun `Status CONFIRMED`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.STATUS to Events.STATUS_CONFIRMED
        ))
        processor.process(entity, entity, result)
        assertEquals(Status.VEVENT_CONFIRMED, result.status)
    }

    @Test
    fun `Status TENTATIVE`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.STATUS to Events.STATUS_TENTATIVE
        ))
        processor.process(entity, entity, result)
        assertEquals(Status.VEVENT_TENTATIVE, result.status)
    }

    @Test
    fun `Status CANCELLED`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.STATUS to Events.STATUS_CANCELED
        ))
        processor.process(entity, entity, result)
        assertEquals(Status.VEVENT_CANCELLED, result.status)
    }

}