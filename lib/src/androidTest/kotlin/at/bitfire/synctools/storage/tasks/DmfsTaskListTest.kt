/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.tasks

import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.database.DatabaseUtils
import android.provider.CalendarContract
import at.bitfire.ical4android.DmfsStyleProvidersTaskTest
import at.bitfire.ical4android.DmfsTask
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.TaskProvider
import net.fortuna.ical4j.model.property.RelatedTo
import org.dmfs.tasks.contract.TaskContract
import org.junit.Assert
import org.junit.Test

class DmfsTaskListTest(providerName: TaskProvider.ProviderName):
    DmfsStyleProvidersTaskTest(providerName) {

    private val testAccount = Account(javaClass.name, CalendarContract.ACCOUNT_TYPE_LOCAL)

    private fun createTaskList(): DmfsTaskList {
        val info = ContentValues()
        info.put(TaskContract.TaskLists.LIST_NAME, "Test Task List")
        info.put(TaskContract.TaskLists.LIST_COLOR, 0xffff0000)
        info.put(TaskContract.TaskLists.OWNER, "test@example.com")
        info.put(TaskContract.TaskLists.SYNC_ENABLED, 1)
        info.put(TaskContract.TaskLists.VISIBLE, 1)

        val dmfsTaskListProvider = DmfsTaskListProvider(testAccount, provider.client, providerName)
        val uri = dmfsTaskListProvider.createTaskList(testAccount, provider.client, providerName, info)
        Assert.assertNotNull(uri)

        dmfsTaskListProvider.createTaskList()

        return DmfsTaskList.findByID(testAccount, provider.client, providerName, ContentUris.parseId(uri))
    }

    @Test
    fun testManageTaskLists() {
        val taskList = createTaskList()

        try {
            // sync URIs
            Assert.assertEquals(
                "true",
                taskList.taskListSyncUri().getQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER)
            )
            Assert.assertEquals(
                testAccount.type,
                taskList.taskListSyncUri().getQueryParameter(TaskContract.ACCOUNT_TYPE)
            )
            Assert.assertEquals(
                testAccount.name,
                taskList.taskListSyncUri().getQueryParameter(TaskContract.ACCOUNT_NAME)
            )

            Assert.assertEquals(
                "true",
                taskList.tasksSyncUri().getQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER)
            )
            Assert.assertEquals(
                testAccount.type,
                taskList.tasksSyncUri().getQueryParameter(TaskContract.ACCOUNT_TYPE)
            )
            Assert.assertEquals(
                testAccount.name,
                taskList.tasksSyncUri().getQueryParameter(TaskContract.ACCOUNT_NAME)
            )
        } finally {
            // delete task list
            Assert.assertTrue(taskList.delete())
        }
    }

    @Test
    fun testTouchRelations() {
        val taskList = createTaskList()
        try {
            val parent = Task()
            parent.uid = "parent"
            parent.summary = "Parent task"

            val child = Task()
            child.uid = "child"
            child.summary = "Child task"
            child.relatedTo.add(RelatedTo(parent.uid))

            // insert child before parent
            val childContentUri = DmfsTask(
                taskList,
                child,
                "452a5672-e2b0-434e-92b4-bc70a7a51ef2",
                null,
                0
            ).add()
            val childId = ContentUris.parseId(childContentUri)
            val parentContentUri = DmfsTask(
                taskList,
                parent,
                "452a5672-e2b0-434e-92b4-bc70a7a51ef2",
                null,
                0
            ).add()
            val parentId = ContentUris.parseId(parentContentUri)

            // OpenTasks should provide the correct relation
            taskList.provider.client.query(taskList.tasksPropertiesSyncUri(), null,
                    "${TaskContract.Properties.TASK_ID}=?", arrayOf(childId.toString()),
                    null, null)!!.use { cursor ->
                Assert.assertEquals(1, cursor.count)
                cursor.moveToNext()

                val row = ContentValues()
                DatabaseUtils.cursorRowToContentValues(cursor, row)

                Assert.assertEquals(
                    TaskContract.Property.Relation.CONTENT_ITEM_TYPE,
                    row.getAsString(TaskContract.Properties.MIMETYPE)
                )
                Assert.assertEquals(
                    parentId,
                    row.getAsLong(TaskContract.Property.Relation.RELATED_ID)
                )
                Assert.assertEquals(
                    parent.uid,
                    row.getAsString(TaskContract.Property.Relation.RELATED_UID)
                )
                Assert.assertEquals(
                    TaskContract.Property.Relation.RELTYPE_PARENT,
                    row.getAsInteger(TaskContract.Property.Relation.RELATED_TYPE)
                )
            }

            // touch the relations to update parent_id values
            taskList.touchRelations()

            // now parent_id should bet set
            taskList.provider.client.query(childContentUri, arrayOf(TaskContract.Tasks.PARENT_ID),
                    null, null, null)!!.use { cursor ->
                Assert.assertTrue(cursor.moveToNext())
                Assert.assertEquals(parentId, cursor.getLong(0))
            }
        } finally {
            taskList.delete()
        }
    }

}