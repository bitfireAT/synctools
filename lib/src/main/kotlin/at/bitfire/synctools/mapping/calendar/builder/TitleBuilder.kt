/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.vcard4android.Utils.trimToNull
import net.fortuna.ical4j.model.component.VEvent

class TitleBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        val summary = from.summary?.value
        to.entityValues.put(Events.TITLE, summary.trimToNull())
        return true
    }

}