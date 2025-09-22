/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import net.fortuna.ical4j.model.property.Organizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrganizerProcessorTest {

    private val processor = OrganizerProcessor()

    @Test
    fun `isOrganizer not set`() {
        val result = Event()
        val entity = Entity(ContentValues())
        processor.process(entity, entity, result)
        assertNull(result.isOrganizer)
    }

    @Test
    fun `isOrganizer is 0`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.IS_ORGANIZER to 0,
        ))
        processor.process(entity, entity, result)
        assertFalse(result.isOrganizer!!)
    }

    @Test
    fun `isOrganizer is 1`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.IS_ORGANIZER to 1,
        ))
        processor.process(entity, entity, result)
        assertTrue(result.isOrganizer!!)
    }


    @Test
    fun `No ORGANIZER for non-group-scheduled event`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.ORGANIZER to "organizer@example.com"
        ))
        processor.process(entity, entity, result)
        assertNull(result.organizer)
    }

    @Test
    fun `ORGANIZER for group-scheduled event`() {
        val result = Event()
        val entity = Entity(contentValuesOf(
            Events.ORGANIZER to "organizer@example.com"
        ))
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "organizer@example.com",
            Attendees.ATTENDEE_TYPE to Attendees.RELATIONSHIP_ORGANIZER
        ))
        processor.process(entity, entity, result)
        assertEquals(Organizer("mailto:organizer@example.com"), result.organizer)
    }

}