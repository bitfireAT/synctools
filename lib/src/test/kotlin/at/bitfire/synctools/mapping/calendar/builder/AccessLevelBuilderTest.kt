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
import net.fortuna.ical4j.model.property.Clazz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccessLevelBuilderTest {

    private val builder = AccessLevelBuilder()

    @Test
    fun `CLASSIFICATION is CONFIDENTIAL`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                Clazz.CONFIDENTIAL
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(Events.ACCESS_CONFIDENTIAL, result.entityValues.getAsInteger(Events.ACCESS_LEVEL))
    }

    @Test
    fun `CLASSIFICATION is PRIVATE`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                Clazz.PRIVATE
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(Events.ACCESS_PRIVATE, result.entityValues.getAsInteger(Events.ACCESS_LEVEL))
    }

    @Test
    fun `CLASSIFICATION is X-OTHER`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                Clazz("X-OTHER")
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(Events.ACCESS_PRIVATE, result.entityValues.getAsInteger(Events.ACCESS_LEVEL))
    }

    @Test
    fun `No CLASSIFICATION`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        ))
        assertEquals(Events.ACCESS_DEFAULT, result.entityValues.getAsInteger(Events.ACCESS_LEVEL))
    }

}