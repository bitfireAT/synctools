/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoriesProcessorTest {

    private val processor = CategoriesProcessor()

    @Test
    fun `No categories`() {
        val result = Event()
        val entity = Entity(ContentValues())
        processor.process(entity, entity, result)
        assertTrue(result.categories.isEmpty())
    }

    @Test
    fun `Multiple categories`() {
        val result = Event()
        val entity = Entity(ContentValues())
        entity.addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
            ExtendedProperties.NAME to AndroidEvent2.EXTNAME_CATEGORIES,
            ExtendedProperties.VALUE to "Cat 1\\Cat 2"
        ))
        processor.process(entity, entity, result)
        assertEquals(listOf("Cat 1", "Cat 2"), result.categories)
    }

}