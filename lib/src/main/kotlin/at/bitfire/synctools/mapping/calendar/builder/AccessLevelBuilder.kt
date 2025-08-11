/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Clazz

class AccessLevelBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        val accessLevel: Int? = when (from.classification) {
            Clazz.PUBLIC ->
                Events.ACCESS_PUBLIC

            Clazz.CONFIDENTIAL ->
                Events.ACCESS_CONFIDENTIAL

            Clazz.PRIVATE ->
                Events.ACCESS_PRIVATE

            else /* including Events.ACCESS_PRIVATE */  ->
                Events.ACCESS_DEFAULT
        }

        to.entityValues.put(Events.ACCESS_LEVEL, accessLevel)

        // TODO retainClassification

        return true
    }

}