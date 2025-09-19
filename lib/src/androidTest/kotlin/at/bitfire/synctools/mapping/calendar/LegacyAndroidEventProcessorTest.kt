/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL
import android.provider.CalendarContract.AUTHORITY
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Reminders
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.LegacyAndroidCalendar
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import at.bitfire.synctools.test.InitCalendarProviderRule
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.XProperty
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import java.net.URI
import java.time.Duration

/**
 * Tests mapping from [at.bitfire.synctools.storage.calendar.EventAndExceptions] to [Event].
 */
class LegacyAndroidEventProcessorTest {

    @get:Rule
    val initCalendarProviderRule: TestRule = InitCalendarProviderRule.initialize()

    private val testAccount = Account("${javaClass.name}@example.com", ACCOUNT_TYPE_LOCAL)
    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")!!
    private val tzShanghai = tzRegistry.getTimeZone("Asia/Shanghai")!!

    private lateinit var calendar: AndroidCalendar
    lateinit var legacyCalendar: LegacyAndroidCalendar
    lateinit var client: ContentProviderClient

    @Before
    fun prepare() {
        val context = getInstrumentation().targetContext
        client = context.contentResolver.acquireContentProviderClient(AUTHORITY)!!

        calendar = TestCalendar.findOrCreate(testAccount, client)
        legacyCalendar = LegacyAndroidCalendar(calendar)
    }

    @After
    fun shutdown() {
        client.closeCompat()
        calendar.delete()
    }


    private fun populateAndroidEvent(
        automaticDates: Boolean,
        destinationCalendar: AndroidCalendar = calendar,
        asSyncAdapter: Boolean = false,
        insertCallback: (id: Long) -> Unit = {},
        extendedProperties: Map<String, String> = emptyMap(),
        valuesBuilder: ContentValues.() -> Unit = {}
    ): AndroidEvent2 {
        val values = ContentValues()
        values.put(Events.CALENDAR_ID, destinationCalendar.id)
        if (automaticDates) {
            values.put(Events.DTSTART, 1592733600000L)  // 21/06/2020 12:00 +0200
            values.put(Events.EVENT_TIMEZONE, "Europe/Berlin")
            values.put(Events.DTEND, 1592742600000L)    // 21/06/2020 14:30 +0200
            values.put(Events.EVENT_END_TIMEZONE, "Europe/Berlin")
        }
        valuesBuilder(values)
        val uri = client.insert(
            if (asSyncAdapter)
                Events.CONTENT_URI.asSyncAdapter(testAccount)
            else
                Events.CONTENT_URI,
            values)!!
        val id = ContentUris.parseId(uri)

        // insert additional rows etc.
        insertCallback(id)

        // insert extended properties
        for ((name, value) in extendedProperties) {
            val extendedValues = contentValuesOf(
                ExtendedProperties.EVENT_ID to id,
                ExtendedProperties.NAME to name,
                ExtendedProperties.VALUE to value
            )
            client.insert(ExtendedProperties.CONTENT_URI.asSyncAdapter(testAccount), extendedValues)
        }

        return calendar.getEvent(id)!!
    }

    private fun populateEvent(
        automaticDates: Boolean,
        destinationCalendar: AndroidCalendar = calendar,
        asSyncAdapter: Boolean = false,
        insertCallback: (id: Long) -> Unit = {},
        extendedProperties: Map<String, String> = emptyMap(),
        valuesBuilder: ContentValues.() -> Unit = {}
    ): Event {
        val androidEvent = populateAndroidEvent(
            automaticDates,
            destinationCalendar,
            asSyncAdapter,
            insertCallback,
            extendedProperties,
            valuesBuilder
        )

        // LegacyAndroidEventProcessor.populate() is called here:
        return LegacyAndroidCalendar(destinationCalendar).getEvent(androidEvent.id)!!
    }


    @Test
    fun testPopulateEvent_Sequence_Int() {
        populateEvent(true, asSyncAdapter = true) {
            put(AndroidEvent2.COLUMN_SEQUENCE, 5)
        }.let { result ->
            assertEquals(5, result.sequence)
        }
    }

