/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.property.Clazz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RetainedClassificationBuilderTest {

    private val builder = RetainedClassificationBuilder()

    @Test
    fun `No CLASS`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(),
            main = Event(),
            to = result
        )
        assertTrue(result.subValues.isEmpty())
    }

    @Test
    fun `CLASS is CONFIDENTIAL`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(classification = Clazz.CONFIDENTIAL),
            main = Event(),
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(
            contentValuesOf(
                ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
                ExtendedProperties.VALUE to "[\"CLASS\",\"CONFIDENTIAL\"]"
            ),
            result.subValues.first().values
        )
    }

    @Test
    fun `CLASS is PRIVATE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(classification = Clazz.PRIVATE),
            main = Event(),
            to = result
        )
        assertTrue(result.subValues.isEmpty())
    }

    @Test
    fun `CLASS is PUBLIC`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(classification = Clazz.PUBLIC),
            main = Event(),
            to = result
        )
        assertTrue(result.subValues.isEmpty())
    }

    @Test
    fun `CLASS is X-Something`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(classification = Clazz("X-Something")),
            main = Event(),
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(
            contentValuesOf(
                ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
                ExtendedProperties.VALUE to "[\"CLASS\",\"X-Something\"]"
            ),
            result.subValues.first().values
        )
    }

}