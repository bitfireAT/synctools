/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.component.VEvent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MutatorsProcessorTest {

    private val processor = MutatorsProcessor()

    @Test
    fun `No mutators`() {
        val result = VEvent()
        val entity = Entity(ContentValues())
        processor.process(entity, entity, result)
        // assertTrue(result.userAgents.isEmpty())
        // TODO
    }

    @Test
    fun `Multiple mutators`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.MUTATORS to "com.example.calendar,com.example.another.calendar"
        ))
        processor.process(entity, entity, result)
        // assertEquals(listOf("com.example.calendar", "com.example.another.calendar"), result.userAgents)
        // TODO
    }

}