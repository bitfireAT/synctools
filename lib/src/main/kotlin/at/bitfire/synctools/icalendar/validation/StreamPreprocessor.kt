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
     * @param line      full line of an iCalendar lines to validate / fix
     *
     * @return the potentially fixed version of [line]
     */
    fun repairLine(line: String): String

}