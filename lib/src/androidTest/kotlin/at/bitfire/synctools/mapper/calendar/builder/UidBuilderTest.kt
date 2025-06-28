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
import at.bitfire.synctools.test.assertEntityEquals
import io.mockk.mockk
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Uid
import org.junit.Test

class UidBuilderTest {

    @Test
    fun testUid_Null() {
        val entity = Entity(ContentValues())
        val vEvent = VEvent()
        UidBuilder.intoEntity(mockk(), vEvent, vEvent, entity)
        assertEntityEquals(
            Entity(contentValuesOf(Events.UID_2445 to null)),
            entity
        )
    }

    @Test
    fun testUid_Value() {
        val entity = Entity(ContentValues())
        val vEvent = VEvent().apply {
            properties.add(Uid("test@12345"))
        }
        UidBuilder.intoEntity(mockk(), vEvent, vEvent, entity)
        assertEntityEquals(
            Entity(contentValuesOf(Events.UID_2445 to "test@12345")),
            entity
        )
    }

}