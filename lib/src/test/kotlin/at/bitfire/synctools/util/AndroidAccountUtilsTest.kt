/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidAccountUtilsTest {

    val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun testCreateAccount() {
        val userData = mapOf(
            "int" to "1",
            "string" to "abc/\"-"
        )

        val account = Account(javaClass.name, "test")
        val manager = AccountManager.get(context)
        try {
            Assert.assertTrue(AndroidAccountUtils.createAccount(context, account, userData))

            // validate user data
            Assert.assertEquals("1", manager.getUserData(account, "int"))
            Assert.assertEquals("abc/\"-", manager.getUserData(account, "string"))
        } finally {
            Assert.assertTrue(manager.removeAccountExplicitly(account))
        }
    }

}