/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.test

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import org.junit.Assert.assertEquals

fun assertContentValuesEqual(expected: ContentValues, actual: ContentValues, onlyFieldsInExpected: Boolean = false, message: String? = null) {
    // assert keys are equal
    val expectedKeys = expected.keySet()
    val actualKeys = if (onlyFieldsInExpected)
        actual.keySet().intersect(expectedKeys)
    else
        actual.keySet()
    assertEquals(message, expectedKeys, actualKeys)

    // keys are equal → assert key values (in String representation)
    for (key in expectedKeys)
        assertEquals("$key", expected.getAsString(key),  actual.getAsString(key))
}

fun assertEntitiesEqual(expected: Entity, actual: Entity, onlyFieldsInExpected: Boolean = false) {
    assertContentValuesEqual(expected.entityValues, actual.entityValues, onlyFieldsInExpected, "entityValues")

    assertEquals("subValues.size", expected.subValues.size, actual.subValues.size)
    for (expectedValue in expected.subValues)
        assertContentValuesEqual(
            expectedValue.values,
            actual.subValues.first { it.uri == expectedValue.uri }.values,
            onlyFieldsInExpected,
            "subValues"
        )
}

fun assertEventAndExceptionsEqual(expected: EventAndExceptions, actual: EventAndExceptions, onlyFieldsInExpected: Boolean = false) {
    assertEntitiesEqual(expected.main, actual.main, onlyFieldsInExpected)

    assertEquals(expected.exceptions.size, actual.exceptions.size)
    for (expectedException in expected.exceptions) {
        val expectedInstanceTime = expectedException.entityValues.getAsLong(Events.ORIGINAL_INSTANCE_TIME)
        val actualException = actual.exceptions.first {
            val actualInstanceTime = it.entityValues.getAsLong(Events.ORIGINAL_INSTANCE_TIME)
            actualInstanceTime == expectedInstanceTime
        }
        assertEntitiesEqual(expectedException, actualException, onlyFieldsInExpected)
    }
}

fun Entity.withIntField(name: String, value: Long?) =
    Entity(ContentValues(this.entityValues)).also { newEntity ->
        newEntity.entityValues.put(name, value)
        newEntity.subValues.addAll(subValues)
    }

fun Entity.withId(eventId: Long) =
    this.withIntField(Events._ID, eventId)

fun EventAndExceptions.withId(mainEventId: Long) =
    EventAndExceptions(
        main = main.withId(mainEventId),
        exceptions = exceptions.map { exception ->
            exception.withIntField(Events.ORIGINAL_ID, mainEventId)
        }
    )