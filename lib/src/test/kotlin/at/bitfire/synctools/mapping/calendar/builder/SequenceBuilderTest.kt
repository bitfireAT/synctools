/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import at.bitfire.synctools.test.assertContentValuesEqual
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SequenceBuilderTest {

    private val builder = SequenceBuilder()

    @Test
    fun `No SEQUENCE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(),
            main = Event(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            AndroidEvent2.COLUMN_SEQUENCE to null
        ), result.entityValues)
    }

    @Test
    fun `SEQUENCE set`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(sequence = 0),
            main = Event(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            AndroidEvent2.COLUMN_SEQUENCE to 0
        ), result.entityValues)
    }

}