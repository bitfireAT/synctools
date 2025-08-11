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
import net.fortuna.ical4j.model.property.ExRule

/**
 * Note that EXRULE is deprecated by RFC 5545 and shouldn't be used anymore.
 *
 * This mapping is only done for compatibility with RFC 2445 events.
 */
class ExRuleBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        // only build for recurring main events
        if (from !== main || !from.isRecurring()) {
            to.entityValues.putNull(Events.EXRULE)
            return true
        }

        val exRules = from.getProperties<ExRule>(Property.EXRULE)

        // concatenate multiple EXRULEs using RECURRENCE_RULE_SEPARATOR so that there's one rule per line
        val androidExRules = exRules.joinToString(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR) { it.value }

        to.entityValues.put(
            Events.EXRULE,
            androidExRules.trimToNull()      // use null if there are no lines
        )
        return true
    }

}