/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapper.calendar

import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList

fun parameterListOf(vararg parameters: Parameter) = ParameterList().apply {
    parameters.forEach { add(it) }
}

fun propertyListOf(vararg properties: Property) = PropertyList<Property>().apply {
    properties.forEach { add(it) }
}

fun Date.isAllDay(): Boolean =
    this !is DateTime