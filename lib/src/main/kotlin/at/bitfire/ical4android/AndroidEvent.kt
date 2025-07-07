/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.EntityIterator
import android.net.Uri
import android.os.RemoteException
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.EventsEntity
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Reminders
import android.util.Patterns
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.AndroidEvent.Companion.CATEGORIES_SEPARATOR
import at.bitfire.ical4android.AndroidEvent.Companion.numInstances
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.ical4android.util.TimeApiExtensions
import at.bitfire.ical4android.util.TimeApiExtensions.toZonedDateTime
import at.bitfire.synctools.exception.InvalidLocalResourceException
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.mapping.calendar.AndroidEventBuilder
import at.bitfire.synctools.mapping.calendar.AttendeeMappings
import at.bitfire.synctools.storage.BatchOperation.CpoBuilder
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.CalendarBatchOperation
import at.bitfire.synctools.storage.removeBlank
import at.bitfire.synctools.storage.toContentValues
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.util.TimeZones
import java.io.FileNotFoundException
import java.net.URI
import java.net.URISyntaxException
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Stores and retrieves VEVENT iCalendar objects (represented as [Event]s) to/from the
 * Android Calendar provider.
 *
 * Extend this class to process specific fields of the event.
 *
 * Important: To use recurrence exceptions, you MUST set _SYNC_ID and ORIGINAL_SYNC_ID
 * in populateEvent() / buildEvent. Setting _ID and ORIGINAL_ID is not sufficient.
 */
