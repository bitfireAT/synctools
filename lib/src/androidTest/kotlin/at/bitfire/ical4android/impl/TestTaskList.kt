/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.impl

import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import at.bitfire.ical4android.DmfsTaskList
import at.bitfire.ical4android.TaskProvider
import org.dmfs.tasks.contract.TaskContract

object TestTaskList {

    fun create(
        account: Account,
        provider: TaskProvider,
    ): DmfsTaskList {
        val values = ContentValues(4)
        values.put(TaskContract.TaskListColumns.LIST_NAME, "Test Task List")
        values.put(TaskContract.TaskListColumns.LIST_COLOR, 0xffff0000)
        values.put(TaskContract.TaskListColumns.SYNC_ENABLED, 1)
        values.put(TaskContract.TaskListColumns.VISIBLE, 1)
        val uri = DmfsTaskList.create(account, provider.client, provider.name, values)

        return DmfsTaskList(account, provider.client, provider.name, ContentUris.parseId(uri))
    }

}