    @Test
    fun testPopulateEvent_Sequence_Null() {
        populateEvent(true, asSyncAdapter = true) {
            putNull(AndroidEvent2.COLUMN_SEQUENCE)
        }.let { result ->
            assertNull(result.sequence)
        }
    }

    @Test
    fun testPopulateEvent_IsOrganizer_False() {
        populateEvent(true, asSyncAdapter = true) {
            put(Events.IS_ORGANIZER, "0")
        }.let { result ->
            assertFalse(result.isOrganizer!!)
        }
    }

    @Test
    fun testPopulateEvent_IsOrganizer_Null() {
        populateEvent(true, asSyncAdapter = true) {
            putNull(Events.IS_ORGANIZER)
        }.let { result ->
            assertNull(result.isOrganizer)
        }
    }

    @Test
    fun testPopulateEvent_IsOrganizer_True() {
        populateEvent(true, asSyncAdapter = true) {
            put(Events.IS_ORGANIZER, "1")
        }.let { result ->
            assertTrue(result.isOrganizer!!)
        }
    }

    @Test
    fun testPopulateEvent_NonAllDay_NonRecurring() {
        populateEvent(false) {
            put(Events.DTSTART, 1592733600000L)  // 21/06/2020 12:00 +0200
            put(Events.EVENT_TIMEZONE, "Europe/Vienna")
            put(Events.DTEND, 1592742600000L)    // 21/06/2020 14:30 +0200
            put(Events.EVENT_END_TIMEZONE, "Europe/Vienna")
        }.let { result ->
            assertEquals(DtStart(DateTime("20200621T120000", tzVienna)), result.dtStart)
            assertEquals(DtEnd(DateTime("20200621T143000", tzVienna)), result.dtEnd)
            assertNull(result.duration)
        }
    }

    @Test
    fun testPopulateEvent_NonAllDay_NonRecurring_MixedZones() {
        populateEvent(false) {
            put(Events.DTSTART, 1592733600000L)  // 21/06/2020 18:00 +0800
            put(Events.EVENT_TIMEZONE, "Asia/Shanghai")
            put(Events.DTEND, 1592742600000L)    // 21/06/2020 14:30 +0200
            put(Events.EVENT_END_TIMEZONE, "Europe/Vienna")
        }.let { result ->
            assertEquals(DtStart(DateTime("20200621T180000", tzShanghai)), result.dtStart)
            assertEquals(DtEnd(DateTime("20200621T143000", tzVienna)), result.dtEnd)
            assertNull(result.duration)
        }
    }

    @Test
    fun testPopulateEvent_NonAllDay_NonRecurring_Duration() {
        /* This should not happen, because according to the documentation, non-recurring events MUST
        have a dtEnd. However, the calendar provider doesn't enforce this for non-sync-adapters. */
        populateEvent(false, asSyncAdapter = false) {
            put(Events.DTSTART, 1592733600000L)  // 21/06/2020 18:00 +0800
            put(Events.EVENT_TIMEZONE, "Asia/Shanghai")
            put(Events.DURATION, "PT1H")
        }.let { result ->
            assertEquals(DtStart(DateTime("20200621T180000", tzShanghai)), result.dtStart)
            assertEquals(DtEnd(DateTime("20200621T190000", tzShanghai)), result.dtEnd)
            assertNull(result.duration)
        }
    }

    @Test
    fun testPopulateEvent_NonAllDay_Recurring_Duration_KievTimeZone() {
        populateEvent(false) {
            put(Events.DTSTART, 1592733600000L)  // 21/06/2020 18:00 +0800
            put(Events.EVENT_TIMEZONE, "Europe/Kiev")
            put(Events.DURATION, "PT1H")
            put(Events.RRULE, "FREQ=DAILY;COUNT=2")
        }.let { result ->
            assertEquals(1592733600000L, result.dtStart?.date?.time)
            assertEquals(1592733600000L + 3600000, result.dtEnd?.date?.time)
            assertEquals("Europe/Kiev", result.dtStart?.timeZone?.id)
            assertEquals("Europe/Kiev", result.dtEnd?.timeZone?.id)
        }
    }

