/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import java.util.TimeZone

inline fun withDefaultTimeZone(timeZone: TimeZone, block: () -> Unit) {
    val originalTimeZone = TimeZone.getDefault()
    try {
        TimeZone.setDefault(timeZone)

        block()
    } finally {
        TimeZone.setDefault(originalTimeZone)
    }
}