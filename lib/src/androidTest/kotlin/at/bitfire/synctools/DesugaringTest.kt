/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/**
 * These tests are especially interesting for Android 7, where the Time API is not part of the
 * system and provided over "desugaring".
 */
class DesugaringTest {

    @Test
    fun test_Duration_ofSeconds() {
        val dur = Duration.ofSeconds(0)
        assertEquals(0, dur.seconds)
    }

}