/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.Event
import net.fortuna.ical4j.model.property.Clazz

class AccessLevelBuilder: AndroidEntityBuilder {

    override fun build(from: Event, main: Event, to: Entity) {
        to.entityValues.put(
            Events.ACCESS_LEVEL,
            when (from.classification) {
                Clazz.PUBLIC -> Events.ACCESS_PUBLIC
                Clazz.CONFIDENTIAL -> Events.ACCESS_CONFIDENTIAL
                null -> Events.ACCESS_DEFAULT
                else /* including Events.ACCESS_PRIVATE */ -> Events.ACCESS_PRIVATE
            }
        )

    }

}