/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.test

import android.content.Entity
import org.junit.ComparisonFailure

fun assertEntityEquals(expected: Entity, actual: Entity, message: String? = null) {
    val entityValuesEqual = actual.entityValues == expected.entityValues
    val subValuesEqual = expected.subValues.toSet() == actual.subValues.toSet()

    if (!entityValuesEqual || !subValuesEqual)
        throw ComparisonFailure(message, expected.toString(), actual.toString())
}