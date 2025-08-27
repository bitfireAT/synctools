/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.storage.emptyEntity
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Attendee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HasAttendeeDataBuilderTest {

    private val builder = HasAttendeeDataBuilder()

    @Test
    fun `Group-scheduled`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                Attendee("attendee1@example.com")
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(1, result.entityValues.getAsInteger(Events.HAS_ATTENDEE_DATA))
    }

    @Test
    fun `Not group-scheduled`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        ))
        assertEquals(0, result.entityValues.getAsInteger(Events.HAS_ATTENDEE_DATA))
    }

}