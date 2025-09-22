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
import at.bitfire.ical4android.UnknownProperty
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.XProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnknownPropertiesProcessorTest {

    private val processor = UnknownPropertiesProcessor()

    @Test
    fun `No unknown properties`() {
        val result = Event()
        val entity = Entity(ContentValues())
        processor.process(entity, entity, result)
        assertTrue(result.unknownProperties.isEmpty())
    }

    @Test
    fun `Three unknown properties, one of them excluded`() {
        val result = Event()
        val entity = Entity(ContentValues())
        entity.addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(     // used by ClassificationProcessor
            ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
            ExtendedProperties.VALUE to "[\"CLASS\", \"CONFIDENTIAL\"]"
        ))
        entity.addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
            ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
            ExtendedProperties.VALUE to "[\"X-PROP1\", \"value 1\"]"
        ))
        entity.addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
            ExtendedProperties.NAME to UnknownProperty.CONTENT_ITEM_TYPE,
            ExtendedProperties.VALUE to "[\"X-PROP2\", \"value 2\", {\"arg1\": \"arg-value\"}]"
        ))
        processor.process(entity, entity, result)
        assertEquals(listOf(
            XProperty("X-PROP1", "value 1"),
            XProperty("X-PROP2", "value 2").apply {
                parameters.add(XParameter("ARG1", "arg-value"))
            },
        ), result.unknownProperties)
    }

}