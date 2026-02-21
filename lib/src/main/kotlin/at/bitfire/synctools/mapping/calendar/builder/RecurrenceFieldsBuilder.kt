/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.requireDtStart
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import java.util.logging.Logger

class RecurrenceFieldsBuilder: AndroidEntityBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        TODO("ical4j 4.x")
    }

}