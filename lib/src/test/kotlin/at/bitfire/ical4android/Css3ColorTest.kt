/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.synctools.icalendar.Css3Color
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Css3ColorTest {

    @Test
    fun testColorFromString() {
        // color name
        Assert.assertEquals(0xffffff00.toInt(), Css3Color.Companion.colorFromString("yellow"))

        // RGB value
        Assert.assertEquals(0xffffff00.toInt(), Css3Color.Companion.colorFromString("#ffff00"))

        // ARGB value
        Assert.assertEquals(0xffffff00.toInt(), Css3Color.Companion.colorFromString("#ffffff00"))

        // empty value
        Assert.assertNull(Css3Color.Companion.colorFromString(""))

        // invalid value
        Assert.assertNull(Css3Color.Companion.colorFromString("DoesNotExist"))
    }

    @Test
    fun testFromString() {
        // lower case
        Assert.assertEquals(0xffffff00.toInt(), Css3Color.Companion.fromString("yellow")?.argb)

        // capitalized
        Assert.assertEquals(0xffffff00.toInt(), Css3Color.Companion.fromString("Yellow")?.argb)

        // not-existing color
        Assert.assertNull(Css3Color.Companion.fromString("DoesNotExist"))
    }

    @Test
    fun testNearestMatch() {
        // every color is its own nearest match
        Css3Color.entries.forEach {
            Assert.assertEquals(it.argb, Css3Color.Companion.nearestMatch(it.argb).argb)
        }
    }

}