    @Test
    fun testPopulateEvent_NonAllDay_NonRecurring_NoTime() {
        populateEvent(false) {
            put(Events.DTSTART, 1592742600000L)  // 21/06/2020 14:30 +0200
            put(Events.EVENT_TIMEZONE, "Europe/Vienna")
            put(Events.DTEND, 1592742600000L)    // 21/06/2020 14:30 +0200
            put(Events.EVENT_END_TIMEZONE, "Europe/Vienna")
        }.let { result ->
            assertEquals(DtStart(DateTime("20200621T143000", tzVienna)), result.dtStart)
            //assertNull(result.dtEnd)
            assertEquals(result.dtEnd!!.date, result.dtStart!!.date)
            assertNull(result.duration)
        }
    }

    @Test
    fun testPopulateEvent_AllDay_NonRecurring_NoTime() {
        populateEvent(false) {
            put(Events.ALL_DAY, 1)
            put(Events.DTSTART, 1592697600000L)  // 21/06/2020
            put(Events.EVENT_TIMEZONE, AndroidTimeUtils.TZID_ALLDAY)
            put(Events.DTEND, 1592697600000L)    // 21/06/2020
            put(Events.EVENT_END_TIMEZONE, AndroidTimeUtils.TZID_ALLDAY)
        }.let { result ->
            assertEquals(DtStart(Date("20200621")), result.dtStart)
            assertNull(result.dtEnd)
            assertNull(result.duration)
        }
    }

    @Test
    fun testPopulateEvent_AllDay_NonRecurring_1Day() {
        populateEvent(false) {
            put(Events.ALL_DAY, 1)
            put(Events.DTSTART, 1592697600000L)  // 21/06/2020
            put(Events.EVENT_TIMEZONE, AndroidTimeUtils.TZID_ALLDAY)
            put(Events.DTEND, 1592784000000L)    // 22/06/2020
            put(Events.EVENT_END_TIMEZONE, AndroidTimeUtils.TZID_ALLDAY)
        }.let { result ->
            assertEquals(DtStart(Date("20200621")), result.dtStart)
            assertEquals(DtEnd(Date("20200622")), result.dtEnd)
            assertNull(result.duration)
        }
    }

    @Test
    fun testPopulateEvent_AllDay_NonRecurring_AllDayDuration() {
        /* This should not happen, because according to the documentation, non-recurring events MUST
        have a dtEnd. However, the calendar provider doesn't enforce this for non-sync-adapters. */
        populateEvent(false, asSyncAdapter = false) {
            put(Events.ALL_DAY, 1)
            put(Events.DTSTART, 1592697600000L)  // 21/06/2020
            put(Events.EVENT_TIMEZONE, AndroidTimeUtils.TZID_ALLDAY)
            put(Events.DURATION, "P1W")
        }.let { result ->
            assertEquals(DtStart(Date("20200621")), result.dtStart)
            assertEquals(DtEnd(Date("20200628")), result.dtEnd)
            assertNull(result.duration)
        }
    }

    @Test
    fun testPopulateEvent_AllDay_NonRecurring_NonAllDayDuration_LessThanOneDay() {
        /* This should not happen, because according to the documentation, non-recurring events MUST
        have a dtEnd. However, the calendar provider doesn't enforce this for non-sync-adapters. */
        populateEvent(false, asSyncAdapter = false) {
            put(Events.ALL_DAY, 1)
            put(Events.DTSTART, 1592697600000L)  // 21/06/2020
            put(Events.EVENT_TIMEZONE, AndroidTimeUtils.TZID_ALLDAY)
            put(Events.DURATION, "PT1H30M")
        }.let { result ->
            assertEquals(DtStart(Date("20200621")), result.dtStart)
            assertNull(result.dtEnd)
            assertNull(result.duration)
        }
    }

    @Test
    fun testPopulateEvent_AllDay_NonRecurring_NonAllDayDuration_MoreThanOneDay() {
        /* This should not happen, because according to the documentation, non-recurring events MUST
        have a dtEnd. However, the calendar provider doesn't enforce this for non-sync-adapters. */
        populateEvent(false, asSyncAdapter = false) {
            put(Events.ALL_DAY, 1)
            put(Events.DTSTART, 1592697600000L)  // 21/06/2020
            put(Events.EVENT_TIMEZONE, AndroidTimeUtils.TZID_ALLDAY)
            put(Events.DURATION, "PT49H2M")
        }.let { result ->
            assertEquals(DtStart(Date("20200621")), result.dtStart)
            assertEquals(DtEnd(Date("20200623")), result.dtEnd)
            assertNull(result.duration)
        }
    }

