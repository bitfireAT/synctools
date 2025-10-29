/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.content.Entity

/**
 * Represents a set of a local Android event and associated exception events that are stored together. It consists of
 *
 * - a (potentially recurring) main event,
 * - optional exceptions of this main event (exception instances, only useful if main event is recurring).
 */
data class EventAndExceptions(
    val main: Entity,
    val exceptions: List<Entity>
)