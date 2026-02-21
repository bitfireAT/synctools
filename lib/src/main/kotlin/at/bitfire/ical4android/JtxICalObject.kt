/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.content.ContentUris
import android.content.ContentValues
import android.net.ParseException
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.ICalendar.Companion.withUserAgents
import at.bitfire.synctools.exception.InvalidICalendarException
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.JtxBatchOperation
import at.bitfire.synctools.storage.toContentValues
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.JtxICalObject.TZ_ALLDAY
import at.techbee.jtx.JtxContract.asSyncAdapter
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.AltRep
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.CuType
import net.fortuna.ical4j.model.parameter.DelegatedFrom
import net.fortuna.ical4j.model.parameter.DelegatedTo
import net.fortuna.ical4j.model.parameter.Dir
import net.fortuna.ical4j.model.parameter.FmtType
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.parameter.Member
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.parameter.Role
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.parameter.SentBy
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Attach
import net.fortuna.ical4j.model.property.Categories
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.Color
import net.fortuna.ical4j.model.property.Comment
import net.fortuna.ical4j.model.property.Completed
import net.fortuna.ical4j.model.property.Contact
import net.fortuna.ical4j.model.property.Created
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.Geo
import net.fortuna.ical4j.model.property.LastModified
import net.fortuna.ical4j.model.property.Location
import net.fortuna.ical4j.model.property.PercentComplete
import net.fortuna.ical4j.model.property.Priority
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.RelatedTo
import net.fortuna.ical4j.model.property.Repeat
import net.fortuna.ical4j.model.property.Resources
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Trigger
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.Url
import net.fortuna.ical4j.model.property.Version
import net.fortuna.ical4j.model.property.XProperty
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.io.Reader
import java.net.URI
import java.net.URISyntaxException
import java.time.format.DateTimeParseException
import java.util.TimeZone
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