class AndroidEvent(
    val calendar: AndroidCalendar
) {

    private val logger: Logger
        get() = Logger.getLogger(javaClass.name)

    var id: Long? = null
        private set

    var syncId: String? = null

    var eTag: String? = null
    var scheduleTag: String? = null
    var flags: Int = 0

    /**
     * Creates a new object from an event which already exists in the calendar storage.
     *
     * @param values database row with all columns, as returned by the calendar provider
     */
    constructor(calendar: AndroidCalendar, values: ContentValues) : this(calendar) {
        this.id = values.getAsLong(Events._ID)
        this.syncId = values.getAsString(Events._SYNC_ID)
        this.eTag = values.getAsString(COLUMN_ETAG)
        this.scheduleTag = values.getAsString(COLUMN_SCHEDULE_TAG)
        this.flags = values.getAsInteger(COLUMN_FLAGS) ?: 0
    }

    /**
     * Creates a new object from an event which doesn't exist in the calendar storage yet.
     *
     * @param event event that can be saved into the calendar storage
     */
    constructor(
        calendar: AndroidCalendar,
        event: Event,
        syncId: String?,
        eTag: String? = null,
        scheduleTag: String? = null,
        flags: Int = 0
    ) : this(calendar) {
        this.event = event
        this.syncId = syncId
        this.eTag = eTag
        this.scheduleTag = scheduleTag
        this.flags = flags
    }

    private var _event: Event? = null

    /**
     * Returns the full event data, either from [event] or, if [event] is null, by reading event
     * number [id] from the Android calendar storage
     * @throws IllegalArgumentException if event has not been saved yet
     * @throws FileNotFoundException if there's no event with [id] in the calendar storage
     * @throws RemoteException on calendar provider errors
     */
    var event: Event?
        private set(value) {
            _event = value
        }
        get() {
            if (_event != null)
                return _event
            val id = requireNotNull(id)

            var iterEvents: EntityIterator? = null
            try {
                iterEvents = EventsEntity.newEntityIterator(
                        calendar.client.query(
                                ContentUris.withAppendedId(EventsEntity.CONTENT_URI, id).asSyncAdapter(calendar.account),
                                null, null, null, null),
                        calendar.client
                )

                if (iterEvents.hasNext()) {
                    val e = iterEvents.next()

                    // create new Event which will be populated
                    val newEvent = Event()
                    _event = newEvent

                    // calculate some scheduling properties
                    val groupScheduled = e.subValues.any { it.uri == Attendees.CONTENT_URI }

                    populateEvent(e.entityValues.removeBlank(), groupScheduled)

                    for (subValue in e.subValues) {
                        val subValues = subValue.values.removeBlank()
                        when (subValue.uri) {
                            Attendees.CONTENT_URI -> populateAttendee(subValues)
                            Reminders.CONTENT_URI -> populateReminder(subValues)
                            ExtendedProperties.CONTENT_URI -> populateExtended(subValues)
                        }
                    }
                    populateExceptions()
                    useRetainedClassification()
                    return newEvent
                }
            } catch (e: Exception) {
                /* Populating event has been interrupted by an exception, so we reset the event to
                avoid an inconsistent state. This also ensures that the exception will be thrown
                again on the next get() call. */
                _event = null
                throw e
            } finally {
                iterEvents?.close()
            }
            throw FileNotFoundException("Couldn't find event $id")
        }

    /**
     * Reads event data from the calendar provider.
     *
     * @param row values of an [Events] row, as returned by the calendar provider
     */
    private fun populateEvent(row: ContentValues, groupScheduled: Boolean) {
        logger.log(Level.FINE, "Read event entity from calender provider", row)
        val event = requireNotNull(event)

        row.getAsString(Events.MUTATORS)?.let { strPackages ->
            val packages = strPackages.split(MUTATORS_SEPARATOR).toSet()
            event.userAgents.addAll(packages)
        }

        val allDay = (row.getAsInteger(Events.ALL_DAY) ?: 0) != 0
        val tsStart = row.getAsLong(Events.DTSTART) ?: throw InvalidLocalResourceException("Found event without DTSTART")

        var tsEnd = row.getAsLong(Events.DTEND)
        var duration =   // only use DURATION of DTEND is not defined
                if (tsEnd == null)
                    row.getAsString(Events.DURATION)?.let { AndroidTimeUtils.parseDuration(it) }
                else
                    null

        if (allDay) {
            event.dtStart = DtStart(Date(tsStart))

            // Android events MUST have duration or dtend [https://developer.android.com/reference/android/provider/CalendarContract.Events#operations].
            // Assume 1 day if missing (should never occur, but occurs).
            if (tsEnd == null && duration == null)
                duration = Duration.ofDays(1)

            if (duration != null) {
                // Some servers have problems with DURATION, so we always generate DTEND.
                val startDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(tsStart), ZoneOffset.UTC).toLocalDate()
                if (duration is Duration)
                    duration = Period.ofDays(duration.toDays().toInt())
                tsEnd = (startDate + duration).toEpochDay() * TimeApiExtensions.MILLIS_PER_DAY
                duration = null
            }

            if (tsEnd != null) {
                when {
                    tsEnd < tsStart ->
                        logger.warning("dtEnd $tsEnd (allDay) < dtStart $tsStart (allDay), ignoring")

                    tsEnd == tsStart ->
                        logger.fine("dtEnd $tsEnd (allDay) = dtStart, won't generate DTEND property")

                    else /* tsEnd > tsStart */ ->
                        event.dtEnd = DtEnd(Date(tsEnd))
                }
            }

        } else /* !allDay */ {
            // use DATE-TIME values

            // check time zone ID (calendar apps may insert no or an invalid ID)
            val startTzId = DateUtils.findAndroidTimezoneID(row.getAsString(Events.EVENT_TIMEZONE))
            val startTz = DateUtils.ical4jTimeZone(startTzId)
            val dtStartDateTime = DateTime(tsStart).apply {
                if (startTz != null) {  // null if there was not ical4j time zone for startTzId, which should not happen, but technically may happen
                    if (TimeZones.isUtc(startTz))
                        isUtc = true
                    else
                        timeZone = startTz
                }
            }
            event.dtStart = DtStart(dtStartDateTime)

            // Android events MUST have duration or dtend [https://developer.android.com/reference/android/provider/CalendarContract.Events#operations].
            // Assume 1 hour if missing (should never occur, but occurs).
            if (tsEnd == null && duration == null)
                duration = Duration.ofHours(1)

            if (duration != null) {
                // Some servers have problems with DURATION, so we always generate DTEND.
                val zonedStart = dtStartDateTime.toZonedDateTime()
                tsEnd = (zonedStart + duration).toInstant().toEpochMilli()
                duration = null
            }

            if (tsEnd != null) {
                if (tsEnd < tsStart)
                    logger.warning("dtEnd $tsEnd < dtStart $tsStart, ignoring")
                /*else if (tsEnd == tsStart)    // iCloud sends 404 when it receives an iCalendar with DTSTART but without DTEND
                    logger.fine("dtEnd $tsEnd == dtStart, won't generate DTEND property")*/
                else /* tsEnd > tsStart */ {
                    val endTz = row.getAsString(Events.EVENT_END_TIMEZONE)?.let { tzId ->
                        DateUtils.ical4jTimeZone(tzId)
                    } ?: startTz
                    event.dtEnd = DtEnd(DateTime(tsEnd).apply {
                        if (endTz != null) {
                            if (TimeZones.isUtc(endTz))
                                isUtc = true
                            else
                                timeZone = endTz
                        }
                    })
                }
            }

        }

        // recurrence
        try {
            row.getAsString(Events.RRULE)?.let { rulesStr ->
                for (rule in rulesStr.split(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR))
                    event.rRules += RRule(rule)
            }
            row.getAsString(Events.RDATE)?.let { datesStr ->
                val rDate = AndroidTimeUtils.androidStringToRecurrenceSet(datesStr, allDay, tsStart) { RDate(it) }
                event.rDates += rDate
            }

            row.getAsString(Events.EXRULE)?.let { rulesStr ->
                for (rule in rulesStr.split(AndroidTimeUtils.RECURRENCE_RULE_SEPARATOR))
                    event.exRules += ExRule(null, rule)
            }
            row.getAsString(Events.EXDATE)?.let { datesStr ->
                val exDate = AndroidTimeUtils.androidStringToRecurrenceSet(datesStr, allDay) { ExDate(it) }
                event.exDates += exDate
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't parse recurrence rules, ignoring", e)
        }

        event.uid = row.getAsString(Events.UID_2445)
        event.sequence = row.getAsInteger(COLUMN_SEQUENCE)
        event.isOrganizer = row.getAsBoolean(Events.IS_ORGANIZER)

        event.summary = row.getAsString(Events.TITLE)
        event.location = row.getAsString(Events.EVENT_LOCATION)
        event.description = row.getAsString(Events.DESCRIPTION)

        // color can be specified as RGB value and/or as index key (CSS3 color of AndroidCalendar)
        event.color =
            row.getAsString(Events.EVENT_COLOR_KEY)?.let { name ->      // try color key first
                try {
                    Css3Color.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    logger.warning("Ignoring unknown color name \"$name\"")
                    null
                }
            } ?:
            row.getAsInteger(Events.EVENT_COLOR)?.let { color ->        // otherwise, try to find the color name from the value
                Css3Color.entries.firstOrNull { it.argb == color }
            }

        // status
        when (row.getAsInteger(Events.STATUS)) {
            Events.STATUS_CONFIRMED -> event.status = Status.VEVENT_CONFIRMED
            Events.STATUS_TENTATIVE -> event.status = Status.VEVENT_TENTATIVE
            Events.STATUS_CANCELED -> event.status = Status.VEVENT_CANCELLED
        }

        // availability
        event.opaque = row.getAsInteger(Events.AVAILABILITY) != Events.AVAILABILITY_FREE

        // scheduling
        if (groupScheduled) {
            // ORGANIZER must only be set for group-scheduled events (= events with attendees)
            if (row.containsKey(Events.ORGANIZER))
                try {
                    event.organizer = Organizer(URI("mailto", row.getAsString(Events.ORGANIZER), null))
                } catch (e: URISyntaxException) {
                    logger.log(Level.WARNING, "Error when creating ORGANIZER mailto URI, ignoring", e)
                }
        }

        // classification
        when (row.getAsInteger(Events.ACCESS_LEVEL)) {
            Events.ACCESS_PUBLIC -> event.classification = Clazz.PUBLIC
            Events.ACCESS_PRIVATE -> event.classification = Clazz.PRIVATE
            Events.ACCESS_CONFIDENTIAL -> event.classification = Clazz.CONFIDENTIAL
        }

        // exceptions from recurring events
        row.getAsLong(Events.ORIGINAL_INSTANCE_TIME)?.let { originalInstanceTime ->
            val originalAllDay = (row.getAsInteger(Events.ORIGINAL_ALL_DAY) ?: 0) != 0
            val originalDate =
                    if (originalAllDay)
                        Date(originalInstanceTime)
                    else
                        DateTime(originalInstanceTime)
            if (originalDate is DateTime) {
                event.dtStart?.let { dtStart ->
                    if (dtStart.isUtc)
                        originalDate.isUtc = true
                    else if (dtStart.timeZone != null)
                        originalDate.timeZone = dtStart.timeZone
                }
            }
            event.recurrenceId = RecurrenceId(originalDate)
        }
    }

    private fun populateAttendee(row: ContentValues) {
        logger.log(Level.FINE, "Read event attendee from calender provider", row)

        try {
            val attendee: Attendee
            val email = row.getAsString(Attendees.ATTENDEE_EMAIL)
            val idNS = row.getAsString(Attendees.ATTENDEE_ID_NAMESPACE)
            val id = row.getAsString(Attendees.ATTENDEE_IDENTITY)

            if (idNS != null || id != null) {
                // attendee identified by namespace and ID
                attendee = Attendee(URI(idNS, id, null))
                email?.let { attendee.parameters.add(Email(it)) }
            } else
                // attendee identified by email address
                attendee = Attendee(URI("mailto", email, null))
            val params = attendee.parameters

            // always add RSVP (offer attendees to accept/decline)
            params.add(Rsvp.TRUE)

            row.getAsString(Attendees.ATTENDEE_NAME)?.let { cn -> params.add(Cn(cn)) }

            // type/relation mapping is complex and thus outsourced to AttendeeMappings
            AttendeeMappings.androidToICalendar(row, attendee)

            // status
            when (row.getAsInteger(Attendees.ATTENDEE_STATUS)) {
                Attendees.ATTENDEE_STATUS_INVITED -> params.add(PartStat.NEEDS_ACTION)
                Attendees.ATTENDEE_STATUS_ACCEPTED -> params.add(PartStat.ACCEPTED)
                Attendees.ATTENDEE_STATUS_DECLINED -> params.add(PartStat.DECLINED)
                Attendees.ATTENDEE_STATUS_TENTATIVE -> params.add(PartStat.TENTATIVE)
                Attendees.ATTENDEE_STATUS_NONE -> { /* no information, don't add PARTSTAT */ }
            }

            event!!.attendees.add(attendee)
        } catch (e: URISyntaxException) {
            logger.log(Level.WARNING, "Couldn't parse attendee information, ignoring", e)
        }
    }

    private fun populateReminder(row: ContentValues) {
        logger.log(Level.FINE, "Read event reminder from calender provider", row)
        val event = requireNotNull(event)

        val alarm = VAlarm(Duration.ofMinutes(-row.getAsLong(Reminders.MINUTES)))

        val props = alarm.properties
        when (row.getAsInteger(Reminders.METHOD)) {
            Reminders.METHOD_EMAIL -> {
                val accountName = calendar.account.name
                if (Patterns.EMAIL_ADDRESS.matcher(accountName).matches()) {
                    props += Action.EMAIL
                    // ACTION:EMAIL requires SUMMARY, DESCRIPTION, ATTENDEE
                    props += Summary(event.summary)
                    props += Description(event.description ?: event.summary)
                    // Android doesn't allow to save email reminder recipients, so we always use the
                    // account name (should be account owner's email address)
                    props += Attendee(URI("mailto", calendar.account.name, null))
                } else {
                    logger.warning("Account name is not an email address; changing EMAIL reminder to DISPLAY")
                    props += Action.DISPLAY
                    props += Description(event.summary)
                }
            }

            // default: set ACTION:DISPLAY (requires DESCRIPTION)
            else -> {
                props += Action.DISPLAY
                props += Description(event.summary)
            }
        }
        event.alarms += alarm
    }

    private fun populateExtended(row: ContentValues) {
        val name = row.getAsString(ExtendedProperties.NAME)
        val rawValue = row.getAsString(ExtendedProperties.VALUE)
        logger.log(Level.FINE, "Read extended property from calender provider", arrayOf(name, rawValue))
        val event = requireNotNull(event)

        try {
            when (name) {
                EXTNAME_CATEGORIES ->
                    event.categories += rawValue.split(CATEGORIES_SEPARATOR)

                EXTNAME_URL ->
                    try {
                        event.url = URI(rawValue)
                    } catch(_: URISyntaxException) {
                        logger.warning("Won't process invalid local URL: $rawValue")
                    }

                EXTNAME_ICAL_UID ->
                    // only consider iCalUid when there's no uid
                    if (event.uid == null)
                        event.uid = rawValue

                UnknownProperty.CONTENT_ITEM_TYPE ->
                    event.unknownProperties += UnknownProperty.fromJsonString(rawValue)
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't parse extended property", e)
        }
    }

    private fun populateExceptions() {
        requireNotNull(id)
        val event = requireNotNull(event)

        calendar.client.query(Events.CONTENT_URI.asSyncAdapter(calendar.account),
                null,
                Events.ORIGINAL_ID + "=?", arrayOf(id.toString()), null)?.use { c ->
            while (c.moveToNext()) {
                val values = c.toContentValues()
                try {
                    val exception = AndroidEvent(calendar, values)
                    val exceptionEvent = exception.event!!
                    val recurrenceId = exceptionEvent.recurrenceId!!

                    // generate EXDATE instead of RECURRENCE-ID exceptions for cancelled instances
                    if (exceptionEvent.status == Status.VEVENT_CANCELLED) {
                        val list = DateList(
                                if (DateUtils.isDate(recurrenceId)) Value.DATE else Value.DATE_TIME,
                                recurrenceId.timeZone
                        )
                        list.add(recurrenceId.date)
                        event.exDates += ExDate(list).apply {
                            if (DateUtils.isDateTime(recurrenceId)) {
                                if (recurrenceId.isUtc)
                                    setUtc(true)
                                else
                                    timeZone = recurrenceId.timeZone
                            }
                        }

                    } else /* exceptionEvent.status != Status.VEVENT_CANCELLED */ {
                        // make sure that all components have the same ORGANIZER [RFC 6638 3.1]
                        exceptionEvent.organizer = event.organizer

                        // add exception to list of exceptions
                        event.exceptions += exceptionEvent
                    }
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Couldn't find exception details", e)
                }
            }
        }
    }

    private fun retainClassification() {
        /* retain classification other than PUBLIC and PRIVATE as unknown property so
           that it can be reused when "server default" is selected */
        val event = requireNotNull(event)
        event.classification?.let {
            if (it != Clazz.PUBLIC && it != Clazz.PRIVATE)
                event.unknownProperties += it
        }
    }


    /**
     * Saves an unsaved event into the calendar storage.
     *
     * @return content URI of the created event
     *
     * @throws LocalStorageException when the calendar provider doesn't return a result row
     * @throws RemoteException on calendar provider errors
     */
    fun add(): Uri {
        val batch = CalendarBatchOperation(calendar.client)

        val builder = AndroidEventBuilder(calendar, event!!, id, syncId, eTag, scheduleTag, flags)
        val idxEvent = builder.addOrUpdateRows(event!!, batch) ?: throw AssertionError("Expected Events._ID backref")
        batch.commit()

        val resultUri = batch.getResult(idxEvent)?.uri
            ?: throw LocalStorageException("Empty result from content provider when adding event")
        id = ContentUris.parseId(resultUri)
        return resultUri
    }

    /**
     * Updates an already existing event in the calendar storage with the values
     * from the instance.
     * @throws LocalStorageException when the calendar provider doesn't return a result row
     * @throws RemoteException on calendar provider errors
     */
    fun update(event: Event): Uri {
        this.event = event
        val existingId = requireNotNull(id)

        // There are cases where the event cannot be updated, but must be completely re-created.
        // Case 1: Events.STATUS shall be updated from a non-null value (like STATUS_CONFIRMED) to null.
        var rebuild = false
        if (event.status == null)
            calendar.client.query(eventSyncURI(), arrayOf(Events.STATUS), null, null, null)?.use { cursor ->
                if (cursor.moveToNext()) {
                    val statusIndex = cursor.getColumnIndexOrThrow(Events.STATUS)
                    if (!cursor.isNull(statusIndex))
                        rebuild = true
                }
            }

        if (rebuild) {  // delete whole event and insert updated event
            delete()
            return add()

        } else {        // update event
            // remove associated rows which are added later again
            val batch = CalendarBatchOperation(calendar.client)
            deleteExceptions(batch)
            batch += CpoBuilder
                .newDelete(Reminders.CONTENT_URI.asSyncAdapter(calendar.account))
                .withSelection("${Reminders.EVENT_ID}=?", arrayOf(existingId.toString()))
            batch += CpoBuilder
                .newDelete(Attendees.CONTENT_URI.asSyncAdapter(calendar.account))
                .withSelection("${Attendees.EVENT_ID}=?", arrayOf(existingId.toString()))
            batch += CpoBuilder
                .newDelete(ExtendedProperties.CONTENT_URI.asSyncAdapter(calendar.account))
                .withSelection(
                    "${ExtendedProperties.EVENT_ID}=? AND ${ExtendedProperties.NAME} IN (?,?,?,?)",
                    arrayOf(
                        existingId.toString(),
                        EXTNAME_CATEGORIES,
                        EXTNAME_ICAL_UID,       // UID is stored in UID_2445, don't leave iCalUid rows in events that we have written
                        EXTNAME_URL,
                        UnknownProperty.CONTENT_ITEM_TYPE
                    )
                )

            val builder = AndroidEventBuilder(calendar, event, id, syncId, eTag, scheduleTag, flags)
            builder.addOrUpdateRows(event, batch)
            batch.commit()

            return ContentUris.withAppendedId(Events.CONTENT_URI, existingId)
        }
    }

    fun update(values: ContentValues) {
        calendar.client.update(eventSyncURI(), values, null, null)
    }

    /**
     * Deletes an existing event from the calendar storage.
     *
     * @return number of affected rows
     *
     * @throws RemoteException on calendar provider errors
     */
    fun delete(): Int {
        val batch = CalendarBatchOperation(calendar.client)

        // remove exceptions of event, too (CalendarProvider doesn't do this)
        deleteExceptions(batch)

        // remove event and unset known id
        batch += CpoBuilder.newDelete(eventSyncURI())
        id = null

        return batch.commit()
    }

    private fun deleteExceptions(batch: CalendarBatchOperation) {
        val existingId = requireNotNull(id)
        batch += CpoBuilder
            .newDelete(Events.CONTENT_URI.asSyncAdapter(calendar.account))
            .withSelection("${Events.ORIGINAL_ID}=?", arrayOf(existingId.toString()))
    }


    private fun useRetainedClassification() {
        val event = requireNotNull(event)

        var retainedClazz: Clazz? = null
        val it = event.unknownProperties.iterator()
        while (it.hasNext()) {
            val prop = it.next()
            if (prop is Clazz) {
                retainedClazz = prop
                it.remove()
            }
        }

        if (event.classification == null)
            // no classification, use retained one if possible
            event.classification = retainedClazz
    }


    private fun eventSyncURI(): Uri {
        val id = requireNotNull(id)
        return ContentUris.withAppendedId(Events.CONTENT_URI, id).asSyncAdapter(calendar.account)
    }

    override fun toString(): String = "AndroidEvent(calendar=$calendar, id=$id, event=$_event)"


    companion object {

        const val MUTATORS_SEPARATOR = ','

        /**
         * Custom sync column to store the last known ETag of an event.
         */
        const val COLUMN_ETAG = Events.SYNC_DATA1

        /**
         * Custom sync column to store sync flags of an event.
         */
        const val COLUMN_FLAGS = Events.SYNC_DATA2

        /**
         * Custom sync column to store the SEQUENCE of an event.
         */
        const val COLUMN_SEQUENCE = Events.SYNC_DATA3

        /**
         * Custom sync column to store the Schedule-Tag of an event.
         */
        const val COLUMN_SCHEDULE_TAG = Events.SYNC_DATA4

        /**
         * VEVENT CATEGORIES are stored as an extended property with this [ExtendedProperties.NAME].
         *
         * The [ExtendedProperties.VALUE] format is the same as used by the AOSP Exchange ActiveSync adapter:
         * the category values are stored as list, separated by [CATEGORIES_SEPARATOR]. (If a category
         * value contains [CATEGORIES_SEPARATOR], [CATEGORIES_SEPARATOR] will be dropped.)
         *
         * Example: `Cat1\Cat2`
         */
        const val EXTNAME_CATEGORIES = "categories"
        const val CATEGORIES_SEPARATOR = '\\'

        /**
         * Google Calendar uses an extended property called `iCalUid` for storing the event's UID, instead of the
         * standard [Events.UID_2445].
         *
         * @see <a href="https://github.com/bitfireAT/ical4android/issues/125">GitHub Issue</a>
         */
        const val EXTNAME_ICAL_UID = "iCalUid"

        /**
         * VEVENT URL is stored as an extended property with this [ExtendedProperties.NAME].
         * The URL is directly put into [ExtendedProperties.VALUE].
         */
        const val EXTNAME_URL = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.ical4android.url"


        // helpers

        /**
         * Marks the event as deleted
         * @param eventID
         */
        fun markAsDeleted(provider: ContentProviderClient, account: Account, eventID: Long) {
            provider.update(
                ContentUris.withAppendedId(
                    Events.CONTENT_URI,
                    eventID
                ).asSyncAdapter(account),
                contentValuesOf(Events.DELETED to 1),
                null, null
            )
        }

        /**
         * Finds the amount of direct instances this event has (without exceptions); used by [numInstances]
         * to find the number of instances of exceptions.
         *
         * The number of returned instances may vary with the Android version.
         *
         * @return number of direct event instances (not counting instances of exceptions); *null* if
         * the number can't be determined or if the event has no last date (recurring event without last instance)
         */
        fun numDirectInstances(provider: ContentProviderClient, account: Account, eventID: Long): Int? {
            // query event to get first and last instance
            var first: Long? = null
            var last: Long? = null
            provider.query(
                ContentUris.withAppendedId(
                    Events.CONTENT_URI,
                    eventID
                ),
                arrayOf(Events.DTSTART, Events.LAST_DATE), null, null, null
            )?.use { cursor ->
                cursor.moveToNext()
                if (!cursor.isNull(0))
                    first = cursor.getLong(0)
                if (!cursor.isNull(1))
                    last = cursor.getLong(1)
            }
            // if this event doesn't have a last occurence, it's endless and always has instances
            if (first == null || last == null)
                return null

            /* We can't use Long.MIN_VALUE and Long.MAX_VALUE because Android generates the instances
             on the fly and it doesn't accept those values. So we use the first/last actual occurence
             of the event (calculated by Android). */
            val instancesUri = CalendarContract.Instances.CONTENT_URI.asSyncAdapter(account)
                .buildUpon()
                .appendPath(first.toString())       // begin timestamp
                .appendPath(last.toString())        // end timestamp
                .build()

            var numInstances = 0
            provider.query(
                instancesUri, null,
                "${CalendarContract.Instances.EVENT_ID}=?", arrayOf(eventID.toString()),
                null
            )?.use { cursor ->
                numInstances += cursor.count
            }
            return numInstances
        }

        /**
         * Finds the total number of instances this event has (including instances of exceptions)
         *
         * The number of returned instances may vary with the Android version.
         *
         * @return number of direct event instances (not counting instances of exceptions); *null* if
         * the number can't be determined or if the event has no last date (recurring event without last instance)
         */
        fun numInstances(provider: ContentProviderClient, account: Account, eventID: Long): Int? {
            // num instances of the main event
            var numInstances = numDirectInstances(provider, account, eventID) ?: return null

            // add the number of instances of every main event's exception
            provider.query(
                Events.CONTENT_URI,
                arrayOf(Events._ID),
                "${Events.ORIGINAL_ID}=?", // get exception events of the main event
                arrayOf("$eventID"), null
            )?.use { exceptionsEventCursor ->
                while (exceptionsEventCursor.moveToNext()) {
                    val exceptionEventID = exceptionsEventCursor.getLong(0)
                    val exceptionInstances = numDirectInstances(provider, account, exceptionEventID)

                    if (exceptionInstances == null)
                    // number of instances of exception can't be determined; so the total number of instances is also unclear
                        return null

                    numInstances += exceptionInstances
                }
            }
            return numInstances
        }

    }

}