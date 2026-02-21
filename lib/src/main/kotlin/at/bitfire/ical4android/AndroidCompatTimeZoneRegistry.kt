/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import java.time.ZoneId
import java.util.logging.Logger
import net.fortuna.ical4j.model.DefaultTimeZoneRegistryFactory
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.TimeZoneRegistryImpl
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.TzId

/**
 * Wrapper around default [TimeZoneRegistry] that uses the Android name if a time zone has a
 * different name in ical4j and Android.
 *
 * **This time zone registry is set as default registry for ical4android projects in
 * resources/ical4j.properties.**
 *
 * For instance, if a time zone is known as "Europe/Kyiv" (with alias "Europe/Kiev") in ical4j
 * and only "Europe/Kiev" in Android, this registry behaves like the default [TimeZoneRegistryImpl],
 * but the returned time zone for `getTimeZone("Europe/Kiev")` has an ID of "Europe/Kiev" and not
 * "Europe/Kyiv".
 */
class AndroidCompatTimeZoneRegistry(
    private val base: TimeZoneRegistry
): TimeZoneRegistry by base {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Gets the time zone for a given ID.
     *
     * If a time zone with the given ID exists in Android, the icalj timezone for this ID
     * is returned, but the TZID is set to the Android name (and not the ical4j name, which
     * may not be known to Android).
     *
     * If a time zone with the given ID doesn't exist in Android, this method returns the
     * result of its [base] method.
     *
     * @param id
     * @return time zone
     */
    override fun getTimeZone(id: String): TimeZone? {
        TODO("ical4j 4.x")
    }


    class Factory : TimeZoneRegistryFactory() {

        override fun createRegistry(): AndroidCompatTimeZoneRegistry {
            val ical4jRegistry = DefaultTimeZoneRegistryFactory().createRegistry()
            return AndroidCompatTimeZoneRegistry(ical4jRegistry)
        }

    }

}