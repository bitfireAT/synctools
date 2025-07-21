/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.test

import android.content.ContentValues
import org.junit.Assert.assertEquals

fun assertContentValuesEqual(expected: ContentValues, actual: ContentValues, message: String? = null) {
    // assert keys are equal
    val expectedKeys = expected.keySet()
    val actualKeys = actual.keySet()
    assertEquals(message, expectedKeys, actualKeys)

    // keys are equal → assert key values (in String representation)
    for (key in expectedKeys)
        assertEquals(message, expected.getAsString(key),  actual.getAsString(key))
}