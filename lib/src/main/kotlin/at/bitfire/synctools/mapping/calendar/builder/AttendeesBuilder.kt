/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Attendees
import at.bitfire.synctools.mapping.calendar.AttendeeMappings
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.property.Attendee

class AttendeesBuilder(
    private val accountName: String
): AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        val attendees = from.getProperties<Attendee>(Property.ATTENDEE)
        if (attendees.isNotEmpty()) {
            val organizer = from.organizer
            val organizerEmail = organizer?.calAddress?.takeIf { it.scheme.equals("mailto", true) }?.schemeSpecificPart
                ?: accountName

            for (attendee in attendees) {
                val attendeeValues = buildAttendee(attendee, organizerEmail)
                if (attendeeValues != null)
                    to.addSubValue(Attendees.CONTENT_URI, attendeeValues)
            }
        }
        return true
    }

    private fun buildAttendee(attendee: Attendee, organizerEmail: String): ContentValues? {
        val values = ContentValues()

        val member = attendee.calAddress
        if (member.scheme.equals("mailto", true))   // attendee identified by email
            values.put(Attendees.ATTENDEE_EMAIL, member.schemeSpecificPart)
        else {
            // attendee identified by other URI
            values.put(Attendees.ATTENDEE_ID_NAMESPACE, member.scheme)
            values.put(Attendees.ATTENDEE_IDENTITY, member.schemeSpecificPart)

            attendee.getParameter<Email>(Parameter.EMAIL)?.let { email ->
                values.put(Attendees.ATTENDEE_EMAIL, email.value)
            }
        }

        attendee.getParameter<Cn>(Parameter.CN)?.let { cn ->
            values.put(Attendees.ATTENDEE_NAME, cn.value)
        }

        // type/relation mapping is complex and thus outsourced to AttendeeMappings
        AttendeeMappings.iCalendarToAndroid(attendee, values, organizerEmail)

        val status = when(attendee.getParameter<PartStat>(Parameter.PARTSTAT)) {
            PartStat.ACCEPTED     -> Attendees.ATTENDEE_STATUS_ACCEPTED
            PartStat.DECLINED     -> Attendees.ATTENDEE_STATUS_DECLINED
            PartStat.TENTATIVE    -> Attendees.ATTENDEE_STATUS_TENTATIVE
            PartStat.DELEGATED    -> Attendees.ATTENDEE_STATUS_NONE
            else /* default: PartStat.NEEDS_ACTION */ -> Attendees.ATTENDEE_STATUS_INVITED
        }
        values.put(Attendees.ATTENDEE_STATUS, status)

        return values
    }

}