/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.synctools.BuildConfig
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Uid
import kotlin.jvm.optionals.getOrNull

/**
 * The used version of ical4j.
 */
@Suppress("unused")
const val ical4jVersion = BuildConfig.version_ical4j


// component access helpers

fun<T: CalendarComponent> componentListOf(vararg components: T): ComponentList<T> =
    ComponentList<T>().addAll(components.toList())

fun propertyListOf(vararg properties: Property): PropertyList =
    PropertyList().addAll(properties.toList())

val CalendarComponent.uid: Uid?
    get() = getProperty<Uid>(Property.UID).getOrNull()

val CalendarComponent.recurrenceId: RecurrenceId<*>?
    get() = getProperty<RecurrenceId<*>>(Property.RECURRENCE_ID).getOrNull()

val CalendarComponent.sequence: Sequence?
    get() = getProperty<Sequence>(Property.SEQUENCE).getOrNull()

fun VEvent.requireDtStart(): DtStart<*> =
    TODO("ical4j 4.x")
    // startDate ?: throw InvalidICalendarException("Missing DTSTART in VEVENT")
