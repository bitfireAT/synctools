/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.exception.InvalidLocalResourceException
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Uid

class UidProcessor(): AndroidEventFieldProcessor {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val uid = main.entityValues.getAsString(Events.UID_2445)
            ?: throw InvalidLocalResourceException("Main event doesn't have a UID")

        to.properties += Uid(uid)
    }

}