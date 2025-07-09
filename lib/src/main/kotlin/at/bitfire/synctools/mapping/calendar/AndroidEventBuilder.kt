/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.Entity
import at.bitfire.synctools.icalendar.AssociatedComponents
import net.fortuna.ical4j.model.component.VEvent

class AndroidEventBuilder {

    fun fromEvents(
        associatedComponents: AssociatedComponents<VEvent>,
        syncId: String
    ): List<Entity> {
        // TODO build main event

        // TODO build exception events

        TODO()
    }

}