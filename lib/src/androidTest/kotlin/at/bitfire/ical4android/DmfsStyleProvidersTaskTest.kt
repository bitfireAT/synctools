/*
 * This file is part of ical4android which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.model.Statement
import java.util.logging.Logger

@RunWith(Parameterized::class)

abstract class DmfsStyleProvidersTaskTest(
    val providerName: TaskProvider.ProviderName
) {

    companion object {
        @Parameterized.Parameters(name="{0}")
        @JvmStatic
        fun taskProviders() = listOf(TaskProvider.ProviderName.OpenTasks,TaskProvider.ProviderName.TasksOrg)
    }

    @get:Rule
    val permissionRule: TestRule = object : TestRule {
        val rule = GrantPermissionRule.grant(*providerName.permissions)

        override fun apply(base: Statement, description: Description) =
            object: Statement() {
                override fun evaluate() {
                    val innerStatement = rule.apply(base, description)
                    try {
                        innerStatement.evaluate()
                    } catch (e: SecurityException) {
                        Assume.assumeNoException(e)
                    }
                }
            }
    }

    var providerOrNull: TaskProvider? = null
    lateinit var provider: TaskProvider

    @Before
    open fun prepare() {
        providerOrNull = TaskProvider.acquire(InstrumentationRegistry.getInstrumentation().context, providerName)
        assertNotNull("$providerName is not installed", providerOrNull != null)

        provider = providerOrNull!!
        Logger.getLogger(javaClass.name).fine("Using task provider: $provider")
    }

    @After
    open fun shutdown() {
        providerOrNull?.close()
    }

}