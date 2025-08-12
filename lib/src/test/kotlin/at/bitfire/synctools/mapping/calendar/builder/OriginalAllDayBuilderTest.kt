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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OriginalAllDayBuilderTest {

    private val builder = OriginalAllDayBuilder()

    @Test
    fun `No DTSTART`() {
        val result = emptyEntity()
        assertFalse(builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        ))
    }

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
    fun `Sets ORIGINAL_ALL_DAY (main event DTSTART is DATE)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(),
            main = VEvent(Date(), "Some Event"),
            to = result
        ))
        assertEquals(1, result.entityValues.getAsInteger(Events.ORIGINAL_ALL_DAY))
    }

    @Test
    fun `Sets ORIGINAL_ALL_DAY (main event DTSTART is DATE-TIME)`() {
        val result = emptyEntity()
        assertTrue(builder.build(
            from = VEvent(),
            main = VEvent(DateTime(), "Some Event"),
            to = result
        ))
        assertEquals(0, result.entityValues.getAsInteger(Events.ORIGINAL_ALL_DAY))
    }

}