/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.content.Entity

/**
 * Represents a set of local events (like [at.bitfire.synctools.storage.calendar.AndroidEvent2] values)
 * that are stored together in one iCalendar object on the server. It consists of
 *
 * - a main component (like a main event),
 * - optional exceptions of this main component (exception instances).
 */
data class EventAndExceptions(
    val main: Entity,
    val exceptions: List<Entity>
)