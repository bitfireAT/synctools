/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.content.ContentValues
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.parameter.CuType
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.Role
import net.fortuna.ical4j.model.property.Attendee

/**
 * Defines mappings between Android [Attendees] and iCalendar parameters.
 *
 * Because the available Android values are quite different from the one in iCalendar, the
 * mapping is very lossy. Some special mapping rules are defined:
 *
 *   - ROLE=CHAIR   ⇄ ATTENDEE_TYPE=TYPE_SPEAKER
 *   - CUTYPE=GROUP ⇄ ATTENDEE_TYPE=TYPE_PERFORMER
 *   - CUTYPE=ROOM  ⇄ ATTENDEE_TYPE=TYPE_RESOURCE, ATTENDEE_RELATIONSHIP=RELATIONSHIP_PERFORMER
 */
object AttendeeMappings {

    /**
     * Maps Android [Attendees.ATTENDEE_TYPE] and [Attendees.ATTENDEE_RELATIONSHIP] to
     * iCalendar [CuType] and [Role] according to this matrix:
     *
     *     TYPE ↓ / RELATIONSHIP → ATTENDEE¹  PERFORMER  SPEAKER   NONE
     *     REQUIRED                indᴰ,reqᴰ  gro,reqᴰ   indᴰ,cha  unk,reqᴰ
     *     OPTIONAL                indᴰ,opt   gro,opt    indᴰ,cha  unk,opt
     *     NONE                    indᴰ,reqᴰ  gro,reqᴰ   indᴰ,cha  unk,reqᴰ
     *     RESOURCE                res,reqᴰ   roo,reqᴰ   res,cha   res,reqᴰ
     *
     *     ᴰ default value
     *     ¹ includes ORGANIZER
     *
     * @param row        Android attendee row to map
     * @param attendee   iCalendar attendee to fill
     */
    fun androidToICalendar(row: ContentValues, attendee: Attendee) {
        TODO("ical4j 4.x")
    }


    /**
     * Maps iCalendar [CuType] and [Role] to Android [CalendarContract.AttendeesColumns.ATTENDEE_TYPE] and
     * [CalendarContract.AttendeesColumns.ATTENDEE_RELATIONSHIP] according to this matrix:
     *
     *     CuType ↓ / Role →   CHAIR    REQ-PARTICIPANT¹ᴰ OPT-PARTICIPANT  NON-PARTICIPANT
     *     INDIVIDUALᴰ         req,spk  req,att           opt,att          non,att
     *     UNKNOWN²            req,spk  req,non           opt,non          non,non
     *     GROUP               req,spk  req,per           opt,per          non,per
     *     RESOURCE            res,spk  res,non           res,non          res,non
     *     ROOM                ::: res,per ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
     *
     *     ᴰ default value
     *     ¹ custom/unknown ROLE values must be treated as REQ-PARTICIPANT
     *     ² custom/unknown CUTYPE values must be treated as UNKNOWN
     *
     *  When [attendee] is the [organizer], [CalendarContract.Attendees.ATTENDEE_RELATIONSHIP] = RELATIONSHIP_ATTENDEE
     *  is replaced by [CalendarContract.Attendees.RELATIONSHIP_ORGANIZER].
     *
     * @param attendee   iCalendar attendee to map
     * @param to         where to mapped values should be put into
     * @param organizer  email address of iCalendar ORGANIZER; used to determine whether [attendee] is the organizer
     */
    fun iCalendarToAndroid(attendee: Attendee, to: ContentValues, organizer: String) {
        TODO("ical4j 4.x")
    }

}