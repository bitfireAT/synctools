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
import at.bitfire.ical4android.UnknownProperty
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Clazz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccessLevelProcessorTest {

    private val processor = AccessLevelProcessor()

    @Test
    fun `No access-level`() {
        val result = VEvent(/* initialise = */ false)
        val entity = Entity(ContentValues())
        processor.process(entity, entity, result)
        assertNull(result.classification)
    }

    @Test
    fun `No access-level, but retained classification`() {
        val result = VEvent(/* initialise = */ false)
        val entity = Entity(ContentValues())
        entity.addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
            ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
            ExtendedProperties.VALUE to "[\"CLASS\",\"x-other\"]"
        ))
        processor.process(entity, entity, result)
        assertEquals(Clazz("x-other"), result.classification)
    }

    @Test
    fun `Access-level DEFAULT`() {
        val result = VEvent(/* initialise = */ false)
        val entity = Entity(contentValuesOf(
            Events.ACCESS_LEVEL to Events.ACCESS_DEFAULT
        ))
        processor.process(entity, entity, result)
        assertNull(result.classification)
    }

    @Test
    fun `Access-level DEFAULT plus retained classification`() {
        val result = VEvent(/* initialise = */ false)
        val entity = Entity(contentValuesOf(
            Events.ACCESS_LEVEL to Events.ACCESS_DEFAULT
        ))
        entity.addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
            ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
            ExtendedProperties.VALUE to "[\"CLASS\",\"x-other\"]"
        ))
        processor.process(entity, entity, result)
        assertEquals(Clazz("x-other"), result.classification)
    }

    @Test
    fun `Access-level PUBLIC`() {
        val result = VEvent(/* initialise = */ false)
        val entity = Entity(contentValuesOf(
            Events.ACCESS_LEVEL to Events.ACCESS_PUBLIC
        ))
        processor.process(entity, entity, result)
        assertEquals(Clazz.PUBLIC, result.classification)
    }

    @Test
    fun `Access-level PRIVATE`() {
        val result = VEvent(/* initialise = */ false)
        val entity = Entity(contentValuesOf(
            Events.ACCESS_LEVEL to Events.ACCESS_PRIVATE
        ))
        processor.process(entity, entity, result)
        assertEquals(Clazz.PRIVATE, result.classification)
    }

    @Test
    fun `Access-level CONFIDENTIAL`() {
        val result = VEvent(/* initialise = */ false)
        val entity = Entity(contentValuesOf(
            Events.ACCESS_LEVEL to Events.ACCESS_CONFIDENTIAL
        ))
        processor.process(entity, entity, result)
        assertEquals(Clazz.CONFIDENTIAL, result.classification)
    }

}