/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

import org.junit.Test

class Ical4jConfigurationTest {

    @Test
    fun testTimeZoneRegistryFactoryConfigured() {
        val registry = TimeZoneRegistryFactory.getInstance().createRegistry()
        assertTrue(registry is AndroidCompatTimeZoneRegistry)
    }

}