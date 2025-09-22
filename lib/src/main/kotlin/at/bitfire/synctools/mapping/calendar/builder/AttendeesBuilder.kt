/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Attendees
import at.bitfire.ical4android.Event
import at.bitfire.synctools.mapping.calendar.AttendeeMappings
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.property.Attendee

class AttendeesBuilder(
    private val calendar: AndroidCalendar
): AndroidEntityBuilder {

    override fun build(from: Event, main: Event, to: Entity) {
        for (attendee in from.attendees)
            to.addSubValue(Attendees.CONTENT_URI, buildAttendee(attendee, from))
    }

    private fun buildAttendee(attendee: Attendee, event: Event): ContentValues {
        val values = ContentValues()
        val organizer = event.organizerEmail ?:
            /* no ORGANIZER, use current account owner as ORGANIZER */
            calendar.ownerAccount ?: calendar.account.name

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
        AttendeeMappings.iCalendarToAndroid(attendee, values, organizer)

        val status = when(attendee.getParameter(Parameter.PARTSTAT) as? PartStat) {
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