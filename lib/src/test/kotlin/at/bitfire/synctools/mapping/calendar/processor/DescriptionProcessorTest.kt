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
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DescriptionProcessorTest {

    private val processor = DescriptionProcessor()

    @Test
    fun `No description`() {
        val result = VEvent(/* initialise = */ false)
        val entity = Entity(ContentValues())
        processor.process(entity, entity, result)
        assertNull(result.description)
    }

    @Test
    fun `Blank description`() {
        val entity = Entity(contentValuesOf(
            Events.DESCRIPTION to "   "
        ))
        val result = VEvent(/* initialise = */ false)
        processor.process(entity, entity, result)
        assertNull(result.description)
    }

    @Test
    fun `Description with two words`() {
        val entity = Entity(contentValuesOf(
            Events.DESCRIPTION to "Two words "
        ))
        val result = VEvent(/* initialise = */ false)
        processor.process(entity, entity, result)
        assertEquals("Two words", result.description.value)
    }

}