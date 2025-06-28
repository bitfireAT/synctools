/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapper.calendar

import net.fortuna.ical4j.model.component.VEvent

/**
 * @param mainVEvent        main VEVENT (with UID, without RECURRENCE-ID), may be `null` if only exceptions are present
 * @param exceptions        exceptions (each with UID and RECURRENCE-ID); UID must be
 *   1. the same as the UID of [mainVEvent] (if present),
 *   2. the same for all exceptions.
 *
 * If no [mainVEvent] is present, [exceptions] must not be empty.
 *
 * @throws IllegalArgumentException   when the constraints above are violated
 */
data class AssociatedVEvents(
    val mainVEvent: VEvent?,
    val exceptions: List<VEvent>
) {

    init {
        validate()
    }

    /**
     * Validates the requirements of [mainVEvent] and [exceptions] UIDs.
     *
     * @throws IllegalArgumentException     if [mainVEvent] and/or [exceptions] UIDs don't match
     */
    private fun validate() {
        val mainUid =
            if (mainVEvent != null) {
                if (mainVEvent.uid == null)
                    throw IllegalArgumentException("Main event must have an UID")
                if (mainVEvent.recurrenceId != null)
                    throw IllegalArgumentException("Main event must not have a RECURRENCE-ID")

                mainVEvent.uid
            }
            else
                null

        val exceptionsUid =
            if (exceptions.isNotEmpty()) {
                if (exceptions.any { it.recurrenceId == null } )
                    throw IllegalArgumentException("Exceptions must have RECURRENCE-ID")

                val firstExceptionUid = exceptions.first().uid
                if (exceptions.any { it.uid != firstExceptionUid })
                    throw IllegalArgumentException("Exceptions must not have different UIDs")
                firstExceptionUid
            } else
                null

        if (mainUid != null && exceptionsUid != null && exceptionsUid != mainUid)
            throw IllegalArgumentException("Exceptions must have the same UID as the main event")
    }

}