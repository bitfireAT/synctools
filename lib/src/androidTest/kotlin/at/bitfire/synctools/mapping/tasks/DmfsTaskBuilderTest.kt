/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks

import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.database.DatabaseUtils
import android.net.Uri
import at.bitfire.ical4android.DmfsStyleProvidersTaskTest
import at.bitfire.ical4android.DmfsTask
import at.bitfire.ical4android.ICalendar
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.ical4android.impl.TestTaskList
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.Completed
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
import net.fortuna.ical4j.model.property.XProperty
import org.dmfs.tasks.contract.TaskContract
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

class DmfsTaskBuilderTest (
    providerName: TaskProvider.ProviderName
): DmfsStyleProvidersTaskTest(providerName) {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()!!
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")!!
    private val tzChicago = tzRegistry.getTimeZone("America/Chicago")!!
    private val tzDefault = tzRegistry.getTimeZone(ZoneId.systemDefault().id)!!

    private val testAccount = Account(javaClass.name, TaskContract.LOCAL_ACCOUNT_TYPE)

    private lateinit var taskListUri: Uri
    private var taskList: DmfsTaskList? = null

    @Before
    override fun prepare() {
        super.prepare()

        taskList = TestTaskList.create(testAccount, provider)
        Assert.assertNotNull("Couldn't find/create test task list", taskList)

        taskListUri = ContentUris.withAppendedId(provider.taskListsUri(), taskList!!.id)
    }

    @After
    override fun shutdown() {
        taskList?.delete()
        super.shutdown()
    }


    // builder tests

    @Test
    fun testBuildTask_Sequence() {
        buildTask {
            ICalendar.apply { sequence = 12345 }
        }.let { result ->
            assertEquals(12345, result.getAsInteger(TaskContract.Tasks.SYNC_VERSION))
        }
    }

    @Test
    fun testBuildTask_CreatedAt() {
        buildTask {
            createdAt = 1593771404  // Fri Jul 03 10:16:44 2020 UTC
        }.let { result ->
            Assert.assertEquals(1593771404, result.getAsLong(TaskContract.Tasks.CREATED))
        }
    }

    @Test
    fun testBuildTask_LastModified() {
        buildTask {
            lastModified = 1593771404
        }.let { result ->
            Assert.assertEquals(1593771404, result.getAsLong(TaskContract.Tasks.LAST_MODIFIED))
        }
    }

    @Test
    fun testBuildTask_Summary() {
        buildTask {
            summary = "Sample Summary"
        }.let { result ->
            assertEquals("Sample Summary", result.get(TaskContract.Tasks.TITLE))
        }
    }

    @Test
    fun testBuildTask_Location() {
        buildTask {
            location = "Sample Location"
        }.let { result ->
            assertEquals("Sample Location", result.get(TaskContract.Tasks.LOCATION))
        }
    }

    @Test
    fun testBuildTask_Geo() {
        buildTask {
            geoPosition = Geo(47.913563.toBigDecimal(), 16.159601.toBigDecimal())
        }.let { result ->
            assertEquals("16.159601,47.913563", result.get(TaskContract.Tasks.GEO))
        }
    }

    @Test
    fun testBuildTask_Description() {
        buildTask {
            description = "Sample Description"
        }.let { result ->
            assertEquals("Sample Description", result.get(TaskContract.Tasks.DESCRIPTION))
        }
    }

    @Test
    fun testBuildTask_Color() {
        buildTask {
            color = 0x11223344
        }.let { result ->
            assertEquals(0x11223344, result.getAsInteger(TaskContract.Tasks.TASK_COLOR))
        }
    }

    @Test
    fun testBuildTask_Url() {
        buildTask {
            url = "https://www.example.com"
        }.let { result ->
            assertEquals(
                "https://www.example.com",
                result.getAsString(TaskContract.Tasks.URL)
            )
        }
    }

    @Test
    fun testBuildTask_Organizer_MailTo() {
        buildTask {
            organizer = Organizer("mailto:organizer@example.com")
        }.let { result ->
            assertEquals(
                "organizer@example.com",
                result.getAsString(TaskContract.Tasks.ORGANIZER)
            )
        }
    }

    @Test
    fun testBuildTask_Organizer_EmailParameter() {
        buildTask {
            organizer = Organizer("uri:unknown").apply {
                parameters.add(Email("organizer@example.com"))
            }
        }.let { result ->
            assertEquals(
                "organizer@example.com",
                result.getAsString(TaskContract.Tasks.ORGANIZER)
            )
        }
    }

    @Test
    fun testBuildTask_Organizer_NotEmail() {
        buildTask {
            organizer = Organizer("uri:unknown")
        }.let { result ->
            Assert.assertNull(result.get(TaskContract.Tasks.ORGANIZER))
        }
    }

    @Test
    fun testBuildTask_Priority() {
        buildTask {
            priority = 2
        }.let { result ->
            assertEquals(2, result.getAsInteger(TaskContract.Tasks.PRIORITY))
        }
    }

    @Test
    fun testBuildTask_Classification_Public() {
        buildTask {
            classification = Clazz.PUBLIC
        }.let { result ->
            assertEquals(
                TaskContract.Tasks.CLASSIFICATION_PUBLIC,
                result.getAsInteger(TaskContract.Tasks.CLASSIFICATION)
            )
        }
    }

    @Test
    fun testBuildTask_Classification_Private() {
        buildTask {
            classification = Clazz.PRIVATE
        }.let { result ->
            assertEquals(
                TaskContract.Tasks.CLASSIFICATION_PRIVATE,
                result.getAsInteger(TaskContract.Tasks.CLASSIFICATION)
            )
        }
    }

    @Test
    fun testBuildTask_Classification_Confidential() {
        buildTask {
            classification = Clazz.CONFIDENTIAL
        }.let { result ->
            assertEquals(
                TaskContract.Tasks.CLASSIFICATION_CONFIDENTIAL,
                result.getAsInteger(TaskContract.Tasks.CLASSIFICATION)
            )
        }
    }

    @Test
    fun testBuildTask_Classification_Custom() {
        buildTask {
            classification = Clazz("x-custom")
        }.let { result ->
            assertEquals(
                TaskContract.Tasks.CLASSIFICATION_PRIVATE,
                result.getAsInteger(TaskContract.Tasks.CLASSIFICATION)
            )
        }
    }

    @Test
    fun testBuildTask_Classification_None() {
        buildTask {
        }.let { result ->
            assertEquals(
                TaskContract.Tasks.CLASSIFICATION_DEFAULT /* null */,
                result.getAsInteger(TaskContract.Tasks.CLASSIFICATION)
            )
        }
    }

    @Test
    fun testBuildTask_Status_NeedsAction() {
        buildTask {
            status = Status.VTODO_NEEDS_ACTION
        }.let { result ->
            assertEquals(
                TaskContract.Tasks.STATUS_NEEDS_ACTION,
                result.getAsInteger(TaskContract.Tasks.STATUS)
            )
        }
    }

    @Test
    fun testBuildTask_Status_Completed() {
        buildTask {
            status = Status.VTODO_COMPLETED
        }.let { result ->
            assertEquals(
                TaskContract.Tasks.STATUS_COMPLETED,
                result.getAsInteger(TaskContract.Tasks.STATUS)
            )
        }
    }

    @Test
    fun testBuildTask_Status_InProcess() {
        buildTask {
            status = Status.VTODO_IN_PROCESS
        }.let { result ->
            assertEquals(
                TaskContract.Tasks.STATUS_IN_PROCESS,
                result.getAsInteger(TaskContract.Tasks.STATUS)
            )
        }
    }

    @Test
    fun testBuildTask_Status_Cancelled() {
        buildTask {
            status = Status.VTODO_CANCELLED
        }.let { result ->
            assertEquals(
                TaskContract.Tasks.STATUS_CANCELLED,
                result.getAsInteger(TaskContract.Tasks.STATUS)
            )
        }
    }

    @Test
    fun testBuildTask_DtStart() {
        buildTask {
            dtStart = DtStart("20200703T155722", tzVienna)
        }.let { result ->
            Assert.assertEquals(1593784642000L, result.getAsLong(TaskContract.Tasks.DTSTART))
            assertEquals(tzVienna.id, result.getAsString(TaskContract.Tasks.TZ))
            assertEquals(0, result.getAsInteger(TaskContract.Tasks.IS_ALLDAY))
        }
    }

    @Test
    fun testBuildTask_DtStart_AllDay() {
        buildTask {
            dtStart = DtStart(Date("20200703"))
        }.let { result ->
            Assert.assertEquals(1593734400000L, result.getAsLong(TaskContract.Tasks.DTSTART))
            Assert.assertNull(result.get(TaskContract.Tasks.TZ))
            assertEquals(1, result.getAsInteger(TaskContract.Tasks.IS_ALLDAY))
        }
    }

    @Test
    fun testBuildTask_Due() {
        buildTask {
            due = Due(DateTime("20200703T155722", tzVienna))
        }.let { result ->
            Assert.assertEquals(1593784642000L, result.getAsLong(TaskContract.Tasks.DUE))
            assertEquals(tzVienna.id, result.getAsString(TaskContract.Tasks.TZ))
            assertEquals(0, result.getAsInteger(TaskContract.Tasks.IS_ALLDAY))
        }
    }

    @Test
    fun testBuildTask_Due_AllDay() {
        buildTask {
            due = Due(Date("20200703"))
        }.let { result ->
            Assert.assertEquals(1593734400000L, result.getAsLong(TaskContract.Tasks.DUE))
            Assert.assertNull(result.getAsString(TaskContract.Tasks.TZ))
            assertEquals(1, result.getAsInteger(TaskContract.Tasks.IS_ALLDAY))
        }
    }

    @Test
    fun testBuildTask_DtStart_NonAllDay_Due_AllDay() {
        buildTask {
            dtStart = DtStart(DateTime("20200101T010203"))
            due = Due(Date("20200201"))
        }.let { result ->
            assertEquals(
                ZoneId.systemDefault().id,
                result.getAsString(TaskContract.Tasks.TZ)
            )
            assertEquals(0, result.getAsInteger(TaskContract.Tasks.IS_ALLDAY))
        }
    }

    @Test
    fun testBuildTask_DtStart_AllDay_Due_NonAllDay() {
        buildTask {
            dtStart = DtStart(Date("20200101"))
            due = Due(DateTime("20200201T010203"))
        }.let { result ->
            Assert.assertNull(result.getAsString(TaskContract.Tasks.TZ))
            assertEquals(1, result.getAsInteger(TaskContract.Tasks.IS_ALLDAY))
        }
    }

    @Test
    fun testBuildTask_DtStart_AllDay_Due_AllDay() {
        buildTask {
            dtStart = DtStart(Date("20200101"))
            due = Due(Date("20200201"))
        }.let { result ->
            assertEquals(1, result.getAsInteger(TaskContract.Tasks.IS_ALLDAY))
        }
    }

    @Test
    fun testBuildTask_DtStart_FloatingTime() {
        buildTask {
            dtStart = DtStart("20200703T010203")
        }.let { result ->
            Assert.assertEquals(
                DateTime("20200703T010203").time,
                result.getAsLong(TaskContract.Tasks.DTSTART)
            )
            assertEquals(
                ZoneId.systemDefault().id,
                result.getAsString(TaskContract.Tasks.TZ)
            )
            assertEquals(0, result.getAsInteger(TaskContract.Tasks.IS_ALLDAY))
        }
    }

    @Test
    fun testBuildTask_DtStart_Utc() {
        buildTask {
            dtStart = DtStart(DateTime(1593730923000), true)
        }.let { result ->
            Assert.assertEquals(1593730923000L, result.getAsLong(TaskContract.Tasks.DTSTART))
            assertEquals("Etc/UTC", result.getAsString(TaskContract.Tasks.TZ))
            assertEquals(0, result.getAsInteger(TaskContract.Tasks.IS_ALLDAY))
        }
    }

    @Test
    fun testBuildTask_Due_FloatingTime() {
        buildTask {
            due = Due("20200703T010203")
        }.let { result ->
            Assert.assertEquals(
                DateTime("20200703T010203").time,
                result.getAsLong(TaskContract.Tasks.DUE)
            )
            assertEquals(
                ZoneId.systemDefault().id,
                result.getAsString(TaskContract.Tasks.TZ)
            )
            assertEquals(0, result.getAsInteger(TaskContract.Tasks.IS_ALLDAY))
        }
    }

    @Test
    fun testBuildTask_Due_Utc() {
        buildTask {
            due = Due(DateTime(1593730923000).apply { isUtc = true })
        }.let { result ->
            Assert.assertEquals(1593730923000L, result.getAsLong(TaskContract.Tasks.DUE))
            assertEquals("Etc/UTC", result.getAsString(TaskContract.Tasks.TZ))
            assertEquals(0, result.getAsInteger(TaskContract.Tasks.IS_ALLDAY))
        }
    }

    @Test
    fun testBuildTask_Duration() {
        buildTask {
            dtStart = DtStart(DateTime())
            duration = Duration(null, "P1D")
        }.let { result ->
            assertEquals("P1D", result.get(TaskContract.Tasks.DURATION))
        }
    }

    @Test
    fun testBuildTask_CompletedAt() {
        val now = DateTime()
        buildTask {
            completedAt = Completed(now)
        }.let { result ->
            // Note: iCalendar does not allow COMPLETED to be all-day [RFC 5545 3.8.2.1]
            assertEquals(0, result.getAsInteger(TaskContract.Tasks.COMPLETED_IS_ALLDAY))
            Assert.assertEquals(now.time, result.getAsLong(TaskContract.Tasks.COMPLETED))
        }
    }

    @Test
    fun testBuildTask_PercentComplete() {
        buildTask {
            percentComplete = 50
        }.let { result ->
            assertEquals(50, result.getAsInteger(TaskContract.Tasks.PERCENT_COMPLETE))
        }
    }

    @Test
    fun testBuildTask_RRule() {
        // Note: OpenTasks only supports one RRULE per VTODO (iCalendar: multiple RRULEs are allowed, but SHOULD not be used)
        buildTask {
            rRule = RRule("FREQ=DAILY;COUNT=10")
        }.let { result ->
            assertEquals("FREQ=DAILY;COUNT=10", result.getAsString(TaskContract.Tasks.RRULE))
        }
    }

    @Test
    fun testBuildTask_RDate() {
        buildTask {
            dtStart = DtStart(DateTime("20200101T010203", tzVienna))
            rDates += RDate(DateList("20200102T020304", Value.DATE_TIME, tzVienna))
            rDates += RDate(DateList("20200102T020304", Value.DATE_TIME, tzChicago))
            rDates += RDate(DateList("20200103T020304Z", Value.DATE_TIME))
            rDates += RDate(DateList("20200103", Value.DATE))
        }.let { result ->
            assertEquals(tzVienna.id, result.getAsString(TaskContract.Tasks.TZ))
            assertEquals(
                "20200102T020304,20200102T090304,20200103T020304Z,20200103T000000",
                result.getAsString(TaskContract.Tasks.RDATE)
            )
        }
    }

    @Test
    fun testBuildTask_ExDate() {
        buildTask {
            dtStart = DtStart(DateTime("20200101T010203", tzVienna))
            rRule = RRule("FREQ=DAILY;COUNT=10")
            exDates += ExDate(DateList("20200102T020304", Value.DATE_TIME, tzVienna))
            exDates += ExDate(DateList("20200102T020304", Value.DATE_TIME, tzChicago))
            exDates += ExDate(DateList("20200103T020304Z", Value.DATE_TIME))
            exDates += ExDate(DateList("20200103", Value.DATE))
        }.let { result ->
            assertEquals(tzVienna.id, result.getAsString(TaskContract.Tasks.TZ))
            assertEquals(
                "20200102T020304,20200102T090304,20200103T020304Z,20200103T000000",
                result.getAsString(TaskContract.Tasks.EXDATE)
            )
        }
    }

    @Test
    fun testBuildTask_Categories() {
        var hasCat1 = false
        var hasCat2 = false
        buildTask {
            categories.addAll(arrayOf("Cat_1", "Cat 2"))
        }.let { result ->
            val id = result.getAsLong(TaskContract.Tasks._ID)
            val uri = taskList!!.tasksPropertiesUri()
            provider.client.query(uri, arrayOf(TaskContract.Property.Category.CATEGORY_NAME), "${TaskContract.Properties.MIMETYPE}=? AND ${TaskContract.PropertyColumns.TASK_ID}=?",
                arrayOf(TaskContract.Property.Category.CONTENT_ITEM_TYPE, id.toString()), null)!!.use { cursor ->
                while (cursor.moveToNext())
                    when (cursor.getString(0)) {
                        "Cat_1" -> hasCat1 = true
                        "Cat 2" -> hasCat2 = true
                    }
            }
        }
        Assert.assertTrue(hasCat1)
        Assert.assertTrue(hasCat2)
    }

    @Test
    fun testBuildTask_Comment() {
        var hasComment = false
        buildTask {
            comment = "Comment value"
        }.let { result ->
            val id = result.getAsLong(TaskContract.Tasks._ID)
            val uri = taskList!!.tasksPropertiesUri()
            provider.client.query(uri, arrayOf(TaskContract.Property.Comment.COMMENT), "${TaskContract.Properties.MIMETYPE}=? AND ${TaskContract.PropertyColumns.TASK_ID}=?",
                arrayOf(TaskContract.Property.Comment.CONTENT_ITEM_TYPE, id.toString()), null)!!.use { cursor ->
                if (cursor.moveToNext())
                    hasComment = cursor.getString(0) == "Comment value"
            }
        }
        Assert.assertTrue(hasComment)
    }

    @Test
    fun testBuildTask_Comment_empty() {
        var hasComment: Boolean
        buildTask {
            comment = null
        }.let { result ->
            val id = result.getAsLong(TaskContract.Tasks._ID)
            val uri = taskList!!.tasksPropertiesUri()
            provider.client.query(uri, arrayOf(TaskContract.Property.Comment.COMMENT), "${TaskContract.Properties.MIMETYPE}=? AND ${TaskContract.PropertyColumns.TASK_ID}=?",
                arrayOf(TaskContract.Property.Comment.CONTENT_ITEM_TYPE, id.toString()), null)!!.use { cursor ->
                hasComment = cursor.count > 0
            }
        }
        Assert.assertFalse(hasComment)
    }

    private fun firstProperty(taskId: Long, mimeType: String): ContentValues? {
        val uri = taskList!!.tasksPropertiesUri()
        provider.client.query(uri, null, "${TaskContract.Properties.MIMETYPE}=? AND ${TaskContract.PropertyColumns.TASK_ID}=?",
            arrayOf(mimeType, taskId.toString()), null)!!.use { cursor ->
            if (cursor.moveToNext()) {
                val result = ContentValues(cursor.count)
                DatabaseUtils.cursorRowToContentValues(cursor, result)
                return result
            }
        }
        return null
    }

    @Test
    fun testBuildTask_RelatedTo_Parent() {
        buildTask {
            relatedTo.add(RelatedTo("Parent-Task").apply {
                parameters.add(RelType.PARENT)
            })
        }.let { result ->
            val taskId = result.getAsLong(TaskContract.Tasks._ID)
            val relation = firstProperty(taskId, TaskContract.Property.Relation.CONTENT_ITEM_TYPE)!!
            assertEquals(
                "Parent-Task",
                relation.getAsString(TaskContract.Property.Relation.RELATED_UID)
            )
            Assert.assertNull(relation.get(TaskContract.Property.Relation.RELATED_ID))   // other task not in DB (yet)
            assertEquals(
                TaskContract.Property.Relation.RELTYPE_PARENT,
                relation.getAsInteger(TaskContract.Property.Relation.RELATED_TYPE)
            )
        }
    }

    @Test
    fun testBuildTask_RelatedTo_Child() {
        buildTask {
            relatedTo.add(RelatedTo("Child-Task").apply {
                parameters.add(RelType.CHILD)
            })
        }.let { result ->
            val taskId = result.getAsLong(TaskContract.Tasks._ID)
            val relation = firstProperty(taskId, TaskContract.Property.Relation.CONTENT_ITEM_TYPE)!!
            assertEquals(
                "Child-Task",
                relation.getAsString(TaskContract.Property.Relation.RELATED_UID)
            )
            Assert.assertNull(relation.get(TaskContract.Property.Relation.RELATED_ID))   // other task not in DB (yet)
            assertEquals(
                TaskContract.Property.Relation.RELTYPE_CHILD,
                relation.getAsInteger(TaskContract.Property.Relation.RELATED_TYPE)
            )
        }
    }

    @Test
    fun testBuildTask_RelatedTo_Sibling() {
        buildTask {
            relatedTo.add(RelatedTo("Sibling-Task").apply {
                parameters.add(RelType.SIBLING)
            })
        }.let { result ->
            val taskId = result.getAsLong(TaskContract.Tasks._ID)
            val relation = firstProperty(taskId, TaskContract.Property.Relation.CONTENT_ITEM_TYPE)!!
            assertEquals(
                "Sibling-Task",
                relation.getAsString(TaskContract.Property.Relation.RELATED_UID)
            )
            Assert.assertNull(relation.get(TaskContract.Property.Relation.RELATED_ID))   // other task not in DB (yet)
            assertEquals(
                TaskContract.Property.Relation.RELTYPE_SIBLING,
                relation.getAsInteger(TaskContract.Property.Relation.RELATED_TYPE)
            )
        }
    }

    @Test
    fun testBuildTask_RelatedTo_Custom() {
        buildTask {
            relatedTo.add(RelatedTo("Sibling-Task").apply {
                parameters.add(RelType("custom-relationship"))
            })
        }.let { result ->
            val taskId = result.getAsLong(TaskContract.Tasks._ID)
            val relation = firstProperty(taskId, TaskContract.Property.Relation.CONTENT_ITEM_TYPE)!!
            assertEquals(
                "Sibling-Task",
                relation.getAsString(TaskContract.Property.Relation.RELATED_UID)
            )
            Assert.assertNull(relation.get(TaskContract.Property.Relation.RELATED_ID))   // other task not in DB (yet)
            assertEquals(
                TaskContract.Property.Relation.RELTYPE_PARENT,
                relation.getAsInteger(TaskContract.Property.Relation.RELATED_TYPE)
            )
        }
    }

    @Test
    fun testBuildTask_RelatedTo_Default() {
        buildTask {
            relatedTo.add(RelatedTo("Parent-Task"))
        }.let { result ->
            val taskId = result.getAsLong(TaskContract.Tasks._ID)
            val relation = firstProperty(taskId, TaskContract.Property.Relation.CONTENT_ITEM_TYPE)!!
            assertEquals(
                "Parent-Task",
                relation.getAsString(TaskContract.Property.Relation.RELATED_UID)
            )
            Assert.assertNull(relation.get(TaskContract.Property.Relation.RELATED_ID))   // other task not in DB (yet)
            assertEquals(
                TaskContract.Property.Relation.RELTYPE_PARENT,
                relation.getAsInteger(TaskContract.Property.Relation.RELATED_TYPE)
            )
        }
    }


    @Test
    fun testBuildTask_UnknownProperty() {
        val xProperty = XProperty("X-TEST-PROPERTY", "test-value").apply {
            parameters.add(TzId(tzVienna.id))
            parameters.add(XParameter("X-TEST-PARAMETER", "12345"))
        }
        buildTask {
            unknownProperties.add(xProperty)
        }.let { result ->
            val taskId = result.getAsLong(TaskContract.Tasks._ID)
            val unknownProperty = firstProperty(taskId, UnknownProperty.CONTENT_ITEM_TYPE)!!
            assertEquals(
                xProperty,
                UnknownProperty.fromJsonString(unknownProperty.getAsString(DmfsTask.UNKNOWN_PROPERTY_DATA))
            )
        }
    }

    @Test
    fun testBuildAllDayTask() {
        // add all-day event to calendar provider
        val task = Task()
        task.summary = "All-day task"
        task.description = "All-day task for testing"
        task.location = "Sample location testBuildAllDayTask"
        task.dtStart = DtStart(Date("20150501"))
        task.due = Due(Date("20150502"))
        Assert.assertTrue(task.isAllDay())
        val uri = DmfsTask(taskList!!, task, "9468a4cf-0d5b-4379-a704-12f1f84100ba", null, 0).add()
        Assert.assertNotNull(uri)

        val testTask = taskList!!.getTask(ContentUris.parseId(uri))
        try {
            // read again and verify result
            val task2 = testTask.task!!
            assertEquals(task.summary, task2.summary)
            assertEquals(task.description, task2.description)
            assertEquals(task.location, task2.location)
            assertEquals(task.dtStart!!.date, task2.dtStart!!.date)
            assertEquals(task.due!!.date, task2.due!!.date)
            Assert.assertTrue(task2.isAllDay())
        } finally {
            testTask.delete()
        }
    }


    // other methods

    @Test
    fun testGetTimeZone_noDateOrDateTime() {
        val builder = DmfsTaskBuilder(taskList!!, Task(), 0, "9468a4cf-0d5b-4379-a704-12f1f84100ba", null, 0)
        assertEquals(tzDefault, builder.getTimeZone())
    }

    @Test
    fun testGetTimeZone_dtstart_with_date_and_no_time() {
        val task = Task()
        val builder = DmfsTaskBuilder(taskList!!, task, 0, "410c19d7-df79-4d65-8146-40b7bec5923b", null, 0)
        val dmfsTask = DmfsTask(taskList!!, task, "410c19d7-df79-4d65-8146-40b7bec5923b", null, 0)
        dmfsTask.task!!.dtStart = DtStart("20150101")
        assertEquals(tzDefault, builder.getTimeZone())
    }

    @Test
    fun testGetTimeZone_dtstart_with_time() {
        val task = Task()
        val builder = DmfsTaskBuilder(taskList!!, task, 0, "9468a4cf-0d5b-4379-a704-12f1f84100ba", null, 0)
        val dmfsTask = DmfsTask(taskList!!, task, "9dc64544-1816-4f04-b952-e894164467f6", null, 0)
        dmfsTask.task!!.dtStart = DtStart("20150101", tzVienna)
        assertEquals(tzVienna, builder.getTimeZone())
    }


    // helpers

    private fun buildTask(taskBuilder: Task.() -> Unit): ContentValues {
        val task = Task().apply {
            taskBuilder()
        }

        val uri = DmfsTask(taskList!!, task, "9468a4cf-0d5b-4379-a704-12f1f84100ba", null, 0).add()
        provider.client.query(uri, null, null, null, null)!!.use {
            it.moveToNext()
            val values = ContentValues()
            DatabaseUtils.cursorRowToContentValues(it, values)
            return values
        }
    }

}