open class JtxICalObject(
    val collection: JtxCollection<JtxICalObject>
) {

    var id: Long = 0L
    lateinit var component: String
    var summary: String? = null
    var description: String? = null
    var dtstart: Long? = null
    var dtstartTimezone: String? = null
    var dtend: Long? = null
    var dtendTimezone: String? = null

    var classification: String? = null
    var status: String? = null
    var xstatus: String? = null

    var priority: Int? = null

    var due: Long? = null      // VTODO only!
    var dueTimezone: String? = null //VTODO only!
    var completed: Long? = null // VTODO only!
    var completedTimezone: String? = null //VTODO only!
    var duration: String? = null //VTODO only!

    var percent: Int? = null
    var url: String? = null
    var contact: String? = null
    var geoLat: Double? = null
    var geoLong: Double? = null
    var location: String? = null
    var locationAltrep: String? = null
    var geofenceRadius: Int? = null

    var uid: String = UUID.randomUUID().toString()

    var created: Long = System.currentTimeMillis()
    var dtstamp: Long = System.currentTimeMillis()
    var lastModified: Long = System.currentTimeMillis()
    var sequence: Long = 0

    var color: Int? = null

    var rrule: String? = null    //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5.3
    var exdate: String? = null   //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5.1
    var rdate: String? = null    //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5.2
    var recurid: String? = null  //only for recurring events, see https://tools.ietf.org/html/rfc5545#section-3.8.5
    var recuridTimezone: String? = null
    //var rstatus: String? = null

    var collectionId: Long = collection.id

    var dirty: Boolean = false     // default false to avoid instant sync after insert, can be overwritten
    var deleted: Boolean = false

    var fileName: String? = null
    var eTag: String? = null
    var scheduleTag: String? = null
    var flags: Int = 0

    var categories: MutableList<Category> = mutableListOf()
    var attachments: MutableList<Attachment> = mutableListOf()
    var attendees: MutableList<Attendee> = mutableListOf()
    var comments: MutableList<Comment> = mutableListOf()
    var organizer: Organizer? = null
    var resources: MutableList<Resource> = mutableListOf()
    var relatedTo: MutableList<RelatedTo> = mutableListOf()
    var alarms: MutableList<Alarm> = mutableListOf()
    var unknown: MutableList<Unknown> = mutableListOf()

    private var recurInstances: MutableList<JtxICalObject> = mutableListOf()




    data class Category(
        var categoryId: Long = 0L,
        var text: String? = null,
        var language: String? = null,
        var other: String? = null
    )

    data class Attachment(
        var attachmentId: Long = 0L,
        var uri: String? = null,
        var binary: String? = null,
        var fmttype: String? = null,
        var other: String? = null,
        var filename: String? = null,
        var extension: String? = null,
        var filesize: Long? = null
    )

    data class Comment(
        var commentId: Long = 0L,
        var text: String? = null,
        var altrep: String? = null,
        var language: String? = null,
        var other: String? = null
    )

    data class RelatedTo(
        var relatedtoId: Long = 0L,
        var text: String? = null,
        var reltype: String? = null,
        var other: String? = null
    )

    data class Attendee(
        var attendeeId: Long = 0L,
        var caladdress: String? = null,
        var cutype: String? = JtxContract.JtxAttendee.Cutype.INDIVIDUAL.name,
        var member: String? = null,
        var role: String? = JtxContract.JtxAttendee.Role.`REQ-PARTICIPANT`.name,
        var partstat: String? = null,
        var rsvp: Boolean? = null,
        var delegatedto: String? = null,
        var delegatedfrom: String? = null,
        var sentby: String? = null,
        var cn: String? = null,
        var dir: String? = null,
        var language: String? = null,
        var other: String? = null
    )

    data class Resource(
        var resourceId: Long = 0L,
        var text: String? = null,
        var altrep: String? = null,
        var language: String? = null,
        var other: String? = null
    )

    data class Organizer(
        var organizerId: Long = 0L,
        var caladdress: String? = null,
        var cn: String? = null,
        var dir: String? = null,
        var sentby: String? = null,
        var language: String? = null,
        var other: String? = null
    )

    data class Alarm(
        var alarmId: Long = 0L,
        var action: String? = null,
        var description: String? = null,
        var summary: String? = null,
        var attendee: String? = null,
        var duration: String? = null,
        var repeat: String? = null,
        var attach: String? = null,
        var other: String? = null,
        var triggerTime: Long? = null,
        var triggerTimezone: String? = null,
        var triggerRelativeTo: String? = null,
        var triggerRelativeDuration: String? = null
    )

    data class Unknown(
        var unknownId: Long = 0L,
        var value: String? = null
    )


    companion object {

        private val logger
            get() = Logger.getLogger(JtxICalObject::class.java.name)

        const val X_PROP_COMPLETEDTIMEZONE = "X-COMPLETEDTIMEZONE"
        const val X_PARAM_ATTACH_LABEL = "X-LABEL"     // used for filename in KOrganizer
        const val X_PARAM_FILENAME = "FILENAME"     // used for filename in GNOME Evolution
        const val X_PROP_XSTATUS = "X-STATUS"   // used to define an extended status (additionally to standard status)
        const val X_PROP_GEOFENCE_RADIUS = "X-GEOFENCE-RADIUS"   // used to define a Geofence-Radius to notifiy the user when close

        /**
         * Parses an iCalendar resource and extracts the VTODOs and/or VJOURNALS.
         *
         * @param reader where the iCalendar is taken from
         *
         * @return array of filled [JtxICalObject] data objects (may have size 0)
         *
         * @throws InvalidICalendarException when the iCalendar can't be parsed
         * @throws IOException on I/O errors
         */
        fun fromReader(
            reader: Reader,
            collection: JtxCollection<JtxICalObject>
        ): List<JtxICalObject> {
            TODO("ical4j 4.x")
        }

        /**
         * Extracts VAlarms from the given Component (VJOURNAL or VTODO). The VAlarm is supposed to be a component within the VJOURNAL or VTODO component.
         * Other components than VAlarms should not occur.
         * @param [iCalObject] where the VAlarms should be inserted
         * @param [calComponents] from which the VAlarms should be extracted
         */
        private fun extractVAlarms(iCalObject: JtxICalObject, calComponents: ComponentList<*>) {

            TODO("ical4j 4.x")
        }

        /**
         * Extracts properties from a given Property list and maps it to a JtxICalObject
         * @param [iCalObject] where the properties should be mapped to
         * @param [properties] from which the properties can be extracted
         */
        private fun extractProperties(iCalObject: JtxICalObject, properties: PropertyList) {
            TODO("ical4j 4.x")
        }

    }

    /**
     * Takes the current JtxICalObject and transforms it to a Calendar (ical4j)
     *
     * @param prodId    `PRODID` that identifies the app
     *
     * @return The current JtxICalObject transformed into a ical4j Calendar
     */
    fun getICalendarFormat(prodId: ProdId): Calendar? {
        TODO("ical4j 4.x")
    }

    /**
     * Takes the current JtxICalObject, transforms it to an iCalendar and writes it in an OutputStream
     *
     * @param [os] OutputStream where iCalendar should be written to
     * @param prodId    `PRODID` that identifies the app
     */
    fun write(os: OutputStream, prodId: ProdId) {
        CalendarOutputter(false).output(this.getICalendarFormat(prodId), os)
    }

    /**
     * This function maps the current JtxICalObject to a iCalendar property list
     * @param [props] The PropertyList where the properties should be added
     */
    private fun addProperties(props: PropertyList) {
        TODO("ical4j 4.x")
    }


    fun prepareForUpload(): String {
        return "${this.uid}.ics"
    }

    /**
     * Updates the fileName, eTag and scheduleTag of the current JtxICalObject
     */
    fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {

        var updateUri = JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, this.id.toString())

        val values = ContentValues()
        fileName?.let { values.put(JtxContract.JtxICalObject.FILENAME, fileName) }
        eTag?.let { values.put(JtxContract.JtxICalObject.ETAG, eTag) }
        scheduleTag?.let { values.put(JtxContract.JtxICalObject.SCHEDULETAG, scheduleTag) }
        values.put(JtxContract.JtxICalObject.DIRTY, false)

        collection.client.update(updateUri, values, null, null)
    }

    /**
     * Updates the flags of the current JtxICalObject
     * @param [flags] to be set as [Int]
     */
    fun updateFlags(flags: Int) {

        var updateUri = JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, this.id.toString())

        val values = ContentValues(1)
        values.put(JtxContract.JtxICalObject.FLAGS, flags)
        collection.client.update(updateUri, values, null, null)
        this.flags = flags
    }

    /**
     * adds the current JtxICalObject in the jtx DB through the provider
     * @return the Content [Uri] of the inserted object
     */
    fun add(): Uri {
        val values = this.toContentValues()

        val newUri = collection.client.insert(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account),
            values
        ) ?: return Uri.EMPTY
        this.id = newUri.lastPathSegment?.toLong() ?: return Uri.EMPTY

        insertOrUpdateListProperties(false)

        return newUri
    }

    /**
     * Updates the current JtxICalObject with the given data
     * @param [data] The JtxICalObject with the information that should be applied to this object and updated in the provider
     * @return [Uri] of the updated entry
     */
    fun update(data: JtxICalObject): Uri {

        this.applyNewData(data)
        val values = this.toContentValues()

        var updateUri = JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account)
        updateUri = Uri.withAppendedPath(updateUri, this.id.toString())
        collection.client.update(
            updateUri,
            values,
            "${JtxContract.JtxICalObject.ID} = ?",
            arrayOf(this.id.toString())
        )

        insertOrUpdateListProperties(true)

        return updateUri
    }


    /**
     * This function takes care of all list properties and inserts them in the DB through the provider
     * @param isUpdate if true then the list properties are deleted through the provider before they are inserted
     */
    private fun insertOrUpdateListProperties(isUpdate: Boolean) {

        // delete the categories, attendees, ... and insert them again after. Only relevant for Update, for an insert there will be no entries
        if (isUpdate) {
            val deleteBatch = JtxBatchOperation(collection.client)

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxCategory.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxCategory.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxComment.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxComment.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxResource.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxResource.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxRelatedto.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxAttendee.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxAttendee.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxOrganizer.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxOrganizer.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxAttachment.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxAlarm.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxAlarm.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch += BatchOperation.CpoBuilder
                .newDelete(JtxContract.JtxUnknown.CONTENT_URI.asSyncAdapter(collection.account))
                .withSelection("${JtxContract.JtxUnknown.ICALOBJECT_ID} = ?", arrayOf(this.id.toString()))

            deleteBatch.commit()
        }

        val insertBatch = JtxBatchOperation(collection.client)

        this.categories.forEach { category ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxCategory.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxCategory.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxCategory.TEXT, category.text)
                .withValue(JtxContract.JtxCategory.ID, category.categoryId)
                .withValue(JtxContract.JtxCategory.LANGUAGE, category.language)
                .withValue(JtxContract.JtxCategory.OTHER, category.other)
        }

        this.comments.forEach { comment ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxComment.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxComment.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxComment.ID, comment.commentId)
                .withValue(JtxContract.JtxComment.TEXT, comment.text)
                .withValue(JtxContract.JtxComment.LANGUAGE, comment.language)
                .withValue(JtxContract.JtxComment.OTHER, comment.other)
        }


        this.resources.forEach { resource ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxResource.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxResource.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxResource.ID, resource.resourceId)
                .withValue(JtxContract.JtxResource.TEXT, resource.text)
                .withValue(JtxContract.JtxResource.LANGUAGE, resource.language)
                .withValue(JtxContract.JtxResource.OTHER, resource.other)
        }

        this.relatedTo.forEach { related ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxRelatedto.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxRelatedto.TEXT, related.text)
                .withValue(JtxContract.JtxRelatedto.RELTYPE, related.reltype)
                .withValue(JtxContract.JtxRelatedto.OTHER, related.other)
        }

        this.attendees.forEach { attendee ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxAttendee.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxAttendee.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxAttendee.CALADDRESS, attendee.caladdress)
                .withValue(JtxContract.JtxAttendee.CN, attendee.cn)
                .withValue(JtxContract.JtxAttendee.CUTYPE, attendee.cutype)
                .withValue(JtxContract.JtxAttendee.DELEGATEDFROM, attendee.delegatedfrom)
                .withValue(JtxContract.JtxAttendee.DELEGATEDTO, attendee.delegatedto)
                .withValue(JtxContract.JtxAttendee.DIR, attendee.dir)
                .withValue(JtxContract.JtxAttendee.LANGUAGE, attendee.language)
                .withValue(JtxContract.JtxAttendee.MEMBER, attendee.member)
                .withValue(JtxContract.JtxAttendee.PARTSTAT, attendee.partstat)
                .withValue(JtxContract.JtxAttendee.ROLE, attendee.role)
                .withValue(JtxContract.JtxAttendee.RSVP, attendee.rsvp)
                .withValue(JtxContract.JtxAttendee.SENTBY, attendee.sentby)
                .withValue(JtxContract.JtxAttendee.OTHER, attendee.other)
        }

        this.organizer?.let { organizer ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxOrganizer.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxOrganizer.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxOrganizer.CALADDRESS, organizer.caladdress)
                .withValue(JtxContract.JtxOrganizer.CN, organizer.cn)
                .withValue(JtxContract.JtxOrganizer.DIR, organizer.dir)
                .withValue(JtxContract.JtxOrganizer.LANGUAGE, organizer.language)
                .withValue(JtxContract.JtxOrganizer.SENTBY, organizer.sentby)
                .withValue(JtxContract.JtxOrganizer.OTHER, organizer.other)
        }

        this.attachments.forEach { attachment ->
            val attachmentContentValues = contentValuesOf(
                JtxContract.JtxAttachment.ICALOBJECT_ID to id,
                JtxContract.JtxAttachment.URI to attachment.uri,
                JtxContract.JtxAttachment.FMTTYPE to attachment.fmttype,
                JtxContract.JtxAttachment.OTHER to attachment.other,
                JtxContract.JtxAttachment.FILENAME to attachment.filename
            )
            val newAttachment = collection.client.insert(JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(collection.account), attachmentContentValues)
            if(attachment.uri.isNullOrEmpty() && newAttachment != null) {
                val attachmentPFD = collection.client.openFile(newAttachment, "w")
                ParcelFileDescriptor.AutoCloseOutputStream(attachmentPFD).write(Base64.decode(attachment.binary, Base64.DEFAULT))
            }
        }

        this.alarms.forEach { alarm ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxAlarm.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxAlarm.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxAlarm.ACTION, alarm.action)
                .withValue(JtxContract.JtxAlarm.ATTACH, alarm.attach)
                //.withValue(JtxContract.JtxAlarm.ATTENDEE, alarm.attendee)
                .withValue(JtxContract.JtxAlarm.DESCRIPTION, alarm.description)
                .withValue(JtxContract.JtxAlarm.DURATION, alarm.duration)
                .withValue(JtxContract.JtxAlarm.REPEAT, alarm.repeat)
                .withValue(JtxContract.JtxAlarm.SUMMARY, alarm.summary)
                .withValue(JtxContract.JtxAlarm.TRIGGER_RELATIVE_TO, alarm.triggerRelativeTo)
                .withValue(JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION, alarm.triggerRelativeDuration)
                .withValue(JtxContract.JtxAlarm.TRIGGER_TIME, alarm.triggerTime)
                .withValue(JtxContract.JtxAlarm.TRIGGER_TIMEZONE, alarm.triggerTimezone)
                .withValue(JtxContract.JtxAlarm.OTHER, alarm.other)
        }

        this.unknown.forEach { unknown ->
            insertBatch += BatchOperation.CpoBuilder
                .newInsert(JtxContract.JtxUnknown.CONTENT_URI.asSyncAdapter(collection.account))
                .withValue(JtxContract.JtxUnknown.ICALOBJECT_ID, id)
                .withValue(JtxContract.JtxUnknown.UNKNOWN_VALUE, unknown.value)
        }

        insertBatch.commit()
    }

    /**
     * Deletes the current JtxICalObject
     * @return The number of deleted records (should always be 1)
     */
    fun delete(): Int {
        val uri = Uri.withAppendedPath(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account),
            id.toString()
        )
        return collection.client.delete(uri, null, null)
    }


    /**
     * This function is used for empty JtxICalObjects that need new data applied, usually a LocalJtxICalObject.
     * @param [newData], the (local) JtxICalObject that should be mapped onto the given JtxICalObject
     */
    fun applyNewData(newData: JtxICalObject) {

        this.component = newData.component
        this.sequence = newData.sequence
        this.created = newData.created
        this.lastModified = newData.lastModified
        this.summary = newData.summary
        this.description = newData.description
        this.uid = newData.uid

        this.location = newData.location
        this.locationAltrep = newData.locationAltrep
        this.geoLat = newData.geoLat
        this.geoLong = newData.geoLong
        this.geofenceRadius = newData.geofenceRadius
        this.percent = newData.percent
        this.classification = newData.classification
        this.status = newData.status
        this.xstatus = newData.xstatus
        this.priority = newData.priority
        this.color = newData.color
        this.url = newData.url
        this.contact = newData.contact

        this.dtstart = newData.dtstart
        this.dtstartTimezone = newData.dtstartTimezone
        this.dtend = newData.dtend
        this.dtendTimezone = newData.dtendTimezone
        this.completed = newData.completed
        this.completedTimezone = newData.completedTimezone
        this.due = newData.due
        this.dueTimezone = newData.dueTimezone
        this.duration = newData.duration

        this.rrule = newData.rrule
        this.rdate = newData.rdate
        this.exdate = newData.exdate
        this.recurid = newData.recurid
        this.recuridTimezone = newData.recuridTimezone


        this.categories = newData.categories
        this.comments = newData.comments
        this.resources = newData.resources
        this.relatedTo = newData.relatedTo
        this.attendees = newData.attendees
        this.organizer = newData.organizer
        this.attachments = newData.attachments
        this.alarms = newData.alarms
        this.unknown = newData.unknown
    }

    /**
     * Takes Content Values, applies them on the current JtxICalObject and retrieves all further list properties from the content provier and adds them.
     * @param [values] The Content Values with the information about the JtxICalObject
     */
    fun populateFromContentValues(values: ContentValues) {
        values.getAsLong(JtxContract.JtxICalObject.ID)?.let { id -> this.id = id }

        values.getAsString(JtxContract.JtxICalObject.COMPONENT)?.let { component -> this.component = component }
        values.getAsString(JtxContract.JtxICalObject.SUMMARY)?.let { summary -> this.summary = summary }
        values.getAsString(JtxContract.JtxICalObject.DESCRIPTION)?.let { description -> this.description = description }
        values.getAsLong(JtxContract.JtxICalObject.DTSTART)?.let { dtstart -> this.dtstart = dtstart }
        values.getAsString(JtxContract.JtxICalObject.DTSTART_TIMEZONE)?.let { dtstartTimezone -> this.dtstartTimezone = dtstartTimezone }
        values.getAsLong(JtxContract.JtxICalObject.DTEND)?.let { dtend -> this.dtend = dtend }
        values.getAsString(JtxContract.JtxICalObject.DTEND_TIMEZONE)?.let { dtendTimezone -> this.dtendTimezone = dtendTimezone }
        values.getAsString(JtxContract.JtxICalObject.STATUS)?.let { status -> this.status = status }
        values.getAsString(JtxContract.JtxICalObject.EXTENDED_STATUS)?.let { xstatus -> this.xstatus = xstatus }
        values.getAsString(JtxContract.JtxICalObject.CLASSIFICATION)?.let { classification -> this.classification = classification }
        values.getAsString(JtxContract.JtxICalObject.URL)?.let { url -> this.url = url }
        values.getAsString(JtxContract.JtxICalObject.CONTACT)?.let { contact -> this.contact = contact }
        values.getAsDouble(JtxContract.JtxICalObject.GEO_LAT)?.let { geoLat -> this.geoLat = geoLat }
        values.getAsDouble(JtxContract.JtxICalObject.GEO_LONG)?.let { geoLong -> this.geoLong = geoLong }
        values.getAsString(JtxContract.JtxICalObject.LOCATION)?.let { location -> this.location = location }
        values.getAsString(JtxContract.JtxICalObject.LOCATION_ALTREP)?.let { locationAltrep -> this.locationAltrep = locationAltrep }
        values.getAsInteger(JtxContract.JtxICalObject.GEOFENCE_RADIUS)?.let { geofenceRadius -> this.geofenceRadius = geofenceRadius  }
        values.getAsInteger(JtxContract.JtxICalObject.PERCENT)?.let { percent -> this.percent = percent }
        values.getAsInteger(JtxContract.JtxICalObject.PRIORITY)?.let { priority -> this.priority = priority }
        values.getAsLong(JtxContract.JtxICalObject.DUE)?.let { due -> this.due = due }
        values.getAsString(JtxContract.JtxICalObject.DUE_TIMEZONE)?.let { dueTimezone -> this.dueTimezone = dueTimezone }
        values.getAsLong(JtxContract.JtxICalObject.COMPLETED)?.let { completed -> this.completed = completed }
        values.getAsString(JtxContract.JtxICalObject.COMPLETED_TIMEZONE)?.let { completedTimezone -> this.completedTimezone = completedTimezone }
        values.getAsString(JtxContract.JtxICalObject.DURATION)?.let { duration -> this.duration = duration }
        values.getAsString(JtxContract.JtxICalObject.UID)?.let { uid -> this.uid = uid }
        values.getAsLong(JtxContract.JtxICalObject.CREATED)?.let { created -> this.created = created }
        values.getAsLong(JtxContract.JtxICalObject.DTSTAMP)?.let { dtstamp -> this.dtstamp = dtstamp }
        values.getAsLong(JtxContract.JtxICalObject.LAST_MODIFIED)?.let { lastModified -> this.lastModified = lastModified }
        values.getAsLong(JtxContract.JtxICalObject.SEQUENCE)?.let { sequence -> this.sequence = sequence }
        values.getAsInteger(JtxContract.JtxICalObject.COLOR)?.let { color -> this.color = color }

        values.getAsString(JtxContract.JtxICalObject.RRULE)?.let { rrule -> this.rrule = rrule }
        values.getAsString(JtxContract.JtxICalObject.EXDATE)?.let { exdate -> this.exdate = exdate }
        values.getAsString(JtxContract.JtxICalObject.RDATE)?.let { rdate -> this.rdate = rdate }
        values.getAsString(JtxContract.JtxICalObject.RECURID)?.let { recurid -> this.recurid = recurid }
        values.getAsString(JtxContract.JtxICalObject.RECURID_TIMEZONE)?.let { recuridTimezone -> this.recuridTimezone = recuridTimezone }

        this.collectionId = collection.id
        values.getAsString(JtxContract.JtxICalObject.DIRTY)?.let { dirty -> this.dirty = dirty == "1" || dirty == "true" }
        values.getAsString(JtxContract.JtxICalObject.DELETED)?.let { deleted -> this.deleted = deleted == "1" || deleted == "true" }

        values.getAsString(JtxContract.JtxICalObject.FILENAME)?.let { fileName -> this.fileName = fileName }
        values.getAsString(JtxContract.JtxICalObject.ETAG)?.let { eTag -> this.eTag = eTag }
        values.getAsString(JtxContract.JtxICalObject.SCHEDULETAG)?.let { scheduleTag -> this.scheduleTag = scheduleTag }
        values.getAsInteger(JtxContract.JtxICalObject.FLAGS)?.let { flags -> this.flags = flags }


        // Take care of categories
        getAsContentValues(
            uri = JtxContract.JtxCategory.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxCategory.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { catValues ->
            val category = Category().apply {
                catValues.getAsLong(JtxContract.JtxCategory.ID)?.let { id -> this.categoryId = id }
                catValues.getAsString(JtxContract.JtxCategory.TEXT)?.let { text -> this.text = text }
                catValues.getAsString(JtxContract.JtxCategory.LANGUAGE)?.let { language -> this.language = language }
                catValues.getAsString(JtxContract.JtxCategory.OTHER)?.let { other -> this.other = other }
            }
            categories.add(category)
        }

        // Take care of comments
        getAsContentValues(
            uri = JtxContract.JtxComment.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxComment.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { commentValues ->
            val comment = Comment().apply {
                commentValues.getAsLong(JtxContract.JtxComment.ID)?.let { id -> this.commentId = id }
                commentValues.getAsString(JtxContract.JtxComment.TEXT)?.let { text -> this.text = text }
                commentValues.getAsString(JtxContract.JtxComment.LANGUAGE)?.let { language -> this.language = language }
                commentValues.getAsString(JtxContract.JtxComment.OTHER)?.let { other -> this.other = other }
            }
            comments.add(comment)
        }

        // Take care of resources
        getAsContentValues(
            uri = JtxContract.JtxResource.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxResource.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { resourceValues ->
            val resource = Resource().apply {
                resourceValues.getAsLong(JtxContract.JtxResource.ID)?.let { id -> this.resourceId = id }
                resourceValues.getAsString(JtxContract.JtxResource.TEXT)?.let { text -> this.text = text }
                resourceValues.getAsString(JtxContract.JtxResource.LANGUAGE)?.let { language -> this.language = language }
                resourceValues.getAsString(JtxContract.JtxResource.OTHER)?.let { other -> this.other = other }
            }
            resources.add(resource)
        }


        // Take care of related-to
        getAsContentValues(
            uri = JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxRelatedto.ICALOBJECT_ID} = ? AND ${JtxContract.JtxRelatedto.RELTYPE} = ?",
            selectionArgs = arrayOf(this.id.toString(), JtxContract.JtxRelatedto.Reltype.PARENT.name)
        ).forEach { relatedToValues ->
            val relTo = RelatedTo().apply {
                relatedToValues.getAsLong(JtxContract.JtxRelatedto.ID)?.let { id -> this.relatedtoId = id }
                relatedToValues.getAsString(JtxContract.JtxRelatedto.TEXT)?.let { text -> this.text = text }
                relatedToValues.getAsString(JtxContract.JtxRelatedto.RELTYPE)?.let { reltype -> this.reltype = reltype }
                relatedToValues.getAsString(JtxContract.JtxRelatedto.OTHER)?.let { other -> this.other = other }

            }
            relatedTo.add(relTo)
        }

        // Take care of attendees
        getAsContentValues(
            uri = JtxContract.JtxAttendee.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxAttendee.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { attendeeValues ->
            val attendee = Attendee().apply {
                attendeeValues.getAsLong(JtxContract.JtxAttendee.ID)?.let { id -> this.attendeeId = id }
                attendeeValues.getAsString(JtxContract.JtxAttendee.CALADDRESS)?.let { caladdress -> this.caladdress = caladdress }
                attendeeValues.getAsString(JtxContract.JtxAttendee.CUTYPE)?.let { cutype -> this.cutype = cutype }
                attendeeValues.getAsString(JtxContract.JtxAttendee.MEMBER)?.let { member -> this.member = member }
                attendeeValues.getAsString(JtxContract.JtxAttendee.ROLE)?.let { role -> this.role = role }
                attendeeValues.getAsString(JtxContract.JtxAttendee.PARTSTAT)?.let { partstat -> this.partstat = partstat }
                attendeeValues.getAsString(JtxContract.JtxAttendee.RSVP)?.let { rsvp -> this.rsvp = rsvp == "1" }
                attendeeValues.getAsString(JtxContract.JtxAttendee.DELEGATEDTO)?.let { delto -> this.delegatedto = delto }
                attendeeValues.getAsString(JtxContract.JtxAttendee.DELEGATEDFROM)?.let { delfrom -> this.delegatedfrom = delfrom }
                attendeeValues.getAsString(JtxContract.JtxAttendee.SENTBY)?.let { sentby -> this.sentby = sentby }
                attendeeValues.getAsString(JtxContract.JtxAttendee.CN)?.let { cn -> this.cn = cn }
                attendeeValues.getAsString(JtxContract.JtxAttendee.DIR)?.let { dir -> this.dir = dir }
                attendeeValues.getAsString(JtxContract.JtxAttendee.LANGUAGE)?.let { lang -> this.language = lang }
                attendeeValues.getAsString(JtxContract.JtxAttendee.OTHER)?.let { other -> this.other = other }
            }
            attendees.add(attendee)
        }

        // Take care of organizer
        getAsContentValues(
            uri = JtxContract.JtxOrganizer.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxOrganizer.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).firstOrNull()?.let { organizerContentValues ->
            val orgnzr = Organizer().apply {
                organizerId = organizerContentValues.getAsLong(JtxContract.JtxOrganizer.ID) ?: 0L
                caladdress = organizerContentValues.getAsString(JtxContract.JtxOrganizer.CALADDRESS)
                sentby = organizerContentValues.getAsString(JtxContract.JtxOrganizer.SENTBY)
                cn = organizerContentValues.getAsString(JtxContract.JtxOrganizer.CN)
                dir = organizerContentValues.getAsString(JtxContract.JtxOrganizer.DIR)
                language = organizerContentValues.getAsString(JtxContract.JtxOrganizer.LANGUAGE)
                other = organizerContentValues.getAsString(JtxContract.JtxOrganizer.OTHER)
            }
            if(orgnzr.caladdress?.isNotEmpty() == true)   // we only take the organizer if there was a caladdress (otherwise an empty ORGANIZER is created)
                organizer = orgnzr
        }

        // Take care of attachments
        getAsContentValues(
            uri = JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxAttachment.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { attachmentValues ->
            val attachment = Attachment().apply {
                attachmentValues.getAsLong(JtxContract.JtxAttachment.ID)?.let { id -> this.attachmentId = id }
                attachmentValues.getAsString(JtxContract.JtxAttachment.URI)?.let { uri -> this.uri = uri }
                attachmentValues.getAsString(JtxContract.JtxAttachment.BINARY)?.let { value -> this.binary = value }
                attachmentValues.getAsString(JtxContract.JtxAttachment.FMTTYPE)?.let { fmttype -> this.fmttype = fmttype }
                attachmentValues.getAsString(JtxContract.JtxAttachment.OTHER)?.let { other -> this.other = other }
                attachmentValues.getAsString(JtxContract.JtxAttachment.FILENAME)?.let { filename -> this.filename = filename }
            }
            attachments.add(attachment)
        }

        // Take care of alarms
        getAsContentValues(
            uri = JtxContract.JtxAlarm.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxAlarm.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString())
        ).forEach { alarmValues ->
            val alarm = Alarm().apply {
                alarmValues.getAsLong(JtxContract.JtxAlarm.ID)?.let { id -> this.alarmId = id }
                alarmValues.getAsString(JtxContract.JtxAlarm.ACTION)?.let { action -> this.action = action }
                alarmValues.getAsString(JtxContract.JtxAlarm.DESCRIPTION)?.let { desc -> this.description = desc }
                alarmValues.getAsLong(JtxContract.JtxAlarm.TRIGGER_TIME)?.let { time -> this.triggerTime = time }
                alarmValues.getAsString(JtxContract.JtxAlarm.TRIGGER_TIMEZONE)?.let { tz -> this.triggerTimezone = tz }
                alarmValues.getAsString(JtxContract.JtxAlarm.TRIGGER_RELATIVE_TO)?.let { relative -> this.triggerRelativeTo = relative }
                alarmValues.getAsString(JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION)?.let { duration -> this.triggerRelativeDuration = duration }
                alarmValues.getAsString(JtxContract.JtxAlarm.SUMMARY)?.let { summary -> this.summary = summary }
                alarmValues.getAsString(JtxContract.JtxAlarm.DURATION)?.let { dur -> this.duration = dur }
                alarmValues.getAsString(JtxContract.JtxAlarm.REPEAT)?.let { repeat -> this.repeat = repeat }
                alarmValues.getAsString(JtxContract.JtxAlarm.ATTACH)?.let { attach -> this.attach = attach }
                alarmValues.getAsString(JtxContract.JtxAlarm.OTHER)?.let { other -> this.other = other }
            }
            alarms.add(alarm)
        }


        // Take care of unknown properties
        getAsContentValues(
            uri = JtxContract.JtxUnknown.CONTENT_URI.asSyncAdapter(collection.account),
            selection = "${JtxContract.JtxUnknown.ICALOBJECT_ID} = ?",
            selectionArgs = arrayOf(this.id.toString()),
        ).forEach { unknownValues ->
            val unknwn = Unknown().apply {
                unknownValues.getAsLong(JtxContract.JtxUnknown.ID)?.let { id -> this.unknownId = id }
                unknownValues.getAsString(JtxContract.JtxUnknown.UNKNOWN_VALUE)?.let { value -> this.value = value }
            }
            unknown.add(unknwn)
        }


        if(rrule?.isNotEmpty() == true) {
            getAsContentValues(
                uri = JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(collection.account),
                selection = "${JtxContract.JtxICalObject.UID} = ? AND ${JtxContract.JtxICalObject.RECURID} IS NOT NULL AND ${JtxContract.JtxICalObject.SEQUENCE} > 0",
                selectionArgs = arrayOf(uid)
            ).forEach { recurInstanceValues ->
                recurInstances.add(
                    JtxICalObject(collection).apply { populateFromContentValues(recurInstanceValues) }
                )
            }
        }
    }

    /**
     * Puts the current JtxICalObjects attributes into Content Values
     * @return The JtxICalObject attributes as [ContentValues] (exluding list properties)
     */
    private fun toContentValues() = contentValuesOf(
        JtxContract.JtxICalObject.ID to id,
        JtxContract.JtxICalObject.SUMMARY to summary,
        JtxContract.JtxICalObject.DESCRIPTION to description,
        JtxContract.JtxICalObject.COMPONENT to component,
        JtxContract.JtxICalObject.STATUS to status,
        JtxContract.JtxICalObject.EXTENDED_STATUS to xstatus,
        JtxContract.JtxICalObject.CLASSIFICATION to classification,
        JtxContract.JtxICalObject.PRIORITY to priority,
        JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID to collectionId,
        JtxContract.JtxICalObject.UID to uid,
        JtxContract.JtxICalObject.COLOR to color,
        JtxContract.JtxICalObject.URL to url,
        JtxContract.JtxICalObject.CONTACT to contact,
        JtxContract.JtxICalObject.GEO_LAT to geoLat,
        JtxContract.JtxICalObject.GEO_LONG to geoLong,
        JtxContract.JtxICalObject.LOCATION to location,
        JtxContract.JtxICalObject.LOCATION_ALTREP to locationAltrep,
        JtxContract.JtxICalObject.GEOFENCE_RADIUS to geofenceRadius,
        JtxContract.JtxICalObject.PERCENT to percent,
        JtxContract.JtxICalObject.DTSTAMP to dtstamp,
        JtxContract.JtxICalObject.DTSTART to dtstart,
        JtxContract.JtxICalObject.DTSTART_TIMEZONE to dtstartTimezone,
        JtxContract.JtxICalObject.DTEND to dtend,
        JtxContract.JtxICalObject.DTEND_TIMEZONE to dtendTimezone,
        JtxContract.JtxICalObject.COMPLETED to completed,
        JtxContract.JtxICalObject.COMPLETED_TIMEZONE to completedTimezone,
        JtxContract.JtxICalObject.DUE to due,
        JtxContract.JtxICalObject.DUE_TIMEZONE to dueTimezone,
        JtxContract.JtxICalObject.DURATION to duration,
        JtxContract.JtxICalObject.CREATED to created,
        JtxContract.JtxICalObject.LAST_MODIFIED to lastModified,
        JtxContract.JtxICalObject.SEQUENCE to sequence,
        JtxContract.JtxICalObject.RRULE to rrule,
        JtxContract.JtxICalObject.RDATE to rdate,
        JtxContract.JtxICalObject.EXDATE to exdate,
        JtxContract.JtxICalObject.RECURID to recurid,
        JtxContract.JtxICalObject.RECURID_TIMEZONE to recuridTimezone,

        JtxContract.JtxICalObject.FILENAME to fileName,
        JtxContract.JtxICalObject.ETAG to eTag,
        JtxContract.JtxICalObject.SCHEDULETAG to scheduleTag,
        JtxContract.JtxICalObject.FLAGS to flags,
        JtxContract.JtxICalObject.DIRTY to dirty
    )

    /**
     * @return The result of the given query as content values of the given JtxICalObject as a list of ContentValues
     */
    private fun getAsContentValues(
        uri: Uri,
        projection: Array<String>? = null,
        selection: String,
        selectionArgs: Array<String>,
        sortOrder: String? = null
    ): List<ContentValues> {

        val values: MutableList<ContentValues> = mutableListOf()
        collection.client.query(uri, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) { values.add(cursor.toContentValues()) }
        }
        return values
    }
}
