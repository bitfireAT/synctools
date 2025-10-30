/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

fun interface ProdIdGenerator {

    /**
     * Generates a `PRODID` string using additional package names.
     *
     * @param packages  package names that have modified/generated the iCalendar (like `com.example.app.calendar`; may be empty)
     *
     * @return the full `PRODID` string, with the package names and probably additional information added
     * (like `MyApp/1.0 (com.example.app.calendar)`)
     */
    fun generateProdId(packages: List<String>): String

}

class DefaultProdIdGenerator(
    private val baseId: String
): ProdIdGenerator {

    override fun generateProdId(packages: List<String>): String {
        val builder = StringBuilder(baseId)
        if (packages.isNotEmpty())
            builder .append(" (")
                .append(packages.joinToString(", "))
                .append(")")
        return builder.toString()
    }

}