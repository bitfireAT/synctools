/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar.validation

interface StreamPreprocessor {

    fun regexpForProblem(): Regex?

    /**
     * Fixes an iCalendar string.
     * The icalendar may not be complete, but just a chunk.
     * Lines won't be incomplete.
     *
     * @param original The complete iCalendar string
     * @return The complete iCalendar string, but fixed
     */
    fun fixString(original: String): String

}