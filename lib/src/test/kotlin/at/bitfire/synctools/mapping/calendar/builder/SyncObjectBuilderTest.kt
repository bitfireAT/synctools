/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.provider.CalendarContract.Events
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import at.bitfire.synctools.storage.emptyEntity
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncObjectBuilderTest {

    val builder = SyncObjectBuilder(
        calendarId = 123,
        syncId = "sync-id",
        eTag = "etag",
        scheduleTag = "schedule-tag",
        flags = 654
    )

    @Test
    fun `sets fields`() {
        val event = VEvent()
        val result = emptyEntity()
        builder.build(from = event, main = event, to = result)
        assertEquals(123, result.entityValues.getAsInteger(Events.CALENDAR_ID))
        assertEquals(0, result.entityValues.getAsInteger(Events.DIRTY))
        assertEquals(0, result.entityValues.getAsInteger(Events.DELETED))
    }

    @Test
    fun `sets main fields (main event)`() {
        val event = VEvent()
        val result = emptyEntity()
        builder.build(from = event, main = event, to = result)

        // always set
        assertEquals(123, result.entityValues.getAsInteger(Events.CALENDAR_ID))
        assertEquals(654, result.entityValues.getAsInteger(AndroidEvent2.COLUMN_FLAGS))

        // ETag/Schedule-Tag only set for main events
        assertEquals("etag", result.entityValues.getAsString(AndroidEvent2.COLUMN_ETAG))
        assertEquals("schedule-tag", result.entityValues.getAsString(AndroidEvent2.COLUMN_SCHEDULE_TAG))
    }

    @Test
    fun `sets null main fields (non-main event)`() {
        val result = emptyEntity()
        builder.build(from = VEvent(), main = VEvent(), to = result)

        // always set
        assertEquals(123, result.entityValues.getAsInteger(Events.CALENDAR_ID))
        assertEquals(654, result.entityValues.getAsInteger(AndroidEvent2.COLUMN_FLAGS))

        // ETag/Schedule-Tag only set for main events
        assertTrue(result.entityValues.containsKey(AndroidEvent2.COLUMN_ETAG))
        assertNull(result.entityValues.get(AndroidEvent2.COLUMN_ETAG))

        assertTrue(result.entityValues.containsKey(AndroidEvent2.COLUMN_SCHEDULE_TAG))
        assertNull(result.entityValues.get(AndroidEvent2.COLUMN_SCHEDULE_TAG))
    }

}