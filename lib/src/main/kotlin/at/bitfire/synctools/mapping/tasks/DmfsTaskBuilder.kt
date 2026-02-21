/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks

import at.bitfire.ical4android.DmfsTask.Companion.COLUMN_ETAG
import at.bitfire.ical4android.DmfsTask.Companion.COLUMN_FLAGS
import at.bitfire.ical4android.DmfsTask.Companion.UNKNOWN_PROPERTY_DATA
import at.bitfire.ical4android.ICalendar
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.storage.tasks.TasksBatchOperation
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.util.TimeZones
import org.dmfs.tasks.contract.TaskContract.Properties
import org.dmfs.tasks.contract.TaskContract.Property.Alarm
import org.dmfs.tasks.contract.TaskContract.Property.Category
import org.dmfs.tasks.contract.TaskContract.Property.Comment
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.time.ZoneId
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Writes [at.bitfire.ical4android.Task] to dmfs task provider data rows
 * (former DmfsTask "build..." methods).
 */
class DmfsTaskBuilder(
    private val taskList: DmfsTaskList,
    private val task: Task,

    // DmfsTask-level fields
    private val id: Long?,
    private val syncId: String?,
    private val eTag: String?,
    private val flags: Int,
) {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }

    fun addRows(batch: TasksBatchOperation): Int {
        val builder = CpoBuilder.newInsert(taskList.tasksUri())
        buildTask(builder, false)
        val idxTask = batch.nextBackrefIdx() // Get nextBackrefIdx BEFORE adding builder to batch
        batch += builder
        return idxTask
    }

    fun updateRows(batch: TasksBatchOperation) {
        val id = requireNotNull(id)
        val builder = CpoBuilder.newUpdate(taskList.taskUri(id))
        buildTask(builder, true)
        batch += builder
    }

    private fun buildTask(builder: CpoBuilder, update: Boolean) {
        TODO()
    }

    fun getTimeZone(): TimeZone = TODO()

    fun insertProperties(batch: TasksBatchOperation, idxTask: Int?) {
        insertAlarms(batch, idxTask)
        insertCategories(batch, idxTask)
        insertComment(batch, idxTask)
        insertRelatedTo(batch, idxTask)
        insertUnknownProperties(batch, idxTask)
    }

    private fun insertAlarms(batch: TasksBatchOperation, idxTask: Int?) {
        TODO()
    }

    private fun insertCategories(batch: TasksBatchOperation, idxTask: Int?) {
        for (category in task.categories) {
            val builder = CpoBuilder.newInsert(taskList.tasksPropertiesUri())
                .withTaskId(Category.TASK_ID, idxTask)
                .withValue(Category.MIMETYPE, Category.CONTENT_ITEM_TYPE)
                .withValue(Category.CATEGORY_NAME, category)
            logger.log(Level.FINE, "Inserting category", builder.build())
            batch += builder
        }
    }

    private fun insertComment(batch: TasksBatchOperation, idxTask: Int?) {
        val comment = task.comment ?: return
        val builder = CpoBuilder.newInsert(taskList.tasksPropertiesUri())
            .withTaskId(Comment.TASK_ID, idxTask)
            .withValue(Comment.MIMETYPE, Comment.CONTENT_ITEM_TYPE)
            .withValue(Comment.COMMENT, comment)
        logger.log(Level.FINE, "Inserting comment", builder.build())
        batch += builder
    }

    private fun insertRelatedTo(batch: TasksBatchOperation, idxTask: Int?) {
        TODO("ical4j 4.x")
    }

    private fun insertUnknownProperties(batch: TasksBatchOperation, idxTask: Int?) {
        for (property in task.unknownProperties) {
            if (property.value.length > UnknownProperty.MAX_UNKNOWN_PROPERTY_SIZE) {
                logger.warning("Ignoring unknown property with ${property.value.length} octets (too long)")
                return
            }

            val builder = CpoBuilder.newInsert(taskList.tasksPropertiesUri())
                .withTaskId(Properties.TASK_ID, idxTask)
                .withValue(Properties.MIMETYPE, UnknownProperty.CONTENT_ITEM_TYPE)
                .withValue(UNKNOWN_PROPERTY_DATA, UnknownProperty.toJsonString(property))
            logger.log(Level.FINE, "Inserting unknown property", builder.build())
            batch += builder
        }
    }

    private fun CpoBuilder.withTaskId(column: String, idxTask: Int?): CpoBuilder {
        if (idxTask != null)
            withValueBackReference(column, idxTask)
        else
            withValue(column, requireNotNull(id))
        return this
    }

}