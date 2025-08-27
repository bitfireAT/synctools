/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.storage.emptyEntity
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Organizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrganizerBuilderTest {

    private val builder = OrganizerBuilder("my@account.com")

    @Test
    fun `ORGANIZER is email address`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                Organizer("mailto:test@example.com"),
                Attendee("attendee1@example.com")
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals("test@example.com", result.entityValues.getAsString(Events.ORGANIZER))
    }

    @Test
    fun `ORGANIZER is text (EMAIL parameter set)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                Organizer(ParameterList().apply {
                    add(Email("test@example.com"))
                }, "SomeOrganizer"),
                Attendee("attendee1@example.com")
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals("test@example.com", result.entityValues.getAsString(Events.ORGANIZER))
    }

    @Test
    fun `ORGANIZER is text (EMAIL parameter not set)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                Organizer("SomeOrganizer"),
                Attendee("attendee1@example.com")
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals("my@account.com", result.entityValues.getAsString(Events.ORGANIZER))
    }

    @Test
    fun `No ORGANIZER`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                Attendee("attendee1@example.com")
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals("my@account.com", result.entityValues.getAsString(Events.ORGANIZER))
    }

    @Test
    fun `Not group-scheduled`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                Organizer("mailto:test@example.com")
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals("my@account.com", result.entityValues.getAsString(Events.ORGANIZER))
    }

}