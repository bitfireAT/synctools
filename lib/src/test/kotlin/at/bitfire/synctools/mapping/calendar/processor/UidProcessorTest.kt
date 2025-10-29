/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UidProcessorTest {

    private val processor = UidProcessor()

    @Test
    fun `No UID`() {
        val result = VEvent()
        val entity = Entity(ContentValues())
        processor.process(entity, entity, result)
        assertNull(result.uid)
    }

    @Test
    fun `UID from event row`() {
        val entity = Entity(contentValuesOf(
            Events.UID_2445 to "from-event"
        ))
        val result = VEvent()
        processor.process(entity, entity, result)
        assertEquals("from-event", result.uid.value)
    }

    @Test
    fun `UID from extended row`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
            ExtendedProperties.NAME to EventsContract.EXTNAME_GOOGLE_CALENDAR_UID,
            ExtendedProperties.VALUE to "from-extended"
        ))
        val result = VEvent()
        processor.process(entity, entity, result)
        assertEquals("from-extended", result.uid.value)
    }

    @Test
    fun `UID from event and extended row`() {
        val entity = Entity(contentValuesOf(
            Events.UID_2445 to "from-event"
        ))
        entity.addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
            ExtendedProperties.NAME to EventsContract.EXTNAME_GOOGLE_CALENDAR_UID,
            ExtendedProperties.VALUE to "from-extended"
        ))
        val result = VEvent()
        processor.process(entity, entity, result)
        assertEquals("from-event", result.uid.value)
    }

}