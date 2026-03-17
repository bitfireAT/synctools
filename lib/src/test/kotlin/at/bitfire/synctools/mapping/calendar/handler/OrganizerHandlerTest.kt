/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Organizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrganizerHandlerTest {

    private val handler = OrganizerHandler()

    @Test
    fun `No ORGANIZER for non-group-scheduled event`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ORGANIZER to "organizer@example.com"
        ))
        handler.process(entity, entity, result)
        assertNull(result.organizer)
    }

    @Test
    fun `ORGANIZER for group-scheduled event`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ORGANIZER to "organizer@example.com"
        ))
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "organizer@example.com",
            Attendees.ATTENDEE_TYPE to Attendees.RELATIONSHIP_ORGANIZER
        ))
        handler.process(entity, entity, result)
        assertEquals(Organizer("mailto:organizer@example.com"), result.organizer)
    }

}