/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.vcard4android

import at.bitfire.vcard4android.Utils.capitalize
import at.bitfire.vcard4android.Utils.trimToNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UtilsTest {
    @Test
    fun testCapitalize() {
        assertEquals("Utils Test", "utils test".capitalize()) // Test multiple words
        assertEquals("Utils", "utils".capitalize()) // Test single word
        assertEquals("", "".capitalize()) // Test empty string
    }

    @Test
    fun testTrimToNull() {
        assertEquals("test", "  test".trimToNull()) // Test spaces only before
        assertEquals("test", "test  ".trimToNull()) // Test spaces only after
        assertEquals("test", "  test  ".trimToNull()) // Test spaces before and after
        assertNull("     ".trimToNull()) // Test spaces
        assertNull("".trimToNull()) // Test empty string
    }
}
