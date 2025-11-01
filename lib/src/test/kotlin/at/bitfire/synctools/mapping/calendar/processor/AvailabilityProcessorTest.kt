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
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Transp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AvailabilityProcessorTest {

    private val processor = AvailabilityProcessor()

    @Test
    fun `No availability`() {
        val result = VEvent()
        val entity = Entity(ContentValues())
        processor.process(entity, entity, result)
        // OPAQUE is default value
        assertNull(result.getProperty<Transp>(Property.TRANSP))
    }

    @Test
    fun `Availability BUSY`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.AVAILABILITY to Events.AVAILABILITY_BUSY
        ))
        processor.process(entity, entity, result)
        // OPAQUE is default value
        assertNull(result.getProperty<Transp>(Property.TRANSP))
    }

    @Test
    fun `Availability FREE`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.AVAILABILITY to Events.AVAILABILITY_FREE
        ))
        processor.process(entity, entity, result)
        assertEquals(Transp.TRANSPARENT, result.getProperty<Transp>(Property.TRANSP))
    }

    @Test
    fun `Availability TENTATIVE`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.AVAILABILITY to Events.AVAILABILITY_TENTATIVE
        ))
        processor.process(entity, entity, result)
        // OPAQUE is default value
        assertNull(result.getProperty<Transp>(Property.TRANSP))
    }

}