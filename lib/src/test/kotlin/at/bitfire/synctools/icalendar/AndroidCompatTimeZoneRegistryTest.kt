/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import net.fortuna.ical4j.model.DefaultTimeZoneRegistryFactory
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistry
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.zone.ZoneRulesException

class AndroidCompatTimeZoneRegistryTest {

    lateinit var ical4jRegistry: TimeZoneRegistry
    lateinit var registry: AndroidCompatTimeZoneRegistry

    private val systemKnowsKyiv =
        try {
            ZoneId.of("Europe/Kyiv")
            true
        } catch (e: ZoneRulesException) {
            false
        }

    @Before
    fun createRegistry() {
        ical4jRegistry = DefaultTimeZoneRegistryFactory().createRegistry()
        registry = AndroidCompatTimeZoneRegistry.Factory().createRegistry()
    }


    @Test
    fun getTimeZone_Existing() {
        Assert.assertEquals(
            ical4jRegistry.getTimeZone("Europe/Vienna"),
            registry.getTimeZone("Europe/Vienna")
        )
    }

    @Test
    fun getTimeZone_Existing_ButNotInIcal4j() {
        val reg = AndroidCompatTimeZoneRegistry(object : TimeZoneRegistry {
            override fun register(timezone: TimeZone?) = throw NotImplementedError()
            override fun register(timezone: TimeZone?, update: Boolean) = throw NotImplementedError()
            override fun clear() = throw NotImplementedError()
            override fun getTimeZone(id: String?) = null

        })
        Assert.assertNull(reg.getTimeZone("Europe/Berlin"))
    }

    @Test
    fun getTimeZone_Existing_Kiev() {
        Assume.assumeFalse(systemKnowsKyiv)
        val tz = registry.getTimeZone("Europe/Kiev")
        Assert.assertFalse(tz === ical4jRegistry.getTimeZone("Europe/Kiev"))      // we have made a copy
        Assert.assertEquals("Europe/Kiev", tz?.id)
        Assert.assertEquals("Europe/Kiev", tz?.vTimeZone?.timeZoneId?.value)
    }

    @Test
    fun getTimeZone_Existing_Kyiv() {
        Assume.assumeFalse(systemKnowsKyiv)

        /* Unfortunately, AndroidCompatTimeZoneRegistry can't rewrite to Europy/Kyiv to anything because
           it doesn't know a valid Android name for it. */
        Assert.assertEquals(
            ical4jRegistry.getTimeZone("Europe/Kyiv"),
            registry.getTimeZone("Europe/Kyiv")
        )
    }

    @Test
    fun getTimeZone_Copenhagen_NoBerlin() {
        val tz = registry.getTimeZone("Europe/Copenhagen")!!
        Assert.assertEquals("Europe/Copenhagen", tz.id)
        Assert.assertFalse(tz.vTimeZone.toString().contains("Berlin"))
    }

    @Test
    fun getTimeZone_NotExisting() {
        Assert.assertNull(registry.getTimeZone("Test/NotExisting"))
    }

}