    @Test
    fun testPopulateEvent_Summary() {
        populateEvent(true) {
            put(Events.TITLE, "Sample Title")
        }.let { result ->
            assertEquals("Sample Title", result.summary)
        }
    }

    @Test
    fun testPopulateEvent_Location() {
        populateEvent(true) {
            put(Events.EVENT_LOCATION, "Sample Location")
        }.let { result ->
            assertEquals("Sample Location", result.location)
        }
    }

    @Test
    fun testPopulateEvent_Url() {
        populateEvent(true,
            extendedProperties = mapOf(AndroidEvent2.EXTNAME_URL to "https://example.com")
        ).let { result ->
            assertEquals(URI("https://example.com"), result.url)
        }
    }

    @Test
    fun testPopulateEvent_Description() {
        populateEvent(true) {
            put(Events.DESCRIPTION, "Sample Description")
        }.let { result ->
            assertEquals("Sample Description", result.description)
        }
    }

    @Test
    fun testPopulateEvent_Color_FromIndex() {
        val provider = AndroidCalendarProvider(testAccount, client)
        provider.provideCss3ColorIndices()
        populateEvent(true) {
            put(Events.EVENT_COLOR_KEY, Css3Color.silver.name)
        }.let { result ->
            assertEquals(Css3Color.silver, result.color)
        }
    }

    @Test
    fun testPopulateEvent_Color_FromValue() {
        populateEvent(true) {
            put(Events.EVENT_COLOR, Css3Color.silver.argb)
        }.let { result ->
            assertEquals(Css3Color.silver, result.color)
        }
    }

    @Test
    fun testPopulateEvent_Status_Confirmed() {
        populateEvent(true) {
            put(Events.STATUS, Events.STATUS_CONFIRMED)
        }.let { result ->
            assertEquals(Status.VEVENT_CONFIRMED, result.status)
        }
    }

    @Test
    fun testPopulateEvent_Status_Tentative() {
        populateEvent(true) {
            put(Events.STATUS, Events.STATUS_TENTATIVE)
        }.let { result ->
            assertEquals(Status.VEVENT_TENTATIVE, result.status)
        }
    }

    @Test
    fun testPopulateEvent_Status_Cancelled() {
        populateEvent(true) {
            put(Events.STATUS, Events.STATUS_CANCELED)
        }.let { result ->
            assertEquals(Status.VEVENT_CANCELLED, result.status)
        }
    }

    @Test
    fun testPopulateEvent_Status_None() {
        assertNull(populateEvent(true).status)
    }

    @Test
    fun testPopulateEvent_Availability_Busy() {
        populateEvent(true) {
            put(Events.AVAILABILITY, Events.AVAILABILITY_BUSY)
        }.let { result ->
            assertTrue(result.opaque)
        }
    }

    @Test
    fun testPopulateEvent_Availability_Tentative() {
        populateEvent(true) {
            put(Events.AVAILABILITY, Events.AVAILABILITY_TENTATIVE)
        }.let { result ->
            assertTrue(result.opaque)
        }
    }

    @Test
    fun testPopulateEvent_Availability_Free() {
        populateEvent(true) {
            put(Events.AVAILABILITY, Events.AVAILABILITY_FREE)
        }.let { result ->
            assertFalse(result.opaque)
        }
    }

    @Test
    fun testPopulateEvent_Organizer_NotGroupScheduled() {
        assertNull(populateEvent(true).organizer)
    }

    @Test
    fun testPopulateEvent_Organizer_NotGroupScheduled_ExplicitOrganizer() {
        populateEvent(true) {
            put(Events.ORGANIZER, "sample@example.com")
        }.let { result ->
            assertNull(result.organizer)
        }
    }

