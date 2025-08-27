/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.provider.CalendarContract.Events
import at.bitfire.synctools.storage.emptyEntity
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OriginalSyncIdBuilderTest {

    private val builder = OriginalSyncIdBuilder("main-sync-id")

    @Test
    fun `Skips main event`() {
        val main = VEvent()
        val result = emptyEntity()
        assertTrue(builder.build(
            from = main,
            main = main,
            to = result
        ))
    }

    @Test
    fun `Sets ORIGINAL_SYNC_ID`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        ))
        assertEquals("main-sync-id", result.entityValues.getAsString(Events.ORIGINAL_SYNC_ID))
    }

}