/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.ical4android.util.TimeApiExtensions.toZoneIdCompat
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class Ical4jDateHelpersTest {

    private val tzBerlin = TimeZoneRegistryFactory.getInstance().createRegistry().getTimeZone("Europe/Berlin")

    @Test
    fun testDate_toLocalDate() {
        val date = Date("20200620").asLocalDate()
        assertEquals(2020, date.year)
        assertEquals(6, date.monthValue)
        assertEquals(20, date.dayOfMonth)
        assertEquals(DayOfWeek.SATURDAY, date.dayOfWeek)
    }

    @Test
    fun testDateTime_asZonedDateTime() {
        assertEquals(
            ZonedDateTime.of(2020, 7, 7, 10, 30, 0, 0, tzBerlin.toZoneIdCompat()),
            DateTime("20200707T103000", tzBerlin).asZonedDateTime()
        )
    }

    @Test
    fun testDateTime_asZonedDateTime_Floating() {
        assertEquals(
            ZonedDateTime.of(2020, 7, 7, 10, 30, 0, 0, ZoneId.systemDefault()),
            DateTime("20200707T103000").asZonedDateTime()
        )
    }

    @Test
    fun testDateTime_asZonedDateTime_UTC() {
        assertEquals(
            ZonedDateTime.of(2020, 7, 7, 10, 30, 0, 0, ZoneOffset.UTC),
            DateTime("20200707T103000Z").apply { isUtc = true }.asZonedDateTime()
        )
    }

}