/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.provider.CalendarContract.Events
import at.bitfire.synctools.storage.emptyEntity
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.RecurrenceId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExceptionMainReferenceBuilderTest {

    val builder = ExceptionMainReferenceBuilder("main-sync-id")

    @Test
    fun `skips main event`() {
        val event = createException()
        val result = emptyEntity()
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertEquals(0, result.entityValues.size())
    }


    @Test
    fun `sets ORIGINAL_ALL_DAY (all-day main event)`() {
        val result = emptyEntity()
        builder.build(
            from = createException(),
            main = createException(time = Date()),
            to = result
        )
        assertEquals(1, result.entityValues.getAsInteger(Events.ORIGINAL_ALL_DAY))
    }

    @Test
    fun `sets ORIGINAL_ALL_DAY (non-all-day main event)`() {
        val result = emptyEntity()
        builder.build(
            from = createException(),
            main = createException(time = DateTime()),
            to = result
        )
        assertEquals(0, result.entityValues.getAsInteger(Events.ORIGINAL_ALL_DAY))
    }


    @Test
    fun `sets ORIGINAL_INSTANCE_TIME (all-day main event, all-day exception)`() {
        val result = emptyEntity()
        builder.build(
            from = createException(time = Date("20250811")),
            main = createException(time = Date()),
            to = result
        )
        assertEquals(
            1754870400000,  // Mon Aug 11 2025 00:00:00 GMT+0000
            result.entityValues.getAsLong(Events.ORIGINAL_INSTANCE_TIME)
        )
    }

    @Test
    fun `sets ORIGINAL_INSTANCE_TIME (all-day main event, non-all-day exception)`() {
        val result = emptyEntity()
        builder.build(
            from = createException(time = DateTime(1754917615000)),     // Mon Aug 11 2025 13:06:55 GMT+0000
            main = createException(time = Date()),
            to = result
        )
        assertEquals(
            1754870400000,  // Mon Aug 11 2025 00:00:00 GMT+0000
            result.entityValues.getAsLong(Events.ORIGINAL_INSTANCE_TIME)
        )
    }

    @Test
    fun `sets ORIGINAL_INSTANCE_TIME (non-all-day main event, all-day exception)`() {
        val result = emptyEntity()
        builder.build(
            main = createException(time = DateTime(1754917615000)),     // Mon Aug 11 2025 13:06:55 GMT+0000
            from = createException(time = Date("20250812")),
            to = result
        )
        assertEquals(
            1755004015000,  // 2025/08/12, but at 13:06:55 GMT+0000
            result.entityValues.getAsLong(Events.ORIGINAL_INSTANCE_TIME)
        )
    }

    @Test
    fun `sets ORIGINAL_INSTANCE_TIME (non-all-day main event, non-all-day exception)`() {
        val result = emptyEntity()
        builder.build(
            from = createException(time = DateTime(1754917615000)),     // Mon Aug 11 2025 13:06:55 GMT+0000
            main = createException(time = DateTime()),
            to = result
        )
        assertEquals(1754917615000, result.entityValues.getAsLong(Events.ORIGINAL_INSTANCE_TIME))
    }

    @Test
    fun `sets ORIGINAL_SYNC_ID`() {
        val result = emptyEntity()
        builder.build(
            from = createException(),
            main = createException(),
            to = result
        )
        assertEquals("main-sync-id", result.entityValues.getAsString(Events.ORIGINAL_SYNC_ID))
    }


    private fun createException(time: Date = Date()) = VEvent(
        /* start = */ time,
        /* end = */ time,
        /* summary = */ "Some Event"
    ).apply {
        // add required fields for exception
        properties += RecurrenceId(time)
    }

}