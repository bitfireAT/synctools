/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Attendees
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.CuType
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.Role
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.property.Attendee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI

@RunWith(RobolectricTestRunner::class)
class AttendeesProcessorTest {

    private val processor = AttendeesProcessor()

    @Test
    fun `Attendee is email address`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com"
        ))
        val result = VEvent()
        processor.process(entity, entity, result)
        assertEquals(
            URI("mailto:attendee@example.com"),
            result.getProperty<Attendee>(Property.ATTENDEE).calAddress
        )
    }

    @Test
    fun `Attendee is other URI`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_ID_NAMESPACE to "https",
            Attendees.ATTENDEE_IDENTITY to "//example.com/principals/attendee"
        ))
        val result = VEvent()
        processor.process(entity, entity, result)
        assertEquals(
            URI("https://example.com/principals/attendee"),
            result.getProperty<Attendee>(Property.ATTENDEE).calAddress
        )
    }

    @Test
    fun `Attendee is email address with other URI`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_ID_NAMESPACE to "https",
            Attendees.ATTENDEE_IDENTITY to "//example.com/principals/attendee"
        ))
        val result = VEvent()
        processor.process(entity, entity, result)
        val attendees = result.getProperties<Attendee>(Property.ATTENDEE)
        assertEquals(1, attendees.size)
        val attendee = attendees.first()
        assertEquals(URI("https://example.com/principals/attendee"), attendee.calAddress)
        assertEquals("attendee@example.com", attendee.getParameter<Email>(Parameter.EMAIL).value)
    }


    @Test
    fun `Attendee with relationship ATTENDEE or ORGANIZER generates empty user-type`() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_ATTENDEE, Attendees.RELATIONSHIP_ORGANIZER))
            for (type in arrayOf(Attendees.TYPE_REQUIRED, Attendees.TYPE_OPTIONAL, Attendees.TYPE_NONE, null)) {
                val entity = Entity(ContentValues())
                entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                    Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                    Attendees.ATTENDEE_RELATIONSHIP to relationship,
                    Attendees.ATTENDEE_TYPE to type
                ))
                val result = VEvent()
                processor.process(entity, entity, result)
                val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
                assertNull(attendee.getParameter(Parameter.CUTYPE))
            }
    }

    @Test
    fun `Attendee with relationship PERFORMER generates user-type GROUP`() {
        for (type in arrayOf(Attendees.TYPE_REQUIRED, Attendees.TYPE_OPTIONAL, Attendees.TYPE_NONE, null)) {
            val entity = Entity(ContentValues())
            entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_PERFORMER,
                Attendees.ATTENDEE_TYPE to type
            ))
            val result = VEvent()
            processor.process(entity, entity, result)
            val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
            assertEquals(CuType.GROUP, attendee.getParameter<CuType>(Parameter.CUTYPE))
        }
    }

    @Test
    fun `Attendee with relationship SPEAKER generates chair role (user-type person)`() {
        for (type in arrayOf(Attendees.TYPE_REQUIRED, Attendees.TYPE_OPTIONAL, Attendees.TYPE_NONE, null)) {
            val entity = Entity(ContentValues())
            entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_SPEAKER,
                Attendees.ATTENDEE_TYPE to type
            ))
            val result = VEvent()
            processor.process(entity, entity, result)
            val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
            assertNull(attendee.getParameter(Parameter.CUTYPE))
            assertEquals(Role.CHAIR, attendee.getParameter<Role>(Parameter.ROLE))
        }
    }

    @Test
    fun `Attendee with relationship SPEAKER generates chair role (user-type RESOURCE)`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_SPEAKER,
            Attendees.ATTENDEE_TYPE to Attendees.TYPE_RESOURCE
        ))
        val result = VEvent()
        processor.process(entity, entity, result)
        val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
        assertEquals(CuType.RESOURCE, attendee.getParameter<CuType>(Parameter.CUTYPE))
        assertEquals(Role.CHAIR, attendee.getParameter<Role>(Parameter.ROLE))
    }

    @Test
    fun `Attendee with relationship NONE generates user-type UNKNOWN`() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_NONE, null))
            for (type in arrayOf(Attendees.TYPE_REQUIRED, Attendees.TYPE_OPTIONAL, Attendees.TYPE_NONE, null)) {
                val entity = Entity(ContentValues())
                entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                    Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                    Attendees.ATTENDEE_RELATIONSHIP to relationship,
                    Attendees.ATTENDEE_TYPE to type
                ))
                val result = VEvent()
                processor.process(entity, entity, result)
                val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
                assertEquals(CuType.UNKNOWN, attendee.getParameter<CuType>(Parameter.CUTYPE))
            }
    }


    @Test
    fun `Attendee with type NONE doesn't generate ROLE`() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_ATTENDEE, Attendees.RELATIONSHIP_ORGANIZER, Attendees.RELATIONSHIP_PERFORMER, Attendees.RELATIONSHIP_NONE, null)) {
            val entity = Entity(ContentValues())
            entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                Attendees.ATTENDEE_RELATIONSHIP to relationship,
                Attendees.ATTENDEE_TYPE to Attendees.TYPE_NONE
            ))
            val result = VEvent()
            processor.process(entity, entity, result)
            val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
            assertNull(attendee.getParameter<Role>(Parameter.ROLE))
        }
    }

    @Test
    fun `Attendee with type REQUIRED doesn't generate ROLE`() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_ATTENDEE, Attendees.RELATIONSHIP_ORGANIZER, Attendees.RELATIONSHIP_PERFORMER, Attendees.RELATIONSHIP_NONE, null)) {
            val entity = Entity(ContentValues())
            entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                Attendees.ATTENDEE_RELATIONSHIP to relationship,
                Attendees.ATTENDEE_TYPE to Attendees.TYPE_REQUIRED
            ))
            val result = VEvent()
            processor.process(entity, entity, result)
            val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
            assertNull(attendee.getParameter<Role>(Parameter.ROLE))
        }
    }

    @Test
    fun `Attendee with type OPTIONAL generates OPTIONAL role`() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_ATTENDEE, Attendees.RELATIONSHIP_ORGANIZER, Attendees.RELATIONSHIP_PERFORMER, Attendees.RELATIONSHIP_NONE, null)) {
            val entity = Entity(ContentValues())
            entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                Attendees.ATTENDEE_RELATIONSHIP to relationship,
                Attendees.ATTENDEE_TYPE to Attendees.TYPE_OPTIONAL
            ))
            val result = VEvent()
            processor.process(entity, entity, result)
            val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
            assertEquals(Role.OPT_PARTICIPANT, attendee.getParameter<Role>(Parameter.ROLE))
        }
    }

    @Test
    fun `Attendee with type RESOURCE generates user-type RESOURCE`() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_ATTENDEE, Attendees.RELATIONSHIP_ORGANIZER, Attendees.RELATIONSHIP_NONE, null)) {
            val entity = Entity(ContentValues())
            entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
                Attendees.ATTENDEE_EMAIL to "attendee@example.com",
                Attendees.ATTENDEE_RELATIONSHIP to relationship,
                Attendees.ATTENDEE_TYPE to Attendees.TYPE_RESOURCE
            ))
            val result = VEvent()
            processor.process(entity, entity, result)
            val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
            assertEquals(CuType.RESOURCE, attendee.getParameter<CuType>(Parameter.CUTYPE))
        }
    }

    @Test
    fun `Attendee with type RESOURCE (relationship PERFORMER) generates user-type ROOM`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_PERFORMER,
            Attendees.ATTENDEE_TYPE to Attendees.TYPE_RESOURCE
        ))
        val result = VEvent()
        processor.process(entity, entity, result)
        val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
        assertEquals(CuType.ROOM, attendee.getParameter<CuType>(Parameter.CUTYPE))
    }


    @Test
    fun `Attendee without participation status`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com"
        ))
        val result = VEvent()
        processor.process(entity, entity, result)
        val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
        assertNull(attendee.getParameter(Parameter.PARTSTAT))
    }

    @Test
    fun `Attendee with participation status INVITED`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_INVITED
        ))
        val result = VEvent()
        processor.process(entity, entity, result)
        val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
        assertEquals(PartStat.NEEDS_ACTION, attendee.getParameter(Parameter.PARTSTAT))
    }

    @Test
    fun `Attendee with participation status ACCEPTED`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_ACCEPTED
        ))
        val result = VEvent()
        processor.process(entity, entity, result)
        val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
        assertEquals(PartStat.ACCEPTED, attendee.getParameter(Parameter.PARTSTAT))
    }

    @Test
    fun `Attendee with participation status DECLINED`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_DECLINED
        ))
        val result = VEvent()
        processor.process(entity, entity, result)
        val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
        assertEquals(PartStat.DECLINED, attendee.getParameter(Parameter.PARTSTAT))
    }

    @Test
    fun `Attendee with participation status TENTATIVE`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_TENTATIVE
        ))
        val result = VEvent()
        processor.process(entity, entity, result)
        val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
        assertEquals(PartStat.TENTATIVE, attendee.getParameter(Parameter.PARTSTAT))
    }

    @Test
    fun `Attendee with participation status NONE`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com",
            Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_NONE
        ))
        val result = VEvent()
        processor.process(entity, entity, result)
        val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
        assertNull(attendee.getParameter(Parameter.PARTSTAT))
    }


    @Test
    fun `Attendee RSVP`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Attendees.CONTENT_URI, contentValuesOf(
            Attendees.ATTENDEE_EMAIL to "attendee@example.com"
        ))
        val result = VEvent()
        processor.process(entity, entity, result)
        val attendee = result.getProperty<Attendee>(Property.ATTENDEE)
        assertTrue(attendee.getParameter<Rsvp>(Parameter.RSVP).rsvp)
    }

}