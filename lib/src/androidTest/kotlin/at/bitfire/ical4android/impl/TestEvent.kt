/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.impl

import android.content.ContentValues
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.AndroidEventFactory
import at.bitfire.ical4android.Event
import java.util.UUID

class TestEvent: AndroidEvent {

    constructor(calendar: AndroidCalendar<AndroidEvent>, values: ContentValues)
        : super(calendar, values)

    constructor(calendar: TestCalendar, event: Event)
            : super(calendar, event, UUID.randomUUID().toString(), null, null, 0)


    object Factory: AndroidEventFactory<TestEvent> {
        override fun fromProvider(calendar: AndroidCalendar<AndroidEvent>, values: ContentValues) =
                TestEvent(calendar, values)
    }

}
