/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.synctools.BuildConfig
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Uid

/**
 * The used version of ical4j.
 */
@Suppress("unused")
const val ical4jVersion = BuildConfig.version_ical4j


// component access helpers

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


// date-time helpers

fun Date.isAllDay(): Boolean =
    this !is DateTime


// recurrence helpers

fun CalendarComponent.isRecurring(): Boolean =
    properties.any { it is RRule || it is RDate }