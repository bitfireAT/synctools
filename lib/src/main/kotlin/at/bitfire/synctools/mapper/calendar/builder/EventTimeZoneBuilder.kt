/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapper.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.util.TimeZones

object EventTimeZoneBuilder: FeatureBuilder {

    override fun intoEntity(syncProperties: AndroidEvent2Builder.SyncProperties, vEvent: VEvent, mainVEvent: VEvent, entity: Entity) {
        val startDate = vEvent.startDate?.date

        val timeZone = if (startDate != null && startDate is DateTime)
            startDate.timeZone.id   // TBD only use Android time zones
        else
            TimeZones.UTC_ID

        entity.entityValues.put(Events.EVENT_TIMEZONE, timeZone)
    }

}