/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.property.Organizer
import org.junit.Assert
import org.junit.Test

class EventTest {

    @Test
    fun testToString() {
        val e = Event()
        e.uid = "SAMPLEUID"
        val s = e.toString()
        Assert.assertTrue(s.contains(Event::class.java.simpleName))
        Assert.assertTrue(s.contains("uid=SAMPLEUID"))
    }

    @Test
    fun testOrganizerEmail_None() {
        Assert.assertNull(Event().organizerEmail)
    }

    @Test
    fun testOrganizerEmail_EmailParameter() {
        Assert.assertEquals("organizer@example.com", Event().apply {
            organizer = Organizer("SomeFancyOrganizer").apply {
                parameters.add(Email("organizer@example.com"))
            }
        }.organizerEmail)
    }

    @Test
    fun testOrganizerEmail_MailtoValue() {
        Assert.assertEquals("organizer@example.com", Event().apply {
            organizer = Organizer("mailto:organizer@example.com")
        }.organizerEmail)
    }

}