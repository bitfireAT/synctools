/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class DesugaringTest {

    /**
     * There was a time where Duration.ofSeconds(0) caused problems, possibly with desugaring.
     * So this test is especially interesting for Android 7.
     */
    @Test
    fun test_Duration_ofSeconds() {
        val dur = Duration.ofSeconds(0)
        assertEquals(0, dur.seconds)
    }
    
}