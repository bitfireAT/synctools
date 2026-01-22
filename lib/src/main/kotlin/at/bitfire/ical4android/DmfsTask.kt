/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.RemoteException
import at.bitfire.synctools.mapping.tasks.DmfsTaskBuilder
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.storage.tasks.TasksBatchOperation
import at.bitfire.synctools.storage.toContentValues
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.Completed
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.Geo
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RelatedTo
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.Trigger
import org.dmfs.tasks.contract.TaskContract.Properties
import org.dmfs.tasks.contract.TaskContract.Property.Alarm
import org.dmfs.tasks.contract.TaskContract.Property.Category
import org.dmfs.tasks.contract.TaskContract.Property.Comment
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.io.FileNotFoundException
import java.net.URISyntaxException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Stores and retrieves VTODO iCalendar objects (represented as [Task]s) to/from the
 * tasks.org-content provider (currently tasks.org and OpenTasks).
 *
 * Extend this class to process specific fields of the task.
 *
 * The SEQUENCE field is stored in [Tasks.SYNC_VERSION], so don't use [Tasks.SYNC_VERSION]
 * for anything else.
 */
class DmfsTask(
    val taskList: DmfsTaskList
) {

    private val logger = Logger.getLogger(javaClass.name)
    private val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }

    var id: Long? = null
    var syncId: String? = null
    var eTag: String? = null
    var flags: Int = 0


    constructor(taskList: DmfsTaskList, values: ContentValues): this(taskList) {
        id = values.getAsLong(Tasks._ID)
        syncId = values.getAsString(Tasks._SYNC_ID)
        eTag = values.getAsString(COLUMN_ETAG)
        flags = values.getAsInteger(COLUMN_FLAGS) ?: 0
    }

    constructor(taskList: DmfsTaskList, task: Task, syncId: String?, eTag: String?, flags: Int): this(taskList) {
        this.task = task
        this.syncId = syncId
        this.eTag = eTag
        this.flags = flags
    }


    var task: Task? = null
        /**
         * This getter returns the full task data, either from [task] or, if [task] is null, by reading task
         * number [id] from the task provider
         * @throws IllegalArgumentException if task has not been saved yet
         * @throws FileNotFoundException if there's no task with [id] in the task provider
         * @throws RemoteException on task provider errors
         */
        get() {
            if (field != null)
                return field
            val id = requireNotNull(id)

            try {
                val client = taskList.provider.client
                client.query(taskSyncURI(true), null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        // create new Task which will be populated
                        val newTask = Task()
                        field = newTask

                        val values = cursor.toContentValues()
                        logger.log(Level.FINER, "Found task", values)
                        populateTask(values)

                        if (values.containsKey(Properties.PROPERTY_ID)) {
                            // process the first property, which is combined with the task row
                            populateProperty(values)

                            while (cursor.moveToNext()) {
                                // process the other properties
                                populateProperty(cursor.toContentValues())
                            }
                        }

                        // Special case: parent_id set, but no matching parent Relation row (like given by aCalendar+)
                        val relatedToList = newTask.relatedTo
                        values.getAsLong(Tasks.PARENT_ID)?.let { parentId ->
                            val hasParentRelation = relatedToList.any { relatedTo ->
                                val relatedType = relatedTo.getParameter<RelType>(Parameter.RELTYPE)
                                relatedType == RelType.PARENT || relatedType == null /* RelType.PARENT is the default value */
                            }
                            if (!hasParentRelation) {
                                // get UID of parent task
                                val parentContentUri = ContentUris.withAppendedId(taskList.tasksUri(), parentId)
                                client.query(parentContentUri, arrayOf(Tasks._UID), null, null, null)?.use { cursor ->
                                    if (cursor.moveToNext()) {
                                        // add RelatedTo for parent task
                                        relatedToList += RelatedTo(cursor.getString(0))
                                    }
                                }
                            }
                        }

                        field = newTask
                        return newTask
                    }
                }
            } catch (e: Exception) {
                /* Populating event has been interrupted by an exception, so we reset the event to
                avoid an inconsistent state. This also ensures that the exception will be thrown
                again on the next get() call. */
                field = null
                throw e
            }
            throw FileNotFoundException("Couldn't find task #$id")
        }

    private fun populateTask(values: ContentValues) {
        val task = requireNotNull(task)

        task.uid = values.getAsString(Tasks._UID)
        task.sequence = values.getAsInteger(Tasks.SYNC_VERSION)
        task.summary = values.getAsString(Tasks.TITLE)
        task.location = values.getAsString(Tasks.LOCATION)
        task.userAgents += taskList.providerName.packageName

        values.getAsString(Tasks.GEO)?.let { geo ->
            val (lng, lat) = geo.split(',')
            try {
                task.geoPosition = Geo(lat.toBigDecimal(), lng.toBigDecimal())
            } catch (e: NumberFormatException) {
                logger.log(Level.WARNING, "Invalid GEO value: $geo", e)
            }
        }

        task.description = values.getAsString(Tasks.DESCRIPTION)
        task.color = values.getAsInteger(Tasks.TASK_COLOR)
        task.url = values.getAsString(Tasks.URL)

        values.getAsString(Tasks.ORGANIZER)?.let {
            try {
                task.organizer = Organizer("mailto:$it")
            } catch(e: URISyntaxException) {
                logger.log(Level.WARNING, "Invalid ORGANIZER email", e)
            }
        }

        values.getAsInteger(Tasks.PRIORITY)?.let { task.priority = it }

        task.classification = when (values.getAsInteger(Tasks.CLASSIFICATION)) {
            Tasks.CLASSIFICATION_PUBLIC ->       Clazz.PUBLIC
            Tasks.CLASSIFICATION_PRIVATE ->      Clazz.PRIVATE
            Tasks.CLASSIFICATION_CONFIDENTIAL -> Clazz.CONFIDENTIAL
            else ->                              null
        }

        values.getAsLong(Tasks.COMPLETED)?.let { task.completedAt = Completed(DateTime(it)) }
        values.getAsInteger(Tasks.PERCENT_COMPLETE)?.let { task.percentComplete = it }

        task.status = when (values.getAsInteger(Tasks.STATUS)) {
            Tasks.STATUS_IN_PROCESS -> Status.VTODO_IN_PROCESS
            Tasks.STATUS_COMPLETED ->  Status.VTODO_COMPLETED
            Tasks.STATUS_CANCELLED ->  Status.VTODO_CANCELLED
            else ->                    Status.VTODO_NEEDS_ACTION
        }

        val allDay = (values.getAsInteger(Tasks.IS_ALLDAY) ?: 0) != 0

        val tzID = values.getAsString(Tasks.TZ)
        val tz = tzID?.let {
            val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
            tzRegistry.getTimeZone(it)
        }

        values.getAsLong(Tasks.CREATED)?.let { task.createdAt = it }
        values.getAsLong(Tasks.LAST_MODIFIED)?.let { task.lastModified = it }

        values.getAsLong(Tasks.DTSTART)?.let { dtStart ->
            task.dtStart =
                    if (allDay)
                        DtStart(Date(dtStart))
                    else {
                        val dt = DateTime(dtStart)
                        if (tz == null)
                            DtStart(dt, true)
                        else
                            DtStart(dt.apply {
                                timeZone = tz
                            })
                    }
        }

        values.getAsLong(Tasks.DUE)?.let { due ->
            task.due =
                    if (allDay)
                        Due(Date(due))
                    else {
                        val dt = DateTime(due)
                        if (tz == null)
                            Due(dt).apply {
                                isUtc = true
                            }
                        else
                            Due(dt.apply {
                                timeZone = tz
                            })
                    }
        }

        values.getAsString(Tasks.DURATION)?.let { duration ->
            val fixedDuration = AndroidTimeUtils.parseDuration(duration)
            task.duration = Duration(fixedDuration)
        }

        values.getAsString(Tasks.RDATE)?.let {
            task.rDates += AndroidTimeUtils.androidStringToRecurrenceSet(it, tzRegistry, allDay) { dates -> RDate(dates) }
        }
        values.getAsString(Tasks.EXDATE)?.let {
            task.exDates += AndroidTimeUtils.androidStringToRecurrenceSet(it, tzRegistry, allDay) { dates -> ExDate(dates) }
        }

        values.getAsString(Tasks.RRULE)?.let { task.rRule = RRule(it) }
    }

    private fun populateProperty(row: ContentValues) {
        logger.log(Level.FINER, "Found property", row)

        val task = requireNotNull(task)
        when (val type = row.getAsString(Properties.MIMETYPE)) {
            Alarm.CONTENT_ITEM_TYPE ->
                populateAlarm(row)
            Category.CONTENT_ITEM_TYPE ->
                task.categories += row.getAsString(Category.CATEGORY_NAME)
            Comment.CONTENT_ITEM_TYPE ->
                task.comment = row.getAsString(Comment.COMMENT)
            Relation.CONTENT_ITEM_TYPE ->
                populateRelatedTo(row)
            UnknownProperty.CONTENT_ITEM_TYPE ->
                task.unknownProperties += UnknownProperty.fromJsonString(row.getAsString(UNKNOWN_PROPERTY_DATA))
            else ->
                logger.warning("Found unknown property of type $type")
        }
    }

    private fun populateAlarm(row: ContentValues) {
        val task = requireNotNull(task)
        val props = PropertyList<Property>()

        val trigger = Trigger(java.time.Duration.ofMinutes(-row.getAsLong(Alarm.MINUTES_BEFORE)))
        when (row.getAsInteger(Alarm.REFERENCE)) {
            Alarm.ALARM_REFERENCE_START_DATE ->
                trigger.parameters.add(Related.START)
            Alarm.ALARM_REFERENCE_DUE_DATE ->
                trigger.parameters.add(Related.END)
        }
        props += trigger

        props += when (row.getAsInteger(Alarm.ALARM_TYPE)) {
            Alarm.ALARM_TYPE_EMAIL ->
                Action.EMAIL
            Alarm.ALARM_TYPE_SOUND ->
                Action.AUDIO
            else ->
                // show alarm by default
                Action.DISPLAY
        }

        props += Description(row.getAsString(Alarm.MESSAGE) ?: task.summary)

        task.alarms += VAlarm(props)
    }

    private fun populateRelatedTo(row: ContentValues) {
        val uid = row.getAsString(Relation.RELATED_UID)
        if (uid == null) {
            logger.warning("Task relation doesn't refer to same task list; can't be synchronized")
            return
        }

        val relatedTo = RelatedTo(uid)

        // add relation type as reltypeparam
        relatedTo.parameters.add(when (row.getAsInteger(Relation.RELATED_TYPE)) {
            Relation.RELTYPE_CHILD ->
                RelType.CHILD
            Relation.RELTYPE_SIBLING ->
                RelType.SIBLING
            else /* Relation.RELTYPE_PARENT, default value */ ->
                RelType.PARENT
        })

        requireNotNull(task).relatedTo.add(relatedTo)
    }


    fun add(): Uri {
        val batch = TasksBatchOperation(taskList.provider.client)

        val requiredTask = requireNotNull(task)
        val builder = DmfsTaskBuilder(taskList, requiredTask, id, syncId, eTag, flags)
        val idxTask = builder.addRows(batch)
        builder.insertProperties(batch, idxTask)

        batch.commit()

        val resultUri = batch.getResult(0)?.uri
            ?: throw LocalStorageException("Empty result from provider when adding a task")
        id = ContentUris.parseId(resultUri)
        return resultUri
    }

    fun update(task: Task): Uri {
        this.task = task
        val existingId = requireNotNull(id)

        val batch = TasksBatchOperation(taskList.provider.client)

        // remove associated rows which are added later again
        batch += CpoBuilder
            .newDelete(taskList.tasksPropertiesUri())
            .withSelection("${Properties.TASK_ID}=?", arrayOf(existingId.toString()))

        // update task
        val builder = DmfsTaskBuilder(taskList, task, id, syncId, eTag, flags)
        builder.updateRows(batch)

        // insert task properties again
        builder.insertProperties(batch, null)

        batch.commit()
        return ContentUris.withAppendedId(Tasks.getContentUri(taskList.providerName.authority), existingId)
    }

    fun update(values: ContentValues) {
        taskList.provider.client.update(taskSyncURI(), values, null, null)
    }

    fun delete(): Int {
        return taskList.provider.client.delete(taskSyncURI(), null, null)
    }

    private fun taskSyncURI(loadProperties: Boolean = false): Uri {
        val id = requireNotNull(id)
        return ContentUris.withAppendedId(taskList.tasksUri(loadProperties), id)
    }

    companion object {
        const val UNKNOWN_PROPERTY_DATA = Properties.DATA0

        const val COLUMN_ETAG = Tasks.SYNC1

        const val COLUMN_FLAGS = Tasks.SYNC2
    }

}
