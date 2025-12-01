/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.RecurrenceId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OriginalInstanceTimeHandlerTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")!!

    private val handler = OriginalInstanceTimeHandler(tzRegistry)

    @Test
    fun `Original event is all-day`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ORIGINAL_INSTANCE_TIME to 1594080000000L,
            Events.ORIGINAL_ALL_DAY to 1
        ))
        handler.process(entity, Entity(ContentValues()), result)
        assertEquals(RecurrenceId(Date("20200707")), result.recurrenceId)
    }

    @Test
    fun `Original event is not all-day`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ORIGINAL_INSTANCE_TIME to 1758550428000L,
            Events.ORIGINAL_ALL_DAY to 0,
            Events.EVENT_TIMEZONE to tzVienna.id
        ))
        handler.process(entity, Entity(ContentValues()), result)
        assertEquals(RecurrenceId(DateTime("20250922T161348", tzVienna)), result.recurrenceId)
    }

}