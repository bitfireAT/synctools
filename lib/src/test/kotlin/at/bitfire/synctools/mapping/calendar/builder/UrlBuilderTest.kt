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
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import at.bitfire.synctools.test.assertContentValuesEqual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI

@RunWith(RobolectricTestRunner::class)
class UrlBuilderTest {

    private val builder = UrlBuilder()

    @Test
    fun `URL is URI`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(url = URI("https://example.com")),
            main = Event(),
            to = result
        )
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(
            contentValuesOf(
                ExtendedProperties.NAME to AndroidEvent2.EXTNAME_URL,
                ExtendedProperties.VALUE to "https://example.com"
            ),
            result.subValues.first().values
        )
    }

    @Test
    fun `No URL`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event(),
            main = Event(),
            to = result
        )
        assertTrue(result.subValues.isEmpty())
    }

}