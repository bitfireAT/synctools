/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.database.DatabaseUtils
import android.provider.CalendarContract
import at.bitfire.ical4android.impl.TestTaskList
import net.fortuna.ical4j.model.property.RelatedTo
import org.dmfs.tasks.contract.TaskContract
import org.dmfs.tasks.contract.TaskContract.Properties
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DmfsTaskListTest(providerName: TaskProvider.ProviderName):
    DmfsStyleProvidersTaskTest(providerName) {

    private val testAccount = Account(javaClass.name, CalendarContract.ACCOUNT_TYPE_LOCAL)


    private fun createTaskList(): TestTaskList {
        val info = ContentValues()
        info.put(TaskContract.TaskLists.LIST_NAME, "Test Task List")
        info.put(TaskContract.TaskLists.LIST_COLOR, 0xffff0000)
        info.put(TaskContract.TaskLists.OWNER, "test@example.com")
        info.put(TaskContract.TaskLists.SYNC_ENABLED, 1)
        info.put(TaskContract.TaskLists.VISIBLE, 1)

        val uri = DmfsTaskList.create(testAccount, provider.client, providerName, info)
        assertNotNull(uri)

        return DmfsTaskList.findByID(testAccount, provider.client, providerName, TestTaskList.Factory, ContentUris.parseId(uri))
    }


    @Test
    fun testManageTaskLists() {
        val taskList = createTaskList()

        try {
            // sync URIs
            assertEquals("true", taskList.taskListSyncUri().getQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER))
            assertEquals(testAccount.type, taskList.taskListSyncUri().getQueryParameter(TaskContract.ACCOUNT_TYPE))
            assertEquals(testAccount.name, taskList.taskListSyncUri().getQueryParameter(TaskContract.ACCOUNT_NAME))

            assertEquals("true", taskList.tasksSyncUri().getQueryParameter(TaskContract.CALLER_IS_SYNCADAPTER))
            assertEquals(testAccount.type, taskList.tasksSyncUri().getQueryParameter(TaskContract.ACCOUNT_TYPE))
            assertEquals(testAccount.name, taskList.tasksSyncUri().getQueryParameter(TaskContract.ACCOUNT_NAME))
        } finally {
            // delete task list
            assertTrue(taskList.delete())
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
            val childContentUri = DmfsTask(taskList, child, "452a5672-e2b0-434e-92b4-bc70a7a51ef2", null, 0).add()
            val childId = ContentUris.parseId(childContentUri)
            val parentContentUri = DmfsTask(taskList, parent, "452a5672-e2b0-434e-92b4-bc70a7a51ef2", null, 0).add()
            val parentId = ContentUris.parseId(parentContentUri)

            // OpenTasks should provide the correct relation
            taskList.provider.query(taskList.tasksPropertiesSyncUri(), null,
                    "${Properties.TASK_ID}=?", arrayOf(childId.toString()),
                    null, null)!!.use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToNext()

                val row = ContentValues()
                DatabaseUtils.cursorRowToContentValues(cursor, row)

                assertEquals(Relation.CONTENT_ITEM_TYPE, row.getAsString(Properties.MIMETYPE))
                assertEquals(parentId, row.getAsLong(Relation.RELATED_ID))
                assertEquals(parent.uid, row.getAsString(Relation.RELATED_UID))
                assertEquals(Relation.RELTYPE_PARENT, row.getAsInteger(Relation.RELATED_TYPE))
            }

            // touch the relations to update parent_id values
            taskList.touchRelations()

            // now parent_id should bet set
            taskList.provider.query(childContentUri, arrayOf(Tasks.PARENT_ID),
                    null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToNext())
                assertEquals(parentId, cursor.getLong(0))
            }
        } finally {
            taskList.delete()
        }
    }

}
