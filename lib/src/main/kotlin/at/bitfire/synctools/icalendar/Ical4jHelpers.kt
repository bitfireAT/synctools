/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.synctools.BuildConfig
import at.bitfire.synctools.exception.InvalidICalendarException
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Uid

/**
 * The used version of ical4j.
 */
@Suppress("unused")
const val ical4jVersion = BuildConfig.version_ical4j


// component access helpers

fun<T: CalendarComponent> componentListOf(vararg components: T): ComponentList<CalendarComponent> {
    TODO("ical4j 4.x")
}

fun propertyListOf(vararg properties: Property): net.fortuna.ical4j.model.PropertyList {
    TODO("ical4j 4.x")
}

val CalendarComponent.uid: Uid?
    get() = TODO("ical4j 4.x")

val CalendarComponent.recurrenceId: RecurrenceId<*>?
    get() = TODO("ical4j 4.x")

val CalendarComponent.sequence: Sequence?
    get() = TODO("ical4j 4.x")

fun VEvent.requireDtStart(): DtStart<*> {
    TODO("ical4j 4.x")
}
