/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapper.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapper.calendar.propertyListOf
import at.bitfire.synctools.test.assertEntityEquals
import io.mockk.mockk
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.RecurrenceId
import org.junit.Test

class OriginalInstanceTimeBuilderTest {

    @Test
    fun testRecurId_AllDay() {
        val entity = Entity(ContentValues())
        val vEvent = VEvent(propertyListOf(
            RecurrenceId(Date("20250628"))
        ))
        OriginalInstanceTimeBuilder.intoEntity(mockk(), vEvent, vEvent, entity)
        assertEntityEquals(
            Entity(contentValuesOf(Events.ORIGINAL_INSTANCE_TIME to 1751068800000)),
            entity
        )
    }

    @Test
    fun testRecurId_TimeUTC() {
        val entity = Entity(ContentValues())
        val vEvent = VEvent(propertyListOf(
            RecurrenceId(DateTime("20250628T010203Z"))
        ))
        OriginalInstanceTimeBuilder.intoEntity(mockk(), vEvent, vEvent, entity)
        assertEntityEquals(
            Entity(contentValuesOf(Events.ORIGINAL_INSTANCE_TIME to 1751072523000)),
            entity
        )
    }

    @Test
    fun testRecurId_Null() {
        val entity = Entity(ContentValues())
        val vEvent = VEvent()
        OriginalInstanceTimeBuilder.intoEntity(mockk(), vEvent, vEvent, entity)
        assertEntityEquals(
            Entity(contentValuesOf()),
            entity
        )
    }

}