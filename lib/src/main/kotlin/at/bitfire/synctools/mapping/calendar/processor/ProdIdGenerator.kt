/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.processor

fun interface ProdIdGenerator {

    /**
     * Generates a `PRODID` string using additional package names.
     *
     * @param additionalComponents  package names that have modified/generated the iCalendar (like `com.example.app.calendar`; may be empty)
     *
     * @return the full `PRODID` string, with the package names and probably additional information added
     * (like `MyApp/1.0 (com.example.app.calendar/3.35)`)
     */
    fun generateProdId(additionalComponents: List<String>): String

}