/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SequenceProcessorTest {

    private val processor = SequenceProcessor()

    @Test
    fun `No sequence`() {
        val result = Event()
        val entity = Entity(ContentValues())
        processor.process(entity, entity, result)
        assertNull(result.sequence)
    }

    @Test
    fun `Sequence is 0`() {
        val entity = Entity(contentValuesOf(
            AndroidEvent2.COLUMN_SEQUENCE to 0
        ))
        val result = Event()
        processor.process(entity, entity, result)
        assertEquals(0, result.sequence)
    }

    @Test
    fun `Sequence is 1`() {
        val entity = Entity(contentValuesOf(
            AndroidEvent2.COLUMN_SEQUENCE to 1
        ))
        val result = Event()
        processor.process(entity, entity, result)
        assertEquals(1, result.sequence)
    }

}