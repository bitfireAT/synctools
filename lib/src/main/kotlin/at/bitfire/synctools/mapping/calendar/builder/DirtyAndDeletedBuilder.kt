/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event

class DirtyAndDeletedBuilder: AndroidEntityBuilder {

    override fun build(from: Event, main: Event, to: Entity) {
        // DIRTY and DELETED is always unset when we create or update an event row
        to.entityValues.put(Events.DIRTY, 0)
        to.entityValues.put(Events.DELETED, 0)
    }

}