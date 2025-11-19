/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.property.ProdId

fun interface ProdIdGenerator {

    /**
     * Generates a `PRODID` string using additional package names.
     *
     * @param packages  package names that have modified/generated the iCalendar (like `com.example.app.calendar`; may be empty)
     *
     * @return the generated `PRODID` property, with possible additional information (like `PRODID:MyApp/1.0`)
     */
    fun generateProdId(packages: List<String>): ProdId

}

class DefaultProdIdGenerator(
    private val prodId: String
): ProdIdGenerator {

    override fun generateProdId(packages: List<String>): ProdId {
        val params = ParameterList()

        // check compatibility first
        /*if (packages.isNotEmpty()) {
            val packagesStr = packages.joinToString(",")
            params.add(XParameter(PARAMETER_MUTATORS, packagesStr))
        }*/

        return ProdId(params, prodId)
    }

    /*companion object {
        const val PARAMETER_MUTATORS = "x-mutators"
    }*/

}