    @Test
    fun testPopulateEvent_Organizer_GroupScheduled() {
        populateEvent(true, insertCallback = { id ->
            client.insert(Attendees.CONTENT_URI.asSyncAdapter(testAccount), ContentValues().apply {
                put(Attendees.EVENT_ID, id)
                put(Attendees.ATTENDEE_EMAIL, "organizer@example.com")
                put(Attendees.ATTENDEE_TYPE, Attendees.RELATIONSHIP_ORGANIZER)
            })
        }) {
            put(Events.ORGANIZER, "organizer@example.com")
        }.let { result ->
            assertEquals("mailto:organizer@example.com", result.organizer?.value)
        }
    }

    @Test
    fun testPopulateEvent_Classification_Public() {
        populateEvent(true) {
            put(Events.ACCESS_LEVEL, Events.ACCESS_PUBLIC)
        }.let { result ->
            assertEquals(Clazz.PUBLIC, result.classification)
        }
    }

    @Test
    fun testPopulateEvent_Classification_Private() {
        populateEvent(true) {
            put(Events.ACCESS_LEVEL, Events.ACCESS_PRIVATE)
        }.let { result ->
            assertEquals(Clazz.PRIVATE, result.classification)
        }
    }

    @Test
    fun testPopulateEvent_Classification_Confidential() {
        populateEvent(true) {
            put(Events.ACCESS_LEVEL, Events.ACCESS_CONFIDENTIAL)
        }.let { result ->
            assertEquals(Clazz.CONFIDENTIAL, result.classification)
        }
    }

    @Test
    fun testPopulateEvent_Classification_Confidential_Retained() {
        populateEvent(true,
            extendedProperties = mapOf(UnknownProperty.CONTENT_ITEM_TYPE to UnknownProperty.toJsonString(Clazz.CONFIDENTIAL))
        ) {
            put(Events.ACCESS_LEVEL, Events.ACCESS_DEFAULT)
        }.let { result ->
            assertEquals(Clazz.CONFIDENTIAL, result.classification)
        }
    }

    @Test
    fun testPopulateEvent_Classification_Default() {
        populateEvent(true) {
            put(Events.ACCESS_LEVEL, Events.ACCESS_DEFAULT)
        }.let { result ->
            assertNull(result.classification)
        }
    }

    @Test
    fun testPopulateEvent_Classification_Custom() {
        populateEvent(
            true,
            valuesBuilder = {
                put(Events.ACCESS_LEVEL, Events.ACCESS_DEFAULT)
            },
            extendedProperties = mapOf(
                UnknownProperty.CONTENT_ITEM_TYPE to UnknownProperty.toJsonString(Clazz("TOP-SECRET"))
            )
        ).let { result ->
            assertEquals(Clazz("TOP-SECRET"), result.classification)
        }
    }

    @Test
    fun testPopulateEvent_Classification_None() {
        populateEvent(true) {
        }.let { result ->
            assertNull(result.classification)
        }
    }


    private fun populateReminder(destinationCalendar: AndroidCalendar = calendar, builder: ContentValues.() -> Unit): VAlarm? {
        populateEvent(true, destinationCalendar = destinationCalendar, insertCallback = { id ->
            val reminderValues = ContentValues()
            reminderValues.put(Reminders.EVENT_ID, id)
            builder(reminderValues)
            client.insert(Reminders.CONTENT_URI.asSyncAdapter(testAccount), reminderValues)
        }).let { result ->
            return result.alarms.firstOrNull()
        }
    }

    @Test
    fun testPopulateReminder_TypeEmail_AccountNameEmail() {
        // account name looks like an email address
        assumeTrue(testAccount.name.endsWith("@example.com"))

        populateReminder {
            put(Reminders.METHOD, Reminders.METHOD_EMAIL)
            put(Reminders.MINUTES, 10)
        }!!.let { alarm ->
            assertEquals(Action.EMAIL, alarm.action)
            assertNotNull(alarm.summary)
            assertNotNull(alarm.description)
        }
    }

    @Test
    fun testPopulateReminder_TypeEmail_AccountNameNotEmail() {
        // test account name that doesn't look like an email address
        val nonEmailAccount = Account("ical4android", ACCOUNT_TYPE_LOCAL)
        val testCalendar = TestCalendar.findOrCreate(nonEmailAccount, client)
        try {
            populateReminder(testCalendar) {
                put(Reminders.METHOD, Reminders.METHOD_EMAIL)
            }!!.let { alarm ->
                assertEquals(Action.DISPLAY, alarm.action)
                assertNotNull(alarm.description)
            }
        } finally {
            testCalendar.delete()
        }
    }

