/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import at.bitfire.synctools.test.assertEntitiesEqual
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventAndExceptionsTest {

    @Test
    fun `merge main values`() {
        val into = EventAndExceptions(
            main = Entity(contentValuesOf(
                "name1" to "value1",
                "name2" to "value2"
            )),
            exceptions = emptyList()
        )
        into.mergeFrom(EventAndExceptions(
            main = Entity(contentValuesOf(
                "name2" to null,
                "name3" to 123
            )),
            exceptions = emptyList()
        ))
        assertEntitiesEqual(
            Entity(contentValuesOf(
                "name1" to "value1",
                "name2" to null,
                "name3" to 123
            )),
            into.main
        )
    }

    @Test
    fun `merge exception by ORIGINAL_INSTANCE_TIME`() {
        val into = EventAndExceptions(
            main = Entity(ContentValues()),
            exceptions = listOf(
                Entity(contentValuesOf(
                    "name1" to "value1",
                    "name3" to "value3",
                    Events.ORIGINAL_INSTANCE_TIME to 123
                ))
            )
        )
        into.mergeFrom(EventAndExceptions(
            main = Entity(ContentValues()),
            exceptions = listOf(
                Entity(contentValuesOf(
                    "name1" to "name1a",
                    "name2" to "value2",
                    Events.ORIGINAL_INSTANCE_TIME to 123
                ))
            )
        ))
        assertEntitiesEqual(
            Entity(contentValuesOf(
                "name1" to "name1a",
                "name2" to "value2",
                "name3" to "value3",
                Events.ORIGINAL_INSTANCE_TIME to 123
            )),
            into.exceptions.first()
        )
    }

    @Test
    fun `leftover other exception`() {
        val into = EventAndExceptions(
            main = Entity(ContentValues()),
            exceptions = emptyList()
        )
        into.mergeFrom(EventAndExceptions(
            main = Entity(ContentValues()),
            exceptions = listOf(
                Entity(contentValuesOf(
                    "name1" to "value1",
                    Events.ORIGINAL_INSTANCE_TIME to 123
                ))
            )
        ))
        assertTrue(into.exceptions.isEmpty())
    }

    @Test
    fun `missing other exception`() {
        val into = EventAndExceptions(
            main = Entity(ContentValues()),
            exceptions = listOf(
                Entity(contentValuesOf(
                    "name1" to "value1",
                    Events.ORIGINAL_INSTANCE_TIME to 123
                ))
            )
        )
        into.mergeFrom(EventAndExceptions(
            main = Entity(ContentValues()),
            exceptions = emptyList()
        ))
        assertEntitiesEqual(
            Entity(contentValuesOf(
                "name1" to "value1",
                Events.ORIGINAL_INSTANCE_TIME to 123
            )),
            into.exceptions.first()
        )
    }

}