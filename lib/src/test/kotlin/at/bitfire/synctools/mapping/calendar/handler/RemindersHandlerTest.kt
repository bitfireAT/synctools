/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Reminders
import androidx.core.content.contentValuesOf
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class RemindersHandlerTest {

    private val accountName = "user@example.com"
    private val handler = RemindersHandler(accountName)

    @Test
    fun `Email reminder`() {
        // account name looks like an email address
        assumeTrue(accountName.endsWith("@example.com"))

        val entity = Entity(ContentValues())
        entity.addSubValue(Reminders.CONTENT_URI, contentValuesOf(
            Reminders.METHOD to Reminders.METHOD_EMAIL,
            Reminders.MINUTES to 10
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        val alarm = result.alarms.first()
        assertEquals(Action.EMAIL, alarm.action)
        assertNotNull(alarm.summary)
        assertNotNull(alarm.description)
    }

    @Test
    fun `Email reminder (account name is not an email address)`() {
        // test account name that doesn't look like an email address
        val nonEmailAccountName = "ical4android"
        val processor2 = RemindersHandler(nonEmailAccountName)

        val entity = Entity(ContentValues())
        entity.addSubValue(Reminders.CONTENT_URI, contentValuesOf(
            Reminders.METHOD to Reminders.METHOD_EMAIL,
            Reminders.MINUTES to 10
        ))
        val result = VEvent()
        processor2.process(entity, entity, result)
        val alarm = result.alarms.first()
        assertEquals(Action.DISPLAY, alarm.action)
        assertNotNull(alarm.description)
    }

    @Test
    fun `Non-email reminder`() {
        for (type in arrayOf(null, Reminders.METHOD_ALARM, Reminders.METHOD_ALERT, Reminders.METHOD_DEFAULT, Reminders.METHOD_SMS)) {
            val entity = Entity(ContentValues())
            entity.addSubValue(Reminders.CONTENT_URI, contentValuesOf(
                Reminders.METHOD to type,
                Reminders.MINUTES to 10
            ))
            val result = VEvent()
            handler.process(entity, entity, result)
            val alarm = result.alarms.first()
            assertEquals(Action.DISPLAY, alarm.action)
            assertNotNull(alarm.description)
        }
    }


    @Test
    fun `Number of minutes is positive`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Reminders.CONTENT_URI, contentValuesOf(
            Reminders.METHOD to Reminders.METHOD_ALERT,
            Reminders.MINUTES to 10
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        val alarm = result.alarms.first()
        assertEquals(Duration.ofMinutes(-10), alarm.trigger.duration)
    }

    @Test
    fun `Number of minutes is negative`() {
        val entity = Entity(ContentValues())
        entity.addSubValue(Reminders.CONTENT_URI, contentValuesOf(
            Reminders.METHOD to Reminders.METHOD_ALERT,
            Reminders.MINUTES to -10
        ))
        val result = VEvent()
        handler.process(entity, entity, result)
        val alarm = result.alarms.first()
        assertEquals(Duration.ofMinutes(10), alarm.trigger.duration)
    }

}