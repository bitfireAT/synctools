/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import android.content.Entity

/**
 * Base class for data classes that contains
 *
 * - a potentially recurring calendar object (event/task) and
 * - possible exceptions to it.
 */
data class EventAndExceptions(
    val main: Entity,
    val exceptions: List<Entity>
)