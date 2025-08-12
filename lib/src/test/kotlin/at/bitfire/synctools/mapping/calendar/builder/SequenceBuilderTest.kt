/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import at.bitfire.synctools.storage.emptyEntity
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Sequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SequenceBuilderTest {

    private val builder = SequenceBuilder()

    @Test
    fun `SEQUENCE is 0`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                Sequence(0)
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(1, result.entityValues.getAsInteger(AndroidEvent2.COLUMN_SEQUENCE))
    }

    @Test
    fun `SEQUENCE is 1`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(propertyListOf(
                Sequence(1)
            )),
            main = VEvent(),
            to = result
        ))
        assertEquals(0, result.entityValues.getAsInteger(AndroidEvent2.COLUMN_SEQUENCE))
    }

    @Test
    fun `No SEQUENCE`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        ))
        assertEquals(0, result.entityValues.getAsInteger(AndroidEvent2.COLUMN_SEQUENCE))
    }

}