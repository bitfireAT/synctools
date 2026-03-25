/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TaskTest {

    @Test
    fun testAllDay() {
        assertTrue(Task().isAllDay())

        // DTSTART has priority
        assertFalse(Task().apply {
            dtStart = DtStart(LocalDateTime.now())
        }.isAllDay())
        assertFalse(Task().apply {
            dtStart = DtStart(LocalDateTime.now())
            due = Due(LocalDate.now())
        }.isAllDay())
        assertTrue(Task().apply {
            dtStart = DtStart(LocalDate.now())
        }.isAllDay())
        assertTrue(Task().apply {
            dtStart = DtStart(LocalDate.now())
            due = Due(LocalDateTime.now())
        }.isAllDay())

        // if DTSTART is missing, DUE decides
        assertFalse(Task().apply {
            due = Due(LocalDateTime.now())
        }.isAllDay())
        assertTrue(Task().apply {
            due = Due(LocalDate.now())
        }.isAllDay())
    }

}