    @Test
    fun testPopulateReminder_TypeNotEmail() {
        for (type in arrayOf(null, Reminders.METHOD_ALARM, Reminders.METHOD_ALERT, Reminders.METHOD_DEFAULT, Reminders.METHOD_SMS))
            populateReminder {
                put(Reminders.METHOD, type)
                put(Reminders.MINUTES, 10)
            }!!.let { alarm ->
                assertEquals(Action.DISPLAY, alarm.action)
                assertNotNull(alarm.description)
            }
    }

    @Test
    fun testPopulateReminder_Minutes_Positive() {
        populateReminder {
            put(Reminders.METHOD, Reminders.METHOD_ALERT)
            put(Reminders.MINUTES, 10)
        }!!.let { alarm ->
            assertEquals(Duration.ofMinutes(-10), alarm.trigger.duration)
        }
    }

    @Test
    fun testPopulateReminder_Minutes_Negative() {
        populateReminder {
            put(Reminders.METHOD, Reminders.METHOD_ALERT)
            put(Reminders.MINUTES, -10)
        }!!.let { alarm ->
            assertEquals(Duration.ofMinutes(10), alarm.trigger.duration)
        }
    }


    @Test
    fun testPopulateUnknownProperty() {
        val params = ParameterList()
        params.add(Language("en"))
        val unknownProperty = XProperty("X-NAME", params, "Custom Value")
        val (result) = populateEvent(
            true,
            extendedProperties = mapOf(
                UnknownProperty.CONTENT_ITEM_TYPE to UnknownProperty.toJsonString(unknownProperty)
            )
        ).unknownProperties
        assertEquals("X-NAME", result.name)
        assertEquals("en", result.getParameter<Language>(Parameter.LANGUAGE).value)
        assertEquals("Custom Value", result.value)
    }


    private fun populateException(mainBuilder: ContentValues.() -> Unit, exceptionBuilder: ContentValues.() -> Unit) =
        populateEvent(false, asSyncAdapter = true, valuesBuilder = mainBuilder, insertCallback = { id ->
            val exceptionValues = ContentValues()
            exceptionValues.put(Events.CALENDAR_ID, calendar.id)
            exceptionBuilder(exceptionValues)
            client.insert(Events.CONTENT_URI.asSyncAdapter(testAccount), exceptionValues)
        })

    @Test
    fun testPopulateException_NonAllDay() {
        populateException({
            put(Events._SYNC_ID, "testPopulateException_NonAllDay")
            put(Events.TITLE, "Recurring non-all-day event with exception")
            put(Events.DTSTART, 1594056600000L)
            put(Events.EVENT_TIMEZONE, tzVienna.id)
            put(Events.ALL_DAY, 0)
            put(Events.RRULE, "FREQ=DAILY;COUNT=10")
        }, {
            put(Events.ORIGINAL_SYNC_ID, "testPopulateException_NonAllDay")
            put(Events.ORIGINAL_INSTANCE_TIME, 1594143000000L)
            put(Events.ORIGINAL_ALL_DAY, 0)
            put(Events.DTSTART, 1594038600000L)
            put(Events.EVENT_TIMEZONE, tzShanghai.id)
            put(Events.ALL_DAY, 0)
            put(Events.TITLE, "Event moved to one hour later")
        }).let { event ->
            assertEquals("Recurring non-all-day event with exception", event.summary)
            assertEquals(DtStart("20200706T193000", tzVienna), event.dtStart)
            assertEquals("FREQ=DAILY;COUNT=10", event.rRules.first().value)
            val exception = event.exceptions.first()
            assertEquals(RecurrenceId("20200708T013000", tzShanghai), exception.recurrenceId)
            assertEquals(DtStart("20200706T203000", tzShanghai), exception.dtStart)
            assertEquals("Event moved to one hour later", exception.summary)
        }
    }

