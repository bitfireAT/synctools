/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks

import android.content.ContentValues
import at.bitfire.ical4android.DmfsTask.Companion.UNKNOWN_PROPERTY_DATA
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
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
import java.net.URISyntaxException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Reads dmfs task provider data rows into a [Task]
 * (former DmfsTask "populate..." methods).
 */
class DmfsTaskProcessor(
    private val taskList: DmfsTaskList
) {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val tzRegistry by lazy { TimeZoneRegistryFactory.getInstance().createRegistry() }

    fun populateTask(values: ContentValues, to: Task) {
        to.uid = values.getAsString(Tasks._UID)
        to.sequence = values.getAsInteger(Tasks.SYNC_VERSION)
        to.summary = values.getAsString(Tasks.TITLE)
        to.location = values.getAsString(Tasks.LOCATION)
        to.userAgents += taskList.providerName.packageName

        values.getAsString(Tasks.GEO)?.let { geo ->
            val (lng, lat) = geo.split(',')
            try {
                to.geoPosition = Geo(lat.toBigDecimal(), lng.toBigDecimal())
            } catch (e: NumberFormatException) {
                logger.log(Level.WARNING, "Invalid GEO value: $geo", e)
            }
        }

        to.description = values.getAsString(Tasks.DESCRIPTION)
        to.color = values.getAsInteger(Tasks.TASK_COLOR)
        to.url = values.getAsString(Tasks.URL)

        values.getAsString(Tasks.ORGANIZER)?.let {
            try {
                to.organizer = Organizer("mailto:$it")
            } catch(e: URISyntaxException) {
                logger.log(Level.WARNING, "Invalid ORGANIZER email", e)
            }
        }

        values.getAsInteger(Tasks.PRIORITY)?.let { to.priority = it }

        to.classification = when (values.getAsInteger(Tasks.CLASSIFICATION)) {
            Tasks.CLASSIFICATION_PUBLIC ->       Clazz.PUBLIC
            Tasks.CLASSIFICATION_PRIVATE ->      Clazz.PRIVATE
            Tasks.CLASSIFICATION_CONFIDENTIAL -> Clazz.CONFIDENTIAL
            else ->                              null
        }

        values.getAsLong(Tasks.COMPLETED)?.let { to.completedAt = Completed(DateTime(it)) }
        values.getAsInteger(Tasks.PERCENT_COMPLETE)?.let { to.percentComplete = it }

        to.status = when (values.getAsInteger(Tasks.STATUS)) {
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

        values.getAsLong(Tasks.CREATED)?.let { to.createdAt = it }
        values.getAsLong(Tasks.LAST_MODIFIED)?.let { to.lastModified = it }

        values.getAsLong(Tasks.DTSTART)?.let { dtStart ->
            to.dtStart =
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
            to.due =
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
            to.duration = Duration(fixedDuration)
        }

        values.getAsString(Tasks.RDATE)?.let {
            to.rDates += AndroidTimeUtils.androidStringToRecurrenceSet(it, tzRegistry, allDay) { dates -> RDate(dates) }
        }
        values.getAsString(Tasks.EXDATE)?.let {
            to.exDates += AndroidTimeUtils.androidStringToRecurrenceSet(it, tzRegistry, allDay) { dates -> ExDate(dates) }
        }

        values.getAsString(Tasks.RRULE)?.let { to.rRule = RRule(it) }
    }

    fun populateProperty(row: ContentValues, to: Task) {
        logger.log(Level.FINER, "Found property", row)

        when (val type = row.getAsString(Properties.MIMETYPE)) {
            Alarm.CONTENT_ITEM_TYPE ->
                populateAlarm(row, to)
            Category.CONTENT_ITEM_TYPE ->
                to.categories += row.getAsString(Category.CATEGORY_NAME)
            Comment.CONTENT_ITEM_TYPE ->
                to.comment = row.getAsString(Comment.COMMENT)
            Relation.CONTENT_ITEM_TYPE ->
                populateRelatedTo(row, to)
            UnknownProperty.CONTENT_ITEM_TYPE ->
                to.unknownProperties += UnknownProperty.fromJsonString(row.getAsString(UNKNOWN_PROPERTY_DATA))
            else ->
                logger.warning("Found unknown property of type $type")
        }
    }

    private fun populateAlarm(row: ContentValues, to: Task) {
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

        props += Description(row.getAsString(Alarm.MESSAGE) ?: to.summary)

        to.alarms += VAlarm(props)
    }

    private fun populateRelatedTo(row: ContentValues, to: Task) {
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

        to.relatedTo.add(relatedTo)
    }

}