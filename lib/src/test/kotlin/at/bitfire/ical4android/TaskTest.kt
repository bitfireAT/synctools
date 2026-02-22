/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTest {

    @Test
    fun testAllDay() {
        assertTrue(Task().isAllDay())

        // DTSTART has priority
        TODO("ical4j 4.x")
        /*assertFalse(Task().apply {
            dtStart = DtStart(DateTime())
        }.isAllDay())
        assertFalse(Task().apply {
            dtStart = DtStart(DateTime())
            due = Due(Date())
        }.isAllDay())
        assertTrue(Task().apply {
            dtStart = DtStart(Date())
        }.isAllDay())
        assertTrue(Task().apply {
            dtStart = DtStart(Date())
            due = Due(DateTime())
        }.isAllDay())

        // if DTSTART is missing, DUE decides
        assertFalse(Task().apply {
            due = Due(DateTime())
        }.isAllDay())
        assertTrue(Task().apply {
            due = Due(Date())
        }.isAllDay())*/
    }

}