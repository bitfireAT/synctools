/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Uid


fun<T: CalendarComponent> componentListOf(vararg components: T) =
    ComponentList<CalendarComponent>().apply {
        addAll(components)
    }

fun propertyListOf(vararg properties: Property) =
    PropertyList<Property>().apply {
        addAll(properties)
    }


val CalendarComponent.uid: Uid?
    get() = getProperty(Property.UID)

val CalendarComponent.recurrenceId: RecurrenceId?
    get() = getProperty(Property.RECURRENCE_ID)

val CalendarComponent.sequence: Sequence?
    get() = getProperty(Property.SEQUENCE)