    @Test
    fun testPopulateException_AllDay() {
        populateException({
            put(Events._SYNC_ID, "testPopulateException_AllDay")
            put(Events.TITLE, "Recurring all-day event with exception")
            put(Events.DTSTART, 1593993600000L)
            put(Events.EVENT_TIMEZONE, AndroidTimeUtils.TZID_ALLDAY)
            put(Events.ALL_DAY, 1)
            put(Events.RRULE, "FREQ=WEEKLY;COUNT=3")
        }, {
            put(Events.ORIGINAL_SYNC_ID, "testPopulateException_AllDay")
            put(Events.ORIGINAL_INSTANCE_TIME, 1594080000000L)
            put(Events.ORIGINAL_ALL_DAY, 1)
            put(Events.DTSTART, 1594031400000L)
            put(Events.ALL_DAY, 0)
            put(Events.EVENT_TIMEZONE, tzShanghai.id)
            put(Events.TITLE, "Today not an all-day event")
        }).let { event ->
            assertEquals("Recurring all-day event with exception", event.summary)
            assertEquals(DtStart(Date("20200706")), event.dtStart)
            assertEquals("FREQ=WEEKLY;COUNT=3", event.rRules.first().value)
            val exception = event.exceptions.first()
            assertEquals(RecurrenceId(Date("20200707")), exception.recurrenceId)
            assertEquals(DtStart("20200706T183000", tzShanghai), exception.dtStart)
            assertEquals("Today not an all-day event", exception.summary)
        }
    }

    @Test
    fun testPopulateException_Exdate() {
        populateException({
            put(Events._SYNC_ID, "testPopulateException_AllDay")
            put(Events.TITLE, "Recurring all-day event with cancelled exception")
            put(Events.DTSTART, 1594056600000L)
            put(Events.EVENT_TIMEZONE, tzVienna.id)
            put(Events.ALL_DAY, 0)
            put(Events.RRULE, "FREQ=DAILY;COUNT=10")
        }, {
            put(Events.ORIGINAL_SYNC_ID, "testPopulateException_AllDay")
            put(Events.ORIGINAL_INSTANCE_TIME, 1594143000000L)
            put(Events.ORIGINAL_ALL_DAY, 0)
            put(Events.DTSTART, 1594143000000L)
            put(Events.ALL_DAY, 0)
            put(Events.EVENT_TIMEZONE, tzShanghai.id)
            put(Events.STATUS, Events.STATUS_CANCELED)
        }).let { event ->
            assertEquals("Recurring all-day event with cancelled exception", event.summary)
            assertEquals(DtStart("20200706T193000", tzVienna), event.dtStart)
            assertEquals("FREQ=DAILY;COUNT=10", event.rRules.first().value)
            assertEquals(DateTime("20200708T013000", tzShanghai), event.exDates.first().dates.first())
            assertTrue(event.exceptions.isEmpty())
        }
    }

    @Test
    fun testPopulateException_Exdate_NoRecurrenceId() {
        populateException({
            put(Events._SYNC_ID, "testPopulateException_AllDay")
            put(Events.TITLE, "Recurring all-day event with cancelled exception and no RECURRENCE-ID")
            put(Events.DTSTART, 1594056600000L)
            put(Events.EVENT_TIMEZONE, tzVienna.id)
            put(Events.ALL_DAY, 0)
            put(Events.RRULE, "FREQ=DAILY;COUNT=10")
        }, {
            put(Events.ORIGINAL_SYNC_ID, "testPopulateException_AllDay")
            //put(Events.ORIGINAL_INSTANCE_TIME, 1594143000000L)
            put(Events.ORIGINAL_ALL_DAY, 0)
            put(Events.DTSTART, 1594143000000L)
            put(Events.ALL_DAY, 0)
            put(Events.EVENT_TIMEZONE, tzShanghai.id)
            put(Events.STATUS, Events.STATUS_CANCELED)
        }).let { event ->
            assertEquals("Recurring all-day event with cancelled exception and no RECURRENCE-ID", event.summary)
            assertEquals(DtStart("20200706T193000", tzVienna), event.dtStart)
            assertEquals("FREQ=DAILY;COUNT=10", event.rRules.first().value)
            assertTrue(event.exDates.isEmpty())
            assertTrue(event.exceptions.isEmpty())
        }
    }

}