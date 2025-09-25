/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.Version
import java.io.Writer

class ICalendarWriter(
    private val prodId: ProdId
) {

    fun write(components: AssociatedComponents<*>, to: Writer) {
        val ical = Calendar()

        // VCALENDAR with PRODID
        ical.properties += Version.VERSION_2_0
        ical.properties += prodId

        // add components
        if (components.main != null)
            ical.components += components.main
        for (exception in components.exceptions)
            ical.components += exception

        // TODO: add minified VTIMEZONEs

        CalendarOutputter(/* validating = */ false).output(ical, to)
    }

}