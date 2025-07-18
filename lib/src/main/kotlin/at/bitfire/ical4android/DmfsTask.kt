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
import androidx.annotation.CallSuper
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.TasksBatchOperation
import at.bitfire.synctools.storage.toContentValues
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Email
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
import net.fortuna.ical4j.util.TimeZones
import org.dmfs.tasks.contract.TaskContract.Properties
import org.dmfs.tasks.contract.TaskContract.Property.Alarm
import org.dmfs.tasks.contract.TaskContract.Property.Category
import org.dmfs.tasks.contract.TaskContract.Property.Comment
import org.dmfs.tasks.contract.TaskContract.Property.Relation
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.io.FileNotFoundException
import java.net.URISyntaxException
import java.time.ZoneId
import java.util.Locale
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
abstract class DmfsTask(
    val taskList: DmfsTaskList<DmfsTask>
) {

    companion object {
        const val UNKNOWN_PROPERTY_DATA = Properties.DATA0
    }

    protected val logger = Logger.getLogger(javaClass.name)
    protected val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }

    var id: Long? = null


    constructor(taskList: DmfsTaskList<DmfsTask>, values: ContentValues): this(taskList) {
        id = values.getAsLong(Tasks._ID)
    }

    constructor(taskList: DmfsTaskList<DmfsTask>, task: Task): this(taskList) {
        this.task = task
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
                val client = taskList.provider
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
                                val parentContentUri = ContentUris.withAppendedId(taskList.tasksSyncUri(), parentId)
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

    @CallSuper
    protected open fun populateTask(values: ContentValues) {
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

    protected open fun populateProperty(row: ContentValues) {
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

    protected open fun populateAlarm(row: ContentValues) {
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

    protected open fun populateRelatedTo(row: ContentValues) {
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
        val batch = TasksBatchOperation(taskList.provider)

        val builder = CpoBuilder.newInsert(taskList.tasksSyncUri())
        buildTask(builder, false)
        val idxTask = batch.nextBackrefIdx()
        batch += builder

        insertProperties(batch, idxTask)

        batch.commit()

        val resultUri = batch.getResult(0)?.uri
            ?: throw LocalStorageException("Empty result from provider when adding a task")
        id = ContentUris.parseId(resultUri)
        return resultUri
    }

    fun update(task: Task): Uri {
        this.task = task
        val existingId = requireNotNull(id)

        val batch = TasksBatchOperation(taskList.provider)

        // remove associated rows which are added later again
        batch += CpoBuilder
                .newDelete(taskList.tasksPropertiesSyncUri())
                .withSelection("${Properties.TASK_ID}=?", arrayOf(existingId.toString()))

        // update task
        val uri = taskSyncURI()
        val builder = CpoBuilder.newUpdate(uri)
        buildTask(builder, true)
        batch += builder

        // insert task properties again
        insertProperties(batch, null)

        batch.commit()
        return ContentUris.withAppendedId(Tasks.getContentUri(taskList.providerName.authority), existingId)
    }

    protected open fun insertProperties(batch: TasksBatchOperation, idxTask: Int?) {
        insertAlarms(batch, idxTask)
        insertCategories(batch, idxTask)
        insertComment(batch, idxTask)
        insertRelatedTo(batch, idxTask)
        insertUnknownProperties(batch, idxTask)
    }

    protected open fun insertAlarms(batch: TasksBatchOperation, idxTask: Int?) {
        val task = requireNotNull(task)
        for (alarm in task.alarms) {
            val (alarmRef, minutes) = ICalendar.vAlarmToMin(alarm, task, true) ?: continue
            val ref = when (alarmRef) {
                Related.END ->
                    Alarm.ALARM_REFERENCE_DUE_DATE
                else /* Related.START is the default value */ ->
                    Alarm.ALARM_REFERENCE_START_DATE
            }

            val alarmType = when (alarm.action?.value?.uppercase(Locale.ROOT)) {
                Action.AUDIO.value ->
                    Alarm.ALARM_TYPE_SOUND
                Action.DISPLAY.value ->
                    Alarm.ALARM_TYPE_MESSAGE
                Action.EMAIL.value ->
                    Alarm.ALARM_TYPE_EMAIL
                else ->
                    Alarm.ALARM_TYPE_NOTHING
            }

            val builder = CpoBuilder
                    .newInsert(taskList.tasksPropertiesSyncUri())
                    .withTaskId(Alarm.TASK_ID, idxTask)
                    .withValue(Alarm.MIMETYPE, Alarm.CONTENT_ITEM_TYPE)
                    .withValue(Alarm.MINUTES_BEFORE, minutes)
                    .withValue(Alarm.REFERENCE, ref)
                    .withValue(Alarm.MESSAGE, alarm.description?.value ?: alarm.summary)
                    .withValue(Alarm.ALARM_TYPE, alarmType)

            logger.log(Level.FINE, "Inserting alarm", builder.build())
            batch += builder
        }
    }

    protected open fun insertCategories(batch: TasksBatchOperation, idxTask: Int?) {
        for (category in requireNotNull(task).categories) {
            val builder = CpoBuilder.newInsert(taskList.tasksPropertiesSyncUri())
                    .withTaskId(Category.TASK_ID, idxTask)
                    .withValue(Category.MIMETYPE, Category.CONTENT_ITEM_TYPE)
                    .withValue(Category.CATEGORY_NAME, category)
            logger.log(Level.FINE, "Inserting category", builder.build())
            batch += builder
        }
    }

    protected open fun insertComment(batch: TasksBatchOperation, idxTask: Int?) {
        val comment = requireNotNull(task).comment ?: return
        val builder = CpoBuilder.newInsert(taskList.tasksPropertiesSyncUri())
            .withTaskId(Comment.TASK_ID, idxTask)
            .withValue(Comment.MIMETYPE, Comment.CONTENT_ITEM_TYPE)
            .withValue(Comment.COMMENT, comment)
        logger.log(Level.FINE, "Inserting comment", builder.build())
        batch += builder
    }

    protected open fun insertRelatedTo(batch: TasksBatchOperation, idxTask: Int?) {
        for (relatedTo in requireNotNull(task).relatedTo) {
            val relType = when ((relatedTo.getParameter(Parameter.RELTYPE) as RelType?)) {
                RelType.CHILD ->
                    Relation.RELTYPE_CHILD
                RelType.SIBLING ->
                    Relation.RELTYPE_SIBLING
                else /* RelType.PARENT, default value */ ->
                    Relation.RELTYPE_PARENT
            }
            val builder = CpoBuilder.newInsert(taskList.tasksPropertiesSyncUri())
                    .withTaskId(Relation.TASK_ID, idxTask)
                    .withValue(Relation.MIMETYPE, Relation.CONTENT_ITEM_TYPE)
                    .withValue(Relation.RELATED_UID, relatedTo.value)
                    .withValue(Relation.RELATED_TYPE, relType)
            logger.log(Level.FINE, "Inserting relation", builder.build())
            batch += builder
        }
    }

    protected open fun insertUnknownProperties(batch: TasksBatchOperation, idxTask: Int?) {
        for (property in requireNotNull(task).unknownProperties) {
            if (property.value.length > UnknownProperty.MAX_UNKNOWN_PROPERTY_SIZE) {
                logger.warning("Ignoring unknown property with ${property.value.length} octets (too long)")
                return
            }

            val builder = CpoBuilder.newInsert(taskList.tasksPropertiesSyncUri())
                    .withTaskId(Properties.TASK_ID, idxTask)
                    .withValue(Properties.MIMETYPE, UnknownProperty.CONTENT_ITEM_TYPE)
                    .withValue(UNKNOWN_PROPERTY_DATA, UnknownProperty.toJsonString(property))
            logger.log(Level.FINE, "Inserting unknown property", builder.build())
            batch += builder
        }
    }

    fun delete(): Int {
        return taskList.provider.delete(taskSyncURI(), null, null)
    }

    @CallSuper
    protected open fun buildTask(builder: CpoBuilder, update: Boolean) {
        if (!update)
            builder .withValue(Tasks.LIST_ID, taskList.id)

        val task = requireNotNull(task)
        builder .withValue(Tasks._UID, task.uid)
                .withValue(Tasks._DIRTY, 0)
                .withValue(Tasks.SYNC_VERSION, task.sequence)
                .withValue(Tasks.TITLE, task.summary)
                .withValue(Tasks.LOCATION, task.location)
                .withValue(Tasks.GEO, task.geoPosition?.let { "${it.longitude},${it.latitude}" })
                .withValue(Tasks.DESCRIPTION, task.description)
                .withValue(Tasks.TASK_COLOR, task.color)
                .withValue(Tasks.URL, task.url)

                // parent_id will be re-calculated when the relation row is inserted (if there is any)
                .withValue(Tasks.PARENT_ID, null)

        task.organizer?.let { organizer ->
            val uri = organizer.calAddress
            val email = if (uri.scheme.equals("mailto", true))
                uri.schemeSpecificPart
            else
                organizer.getParameter<Email>(Parameter.EMAIL)?.value
            if (email != null)
                builder.withValue(Tasks.ORGANIZER, email)
            else
                logger.warning("Ignoring ORGANIZER without email address (not supported by Android)")
        }

        builder .withValue(Tasks.PRIORITY, task.priority)
                .withValue(Tasks.CLASSIFICATION, when (task.classification) {
                    Clazz.PUBLIC -> Tasks.CLASSIFICATION_PUBLIC
                    Clazz.CONFIDENTIAL -> Tasks.CLASSIFICATION_CONFIDENTIAL
                    null -> Tasks.CLASSIFICATION_DEFAULT
                    else -> Tasks.CLASSIFICATION_PRIVATE    // all unknown classifications MUST be treated as PRIVATE
                })

        // COMPLETED must always be a DATE-TIME
        builder .withValue(Tasks.COMPLETED, task.completedAt?.date?.time)
                .withValue(Tasks.COMPLETED_IS_ALLDAY, 0)
                .withValue(Tasks.PERCENT_COMPLETE, task.percentComplete)

        val status = when (task.status) {
            Status.VTODO_IN_PROCESS -> Tasks.STATUS_IN_PROCESS
            Status.VTODO_COMPLETED  -> Tasks.STATUS_COMPLETED
            Status.VTODO_CANCELLED  -> Tasks.STATUS_CANCELLED
            else                    -> Tasks.STATUS_DEFAULT    // == Tasks.STATUS_NEEDS_ACTION
        }
        builder.withValue(Tasks.STATUS, status)

        val allDay = task.isAllDay()
        if (allDay) {
            builder .withValue(Tasks.IS_ALLDAY, 1)
                    .withValue(Tasks.TZ, null)
        } else {
            AndroidTimeUtils.androidifyTimeZone(task.dtStart, tzRegistry)
            AndroidTimeUtils.androidifyTimeZone(task.due, tzRegistry)
            builder .withValue(Tasks.IS_ALLDAY, 0)
                    .withValue(Tasks.TZ, getTimeZone().id)
        }

        builder .withValue(Tasks.CREATED, task.createdAt)
                .withValue(Tasks.LAST_MODIFIED, task.lastModified)

                .withValue(Tasks.DTSTART, task.dtStart?.date?.time)
                .withValue(Tasks.DUE, task.due?.date?.time)
                .withValue(Tasks.DURATION, task.duration?.value)

                .withValue(Tasks.RDATE,
                        if (task.rDates.isEmpty())
                            null
                        else
                            AndroidTimeUtils.recurrenceSetsToOpenTasksString(task.rDates, if (allDay) null else getTimeZone()))
                .withValue(Tasks.RRULE, task.rRule?.value)

                .withValue(Tasks.EXDATE,
                        if (task.exDates.isEmpty())
                            null
                        else
                            AndroidTimeUtils.recurrenceSetsToOpenTasksString(task.exDates, if (allDay) null else getTimeZone()))
        logger.log(Level.FINE, "Built task object", builder.build())
    }


    fun getTimeZone(): TimeZone {
        val task = requireNotNull(task)
        return  task.dtStart?.let { dtStart ->
                    if (dtStart.isUtc)
                        tzRegistry.getTimeZone(TimeZones.UTC_ID)
                    else
                        dtStart.timeZone
                } ?:
                task.due?.let { due ->
                    if (due.isUtc)
                        tzRegistry.getTimeZone(TimeZones.UTC_ID)
                    else
                        due.timeZone
                } ?:
                tzRegistry.getTimeZone(ZoneId.systemDefault().id)!!
    }


    protected fun CpoBuilder.withTaskId(column: String, idxTask: Int?): CpoBuilder {
        if (idxTask != null)
            withValueBackReference(column, idxTask)
        else
            withValue(column, requireNotNull(id))
        return this
    }


    protected fun taskSyncURI(loadProperties: Boolean = false): Uri {
        val id = requireNotNull(id)
        return ContentUris.withAppendedId(taskList.tasksSyncUri(loadProperties), id)
    }

}
