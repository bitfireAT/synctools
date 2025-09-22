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
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI

@RunWith(RobolectricTestRunner::class)
class UrlProcessorTest {

    private val processor = UrlProcessor()

    @Test
    fun `No URL`() {
        val result = Event()
        val entity = Entity(ContentValues())
        processor.process(entity, entity, result)
        assertNull(result.url)
    }

    @Test
    fun `Invalid URL`() {
        val result = Event()
        val entity = Entity(ContentValues())
        entity.addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
            ExtendedProperties.NAME to AndroidEvent2.EXTNAME_URL,
            ExtendedProperties.VALUE to "invalid\\uri"
        ))
        processor.process(entity, entity, result)
        assertNull(result.url)
    }

    @Test
    fun `Valid URL`() {
        val result = Event()
        val entity = Entity(ContentValues())
        entity.addSubValue(ExtendedProperties.CONTENT_URI, contentValuesOf(
            ExtendedProperties.NAME to AndroidEvent2.EXTNAME_URL,
            ExtendedProperties.VALUE to "https://example.com"
        ))
        processor.process(entity, entity, result)
        assertEquals(URI("https://example.com"), result.url)
    }

}