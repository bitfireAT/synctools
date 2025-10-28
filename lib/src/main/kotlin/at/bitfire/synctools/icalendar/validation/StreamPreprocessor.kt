/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar.validation

interface StreamPreprocessor {

    /**
     * Fixes an iCalendar string.
     *
     * @param lines The iCalendar lines to fix. Those may be the full iCalendar file, or just a part of it.
     * @return The fixed version of [lines].
     */
    fun fixString(lines: String): String

}