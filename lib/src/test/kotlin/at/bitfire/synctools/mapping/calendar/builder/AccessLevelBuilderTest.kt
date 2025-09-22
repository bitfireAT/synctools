/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.property.Clazz
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccessLevelBuilderTest {

    private val builder = AccessLevelBuilder()

    @Test
    fun `No classification`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(),
            main = Event(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ACCESS_LEVEL to Events.ACCESS_DEFAULT
        ), result.entityValues)
        assertEquals(0, result.subValues.size)
    }

    @Test
    fun `Classification is PUBLIC`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(classification = Clazz.PUBLIC),
            main = Event(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ACCESS_LEVEL to Events.ACCESS_PUBLIC
        ), result.entityValues)
        assertEquals(0, result.subValues.size)
    }

    @Test
    fun `Classification is PRIVATE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(classification = Clazz.PRIVATE),
            main = Event(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ACCESS_LEVEL to Events.ACCESS_PRIVATE
        ), result.entityValues)
        assertEquals(0, result.subValues.size)
    }

    @Test
    fun `Classification is CONFIDENTIAL`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(classification = Clazz.CONFIDENTIAL),
            main = Event(),
            to = result
        )

        assertContentValuesEqual(contentValuesOf(
            Events.ACCESS_LEVEL to Events.ACCESS_CONFIDENTIAL
        ), result.entityValues)

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
    fun `Classification is custom value`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(classification = Clazz("TOP-SECRET")),
            main = Event(),
            to = result
        )

        assertContentValuesEqual(contentValuesOf(
            Events.ACCESS_LEVEL to Events.ACCESS_PRIVATE
        ), result.entityValues)

        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(
            contentValuesOf(
                ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
                ExtendedProperties.VALUE to "[\"CLASS\",\"TOP-SECRET\"]"
            ),
            result.subValues.first().values
        )
    }

}