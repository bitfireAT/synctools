/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract
import at.bitfire.vcard4android.Utils.trimToNull
import net.fortuna.ical4j.model.component.VEvent

class EventLocationBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        val location = from.location?.value
        to.entityValues.put(CalendarContract.Events.EVENT_LOCATION, location.trimToNull())
        return true
    }

}