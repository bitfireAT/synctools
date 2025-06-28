/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapper.calendar.processor

import at.bitfire.synctools.storage.calendar.AndroidEvent2
import net.fortuna.ical4j.model.Calendar

class AndroidEventProcessor(
    val androidEvent: AndroidEvent2
) {

    fun toVEvents(): Calendar {
        TODO()
    }

}