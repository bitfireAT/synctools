/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.synctools.icalendar.isRecurring
import at.bitfire.vcard4android.Utils.trimToNull
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.ExDate

class ExDateBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        // only build for recurring main events
        if (from !== main || !from.isRecurring()) {
            to.entityValues.putNull(Events.EXDATE)
            return true
        }

        val exDates = from.getProperties<ExDate>(Property.EXDATE)

        // concatenate multiple EXRULEs using RECURRENCE_RULE_SEPARATOR so that there's one rule per line
        val androidExDates = AndroidTimeUtils.recurrenceSetsToAndroidString(exDates, from.startDate?.date)

        to.entityValues.put(
            Events.EXDATE,
            androidExDates.trimToNull()      // use null if there are no lines
        )
        return true
    }

}