/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools

import androidx.test.rule.GrantPermissionRule
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Requests the given permissions for testing. If the permissions are not available/granted,
 * the tests are skipped.
 *
 * @param permissions   requested permissions
 */
class GrantPermissionOrSkipRule(permissions: Set<String>): TestRule {

    val grantRule: TestRule = GrantPermissionRule.grant(*permissions.toTypedArray())

    override fun apply(base: Statement, description: Description) =
        object: Statement() {
            override fun evaluate() {
                val innerStatement = grantRule.apply(base, description)
                try {
                    innerStatement.evaluate()
                } catch (e: SecurityException) {
                    Assume.assumeNoException(e)
                }
            }
        }
}