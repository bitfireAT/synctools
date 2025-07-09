/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import org.junit.Assert
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class AndroidTimeZonesTest {

    @Test
    fun testLoadSystemTimezones() {
        val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
        for (id in ZoneId.getAvailableZoneIds()) {
            val name = ZoneId.of(id).getDisplayName(TextStyle.FULL, Locale.US)
            val info = try {
                tzRegistry.getTimeZone(id)
            } catch(e: Exception) {
                Assert.fail("Invalid system timezone $name ($id)")
            }
            if (info == null)
                assertNotNull("ical4j can't load system timezone $name ($id)", info)
        }
    }

}