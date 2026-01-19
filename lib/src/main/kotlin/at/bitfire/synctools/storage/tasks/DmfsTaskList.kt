/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.tasks

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import at.bitfire.ical4android.DmfsTask
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.toContentValues
import org.dmfs.tasks.contract.TaskContract
import java.util.LinkedList
import java.util.logging.Logger

/**
 * Represents a locally stored task list, containing [at.bitfire.ical4android.DmfsTask]s (tasks).
 * Communicates with tasks.org-compatible content providers (currently tasks.org and OpenTasks) to store the tasks.
 */
class DmfsTaskList(
    val provider: DmfsTaskListProvider,
    val values: ContentValues,
    val providerName: TaskProvider.ProviderName,
) {

    private val logger
        get() = Logger.getLogger(DmfsTaskList::class.java.name)

    /** see [TaskContract.TaskLists._ID] **/
    val id: Long = values.getAsLong(TaskContract.TaskLists._ID)
        ?: throw IllegalArgumentException("${TaskContract.TaskLists._ID} must be set")

    /** see [TaskContract.TaskListColumns.ACCESS_LEVEL] **/
    val accessLevel: Int
        get() = values.getAsInteger(TaskContract.TaskListColumns.ACCESS_LEVEL) ?: 0

    /** see [TaskContract.TaskLists.LIST_NAME] **/
    val name: String?
        get() = values.getAsString(TaskContract.TaskLists.LIST_NAME)

    /** see [TaskContract.TaskLists._SYNC_ID] **/
    val syncId: String?
        get() = values.getAsString(TaskContract.TaskLists._SYNC_ID)


    // CRUD DmfsTask

    /**
     * Queries tasks from this task list. Adds a WHERE clause that restricts the
     * query to [TaskContract.TaskColumns.LIST_ID] = [id].
     *
     * @param where selection
     * @param whereArgs arguments for selection
     *
     * @return events from this task list which match the selection
     */
    fun findTasks(where: String? = null, whereArgs: Array<String>? = null): List<DmfsTask> {
        val tasks = LinkedList<DmfsTask>()
        try {
            val (protectedWhere, protectedWhereArgs) = whereWithTaskListId(where, whereArgs)
            client.query(tasksUri(), null, protectedWhere, protectedWhereArgs, null)?.use { cursor ->
                while (cursor.moveToNext())
                    tasks += DmfsTask(this, cursor.toContentValues())
            }
        } catch (e: Exception) {
            throw LocalStorageException("Couldn't query ${providerName.authority} tasks", e)
        }
        return tasks
    }

    fun getTask(id: Long) = findTasks("${TaskContract.Tasks._ID}=?", arrayOf(id.toString())).firstOrNull()
        ?: throw LocalStorageException("Couldn't query ${providerName.authority} tasks")
    
    /**
     * Updates tasks in this task list.
     *
     * @param values        values to update
     * @param where         selection
     * @param whereArgs     arguments for selection
     *
     * @return number of updated rows
     * @throws LocalStorageException when the content provider returns an error
     */
    fun updateTasks(values: ContentValues, where: String?, whereArgs: Array<String>?): Int =
        try {
            client.update(tasksUri(), values, where, whereArgs)
        } catch (e: Exception) {
            throw LocalStorageException("Couldn't update ${providerName.authority} tasks", e)
        }


    // shortcuts to upper level

    /** Calls [DmfsTaskListProvider.delete] for this task list **/
    fun delete(): Boolean = provider.delete(id)

    /**
     * Calls [DmfsTaskListProvider.updateTaskList] for this task list.
     *
     * **Attention**: Does not update this object with the new values!
     */
    fun update(values: ContentValues): Int = provider.updateTaskList(id,values)

    /** Calls [DmfsTaskListProvider.readTaskListSyncState] for this task list. */
    fun readSyncState(): String? = provider.readTaskListSyncState(id)

    /** Calls [DmfsTaskListProvider.writeTaskListSyncState] for this task list. */
    fun writeSyncState(state: String?) = provider.writeTaskListSyncState(id, state)


    // helpers

    val account
        get() = provider.account

    val client
        get() = provider.client

    fun tasksUri(loadProperties: Boolean = false): Uri {
        val uri = TaskContract.Tasks.getContentUri(providerName.authority).asSyncAdapter(account)
        return if (loadProperties)
            uri.buildUpon()
                .appendQueryParameter(TaskContract.LOAD_PROPERTIES, "1")
                .build()
        else
            uri
    }

    fun tasksPropertiesUri() =
        TaskContract.Properties.getContentUri(providerName.authority).asSyncAdapter(account)

    /**
     * Restricts a given selection/where clause to this task list ID.
     *
     * @param where      selection
     * @param whereArgs  arguments for selection
     * @return           restricted selection and arguments
     */
    private fun whereWithTaskListId(where: String?, whereArgs: Array<String>?): Pair<String, Array<String>> {
        val protectedWhere = "(${where ?: "1"}) AND ${TaskContract.Tasks.LIST_ID}=?"
        val protectedWhereArgs = (whereArgs ?: arrayOf()) + id.toString()
        return Pair(protectedWhere, protectedWhereArgs)
    }

    /**
     * When tasks are added or updated, they may refer to related tasks by UID ([TaskContract.Property.Relation.RELATED_UID]).
     * However, those related tasks may not be available (for instance, because they have not been
     * synchronized yet), so that the tasks provider can't establish the actual relation (= set
     * [TaskContract.PropertyColumns.TASK_ID]) in the database.
     *
     * As soon as such a related task is added, OpenTasks updates the [TaskContract.Property.Relation.RELATED_ID],
     * but it does *not* update [TaskContract.TaskColumns.PARENT_ID] of the parent task:
     * https://github.com/dmfs/opentasks/issues/877
     *
     * This method shall be called after all tasks have been synchronized. It touches
     *
     *   - all [TaskContract.Property.Relation] rows
     *   - with [TaskContract.Property.Relation.RELATED_ID] (→ related task is already synchronized)
     *   - of tasks without [TaskContract.TaskColumns.PARENT_ID] (→ only touch relevant rows)
     *
     * so that missing [TaskContract.TaskColumns.PARENT_ID] fields are updated.
     *
     * @return number of touched [TaskContract.Property.Relation] rows
     */
    fun touchRelations(): Int {
        logger.fine("Touching relations to set parent_id")
        val batch = TasksBatchOperation(client)
        client.query(
            tasksUri(true), null,
            "${TaskContract.Tasks.LIST_ID}=? AND ${TaskContract.Tasks.PARENT_ID} IS NULL AND ${TaskContract.Property.Relation.MIMETYPE}=? AND ${TaskContract.Property.Relation.RELATED_ID} IS NOT NULL",
            arrayOf(id.toString(), TaskContract.Property.Relation.CONTENT_ITEM_TYPE),
            null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val values = cursor.toContentValues()
                val id = values.getAsLong(TaskContract.Property.Relation.PROPERTY_ID)
                val propertyContentUri = ContentUris.withAppendedId(tasksPropertiesUri(), id)
                batch += BatchOperation.CpoBuilder
                    .newUpdate(propertyContentUri)
                    .withValue(
                        TaskContract.Property.Relation.RELATED_ID,
                        values.getAsLong(TaskContract.Property.Relation.RELATED_ID)
                    )
            }
        }
        return batch.commit()
    }

}