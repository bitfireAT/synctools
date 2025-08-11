/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.storage.emptyEntity
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Transp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AvailabilityBuilderTest {

    private val builder = AvailabilityBuilder()

    @Test
    fun `TRANSPARENCY is OPAQUE`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                Transp.OPAQUE
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(Events.AVAILABILITY_BUSY, result.entityValues.getAsInteger(Events.AVAILABILITY))
    }

    @Test
    fun `TRANSPARENCY is TRANSPARENT`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                Transp.TRANSPARENT
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(Events.AVAILABILITY_FREE, result.entityValues.getAsInteger(Events.AVAILABILITY))
    }

    @Test
    fun `No TRANSPARENCY`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        ))
        assertTrue(result.entityValues.containsKey(Events.AVAILABILITY))
        assertNull(result.entityValues.get(Events.AVAILABILITY))
    }

}