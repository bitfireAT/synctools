/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.database.DatabaseUtils
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL
import android.provider.CalendarContract.AUTHORITY
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Reminders
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import at.bitfire.synctools.test.InitCalendarProviderRule
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.CuType
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.parameter.Role
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.*
import net.fortuna.ical4j.util.TimeZones
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TestRule
import java.net.URI
import java.time.Duration
import java.time.Period
import java.util.UUID
import java.util.logging.Logger
import kotlin.collections.plusAssign

class AndroidEventTest {

    companion object {

        @JvmField
        @ClassRule
        val initCalendarProviderRule: TestRule = InitCalendarProviderRule.initialize()

        lateinit var client: ContentProviderClient

        @BeforeClass
        @JvmStatic
        fun connectProvider() {
            client = getInstrumentation().targetContext.contentResolver.acquireContentProviderClient(AUTHORITY)!!
        }

        @AfterClass
        @JvmStatic
        fun closeProvider() {
            client.closeCompat()
        }

    }

    private val logger = Logger.getLogger(javaClass.name)

    private val testAccount = Account("ical4android@example.com", ACCOUNT_TYPE_LOCAL)

    private val tzVienna = DateUtils.ical4jTimeZone("Europe/Vienna")!!
    private val tzShanghai = DateUtils.ical4jTimeZone("Asia/Shanghai")!!

    private val tzIdDefault = java.util.TimeZone.getDefault().id
    private val tzDefault = DateUtils.ical4jTimeZone(tzIdDefault)

    private lateinit var calendarUri: Uri
    private lateinit var calendar: AndroidCalendar

    @Before
    fun prepare() {
        calendar = TestCalendar.findOrCreate(testAccount, client)
        assertNotNull(calendar)
        calendarUri = ContentUris.withAppendedId(Calendars.CONTENT_URI, calendar.id)
    }

    @After
    fun shutdown() {
        calendar.delete()
    }


    @Test
    fun testConstructor_ContentValues() {
        val e = AndroidEvent(
            calendar, contentValuesOf(
                Events._ID to 123,
                Events._SYNC_ID to "some-ical.ics",
                AndroidEvent.COLUMN_ETAG to "some-etag",
                AndroidEvent.COLUMN_SCHEDULE_TAG to "some-schedule-tag",
                AndroidEvent.COLUMN_FLAGS to 45
            )
        )
        assertEquals(123L, e.id)
        assertEquals("some-ical.ics", e.syncId)
        assertEquals("some-etag", e.eTag)
        assertEquals("some-schedule-tag", e.scheduleTag)
        assertEquals(45, e.flags)
    }


    /**
     * buildEvent() BASIC TEST MATRIX:
     *
     * all-day event | hasDtEnd | hasDuration | recurring event | notes
     *        0            0            0              0          dtEnd = dtStart
     *        0            0            0              1          duration = 0s, rRule/rDate set
     *        0            0            1              0          dtEnd calulcated from duration
     *        0            0            1              1
     *        0            1            0              0
     *        0            1            0              1          dtEnd calulcated from duration
     *        0            1            1              0          duration ignored
     *        0            1            1              1          duration ignored
     *        1            0            0              0          duration = 1d
     *        1            0            0              1          duration = 1d
     *        1            0            1              0          dtEnd calculated from duration
     *        1            0            1              1
     *        1            1            0              0
     *        1            1            0              1          duration calculated from dtEnd; ignore times in rDate
     *        1            1            1              0          duration ignored
     *        1            1            1              1          duration ignored
     *
     *  buildEvent() EXTRA TESTS:
     *
     *  - floating times
     *  - floating times in rdate/exdate
     *  - UTC times
     */

    private fun buildEvent(automaticDates: Boolean, eventBuilder: Event.() -> Unit): ContentValues {
        val event = Event().apply {
            if (automaticDates)
                dtStart = DtStart(DateTime())
            eventBuilder()
        }
        // write event with random file name/sync_id
        val uri = AndroidEvent(calendar, event, syncId = UUID.randomUUID().toString()).add()
        client.query(uri, null, null, null, null)!!.use { cursor ->
            cursor.moveToNext()
            val values = ContentValues(cursor.columnCount)
            DatabaseUtils.cursorRowToContentValues(cursor, values)
            return values
        }
    }

    private fun firstExtendedProperty(values: ContentValues): String? {
        val id = values.getAsInteger(Events._ID)
        client.query(ExtendedProperties.CONTENT_URI.asSyncAdapter(testAccount), arrayOf(ExtendedProperties.VALUE),
                "${ExtendedProperties.EVENT_ID}=?", arrayOf(id.toString()), null)?.use {
            if (it.moveToNext())
                return it.getString(0)
        }
        return null
    }

    private fun firstUnknownProperty(values: ContentValues): Property? {
        val rawValue = firstExtendedProperty(values)
        return if (rawValue != null)
            UnknownProperty.fromJsonString(rawValue)
        else
            null
    }

    @Test
    fun testBuildEvent_NonAllDay_NoDtEnd_NoDuration_NonRecurring() {
        val values = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzVienna)
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1591007400000L, values.getAsLong(Events.DTEND))
        assertEquals(tzVienna.id, values.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun testBuildEvent_NonAllDay_NoDtEnd_NoDuration_Recurring() {
        val values = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzVienna)
            rRules += RRule("FREQ=DAILY;COUNT=5")
            rRules += RRule("FREQ=WEEKLY;COUNT=10")
            rDates += RDate(DateList("20210601T123000", Value.DATE_TIME, tzVienna))
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals("P0D", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("FREQ=DAILY;COUNT=5\nFREQ=WEEKLY;COUNT=10", values.getAsString(Events.RRULE))
        assertEquals("${tzVienna.id};20200601T123000,20210601T123000", values.getAsString(Events.RDATE))
    }

    @Test
    fun testBuildEvent_NonAllDay_NoDtEnd_Duration_NonRecurring() {
        val values = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzVienna)
            duration = Duration(null, "PT1H30M")
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1591007400000L + 90*60000, values.getAsLong(Events.DTEND))
        assertEquals(tzVienna.id, values.get(Events.EVENT_END_TIMEZONE))
        assertNull(values.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_NonAllDayUtc_NoDtEnd_Duration_NonRecurring() {
        val values = buildEvent(false) {
            dtStart = DtStart(DateTime("20200601T103000Z").apply { isUtc = true })
            duration = Duration(null, "PT1H30M")
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, values.getAsLong(Events.DTSTART))
        assertEquals(TimeZones.getUtcTimeZone().id, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1591007400000L + 90*60000, values.getAsLong(Events.DTEND))
        assertEquals(TimeZones.getUtcTimeZone().id, values.get(Events.EVENT_END_TIMEZONE))
        assertNull(values.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_NonAllDay_NoDtEnd_Duration_Recurring() {
        val values = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzVienna)
            duration = Duration(null, "PT1H30M")
            rDates += RDate(DateList("20200602T113000", Value.DATE_TIME, tzVienna))
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals("PT1H30M", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("${tzVienna.id};20200601T123000,20200602T113000", values.get(Events.RDATE))
    }

    @Test
    fun testBuildEvent_NonAllDay_DtEnd_NoDuration_NonRecurring() {
        val values = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzVienna)
            dtEnd = DtEnd("20200602T143000", tzShanghai)
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1591079400000L, values.getAsLong(Events.DTEND))
        assertEquals(tzShanghai.id, values.get(Events.EVENT_END_TIMEZONE))
        assertNull(values.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_NonAllDay_DtEnd_NoDuration_Recurring() {
        val values = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzShanghai)
            dtEnd = DtEnd("20200601T123000", tzVienna)
            rDates += RDate(DateList("20200701T123000,20200702T123000", Value.DATE_TIME, tzVienna))
            rDates += RDate(DateList("20200801T123000,20200802T123000", Value.DATE_TIME, tzShanghai))
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590985800000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzShanghai.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals("PT6H", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("${tzShanghai.id};20200601T123000,20200701T183000,20200702T183000,20200801T123000,20200802T123000", values.getAsString(Events.RDATE))
    }

    @Test
    fun testBuildEvent_NonAllDay_DtEnd_NoDuration_Recurring_InfiniteRruleAndRdate() {
        val values = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzShanghai)
            dtEnd = DtEnd("20200601T123000", tzVienna)
            rRules += RRule(
                Recur("FREQ=DAILY;INTERVAL=2")
            )
            rDates += RDate(DateList("20200701T123000,20200702T123000", Value.DATE_TIME, tzVienna))
        }

        assertNull(values.get(Events.RDATE))
        assertEquals("FREQ=DAILY;INTERVAL=2", values.get(Events.RRULE))
    }

    @Test
    fun testBuildEvent_NonAllDay_DtEnd_Duration_NonRecurring() {
        val values = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzVienna)
            dtEnd = DtEnd("20200601T143000", tzVienna)
            duration = Duration(null, "PT1S")
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1591014600000L, values.getAsLong(Events.DTEND))
        assertEquals(tzVienna.id, values.get(Events.EVENT_END_TIMEZONE))
        assertNull(values.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_NonAllDay_DtEnd_Duration_Recurring() {
        val values = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzVienna)
            dtEnd = DtEnd("20200601T143000", tzVienna)
            duration = Duration(null, "PT10S")
            rRules += RRule("FREQ=MONTHLY;COUNT=1")
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals("PT2H", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("FREQ=MONTHLY;COUNT=1", values.get(Events.RRULE))
    }

    @Test
    fun testBuildEvent_AllDay_NoDtEnd_NoDuration_NonRecurring() {
        val values = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1591056000000L, values.getAsLong(Events.DTEND))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_END_TIMEZONE))
        assertNull(values.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_AllDay_NoDtEnd_NoDuration_Recurring() {
        val values = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
            rRules += RRule("FREQ=MONTHLY;COUNT=3")
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals("P1D", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("FREQ=MONTHLY;COUNT=3", values.get(Events.RRULE))
    }

    @Test
    fun testBuildEvent_AllDay_NoDtEnd_Duration_NonRecurring() {
        val values = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
            duration = Duration(null, "P2W1D")
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1592265600000L, values.getAsLong(Events.DTEND))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_END_TIMEZONE))
        assertNull(values.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_AllDay_NoDtEnd_Duration_Recurring() {
        val values = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
            duration = Duration(null, "P2D")
            rRules += RRule("FREQ=YEARLY;BYMONTH=4;BYDAY=-1SU")
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals("P2D", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("FREQ=YEARLY;BYMONTH=4;BYDAY=-1SU", values.get(Events.RRULE))
    }

    @Test
    fun testBuildEvent_AllDay_DtEnd_NoDuration_NonRecurring() {
        val values = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
            dtEnd = DtEnd(Date("20200701"))
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1593561600000L, values.getAsLong(Events.DTEND))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_END_TIMEZONE))
        assertNull(values.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_AllDay_DtEnd_NoDuration_Recurring() {
        val values = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
            dtEnd = DtEnd(Date("20200701"))
            rDates += RDate(DateList("20210601", Value.DATE))
            rDates += RDate(DateList("20220601T120030", Value.DATE_TIME))
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals("P30D", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("20200601T000000Z,20210601T000000Z,20220601T000000Z", values.get(Events.RDATE))
    }

    @Test
    fun testBuildEvent_AllDay_DtEnd_Duration_NonRecurring() {
        val values = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
            dtEnd = DtEnd(Date("20200701"))
            duration = Duration(null, "PT5M")
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1593561600000L, values.getAsLong(Events.DTEND))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_END_TIMEZONE))
        assertNull(values.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_AllDay_DtEnd_Duration_Recurring() {
        val values = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
            dtEnd = DtEnd(Date("20200701"))
            duration = Duration(null, "PT1M")
            rRules += RRule("FREQ=DAILY;COUNT=1")
        }
        assertEquals(1, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, values.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.get(Events.EVENT_TIMEZONE))

        assertEquals("P30D", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.DTEND))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        assertEquals("FREQ=DAILY;COUNT=1", values.get(Events.RRULE))
    }

    @Test
    fun testBuildEvent_FloatingTimes() {
        val values = buildEvent(false) {
            dtStart = DtStart("20200601T123000")
            dtEnd = DtEnd("20200601T123001")
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(DateTime("20200601T123000", tzDefault).time, values.getAsLong(Events.DTSTART))
        assertEquals(tzIdDefault, values.get(Events.EVENT_TIMEZONE))

        assertEquals(DateTime("20200601T123001", tzDefault).time, values.getAsLong(Events.DTEND))
        assertEquals(tzIdDefault, values.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun testBuildEvent_FloatingTimesInRecurrenceDates() {
        val values = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzShanghai)
            duration = Duration(null, "PT5M30S")
            rDates += RDate(DateList("20200602T113000", Value.DATE_TIME))
            exDates += ExDate(DateList("20200602T113000", Value.DATE_TIME))
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1590985800000L, values.getAsLong(Events.DTSTART))
        assertEquals(tzShanghai.id, values.get(Events.EVENT_TIMEZONE))

        assertEquals("PT5M30S", values.getAsString(Events.DURATION))
        assertNull(values.get(Events.EVENT_END_TIMEZONE))

        val rewritten = DateTime("20200602T113000")
        rewritten.timeZone = tzShanghai
        assertEquals("${tzShanghai.id};20200601T123000,$rewritten", values.get(Events.RDATE))
        assertEquals("$tzIdDefault;20200602T113000", values.get(Events.EXDATE))
    }

    @Test
    fun testBuildEvent_UTC() {
        val values = buildEvent(false) {
            dtStart = DtStart(DateTime(1591014600000L), true)
            dtEnd = DtEnd(DateTime(1591021801000L), true)
        }
        assertEquals(0, values.getAsInteger(Events.ALL_DAY))

        assertEquals(1591014600000L, values.getAsLong(Events.DTSTART))
        assertEquals(TimeZones.UTC_ID, values.get(Events.EVENT_TIMEZONE))

        assertEquals(1591021801000L, values.getAsLong(Events.DTEND))
        assertEquals(TimeZones.UTC_ID, values.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun testBuildEvent_Summary() {
        buildEvent(true) {
            summary = "Sample Summary"
        }.let { result ->
            assertEquals("Sample Summary", result.get(Events.TITLE))
        }
    }

    @Test
    fun testBuildEvent_Location() {
        buildEvent(true) {
            location = "Sample Location"
        }.let { result ->
            assertEquals("Sample Location", result.get(Events.EVENT_LOCATION))
        }
    }

    @Test
    fun testBuildEvent_Url() {
        buildEvent(true) {
            url = URI("https://example.com")
        }.let { result ->
            assertEquals("https://example.com", firstExtendedProperty(result))
        }
    }

    @Test
    fun testBuildEvent_Description() {
        buildEvent(true) {
            description = "Sample Description"
        }.let { result ->
            assertEquals("Sample Description", result.get(Events.DESCRIPTION))
        }
    }

    @Test
    fun testBuildEvent_Color_WhenNotAvailable() {
        buildEvent(true) {
            color = Css3Color.darkseagreen
        }.let { result ->
            assertNull(result.get(Events.CALENDAR_COLOR_KEY))
        }
    }

    @Test
    fun testBuildEvent_Color_WhenAvailable() {
        val provider = AndroidCalendarProvider(testAccount, client)
        provider.provideCss3ColorIndices()
        buildEvent(true) {
            color = Css3Color.darkseagreen
        }.let { result ->
            assertEquals(Css3Color.darkseagreen.name, result.get(Events.EVENT_COLOR_KEY))
        }
    }

    @Test
    fun testBuildEvent_Organizer_NotGroupScheduled() {
        buildEvent(true) {
            organizer = Organizer("mailto:organizer@example.com")
        }.let { result ->
            assertNull(result.get(Events.ORGANIZER))
        }
    }

    @Test
    fun testBuildEvent_Organizer_MailTo() {
        buildEvent(true) {
            organizer = Organizer("mailto:organizer@example.com")
            attendees += Attendee("mailto:attendee@example.com")
        }.let { result ->
            assertEquals("organizer@example.com", result.get(Events.ORGANIZER))
        }
    }

    @Test
    fun testBuildEvent_Organizer_EmailParameter() {
        buildEvent(true) {
            organizer = Organizer("local-id:user").apply {
                parameters.add(Email("organizer@example.com"))
            }
            attendees += Attendee("mailto:attendee@example.com")
        }.let { result ->
            assertEquals("organizer@example.com", result.get(Events.ORGANIZER))
        }
    }

    @Test
    fun testBuildEvent_Organizer_NotEmail() {
        buildEvent(true) {
            organizer = Organizer("local-id:user")
            attendees += Attendee("mailto:attendee@example.com")
        }.let { result ->
            assertNull(result.get(Events.ORGANIZER))
        }
    }

    @Test
    fun testBuildEvent_Status_Confirmed() {
        buildEvent(true) {
            status = Status.VEVENT_CONFIRMED
        }.let { result ->
            assertEquals(Events.STATUS_CONFIRMED, result.getAsInteger(Events.STATUS))
        }
    }

    @Test
    fun testBuildEvent_Status_Cancelled() {
        buildEvent(true) {
            status = Status.VEVENT_CANCELLED
        }.let { result ->
            assertEquals(Events.STATUS_CANCELED, result.getAsInteger(Events.STATUS))
        }
    }

    @Test
    fun testBuildEvent_Status_Tentative() {
        buildEvent(true) {
            status = Status.VEVENT_TENTATIVE
        }.let { result ->
            assertEquals(Events.STATUS_TENTATIVE, result.getAsInteger(Events.STATUS))
        }
    }

    @Test
    fun testBuildEvent_Status_Invalid() {
        buildEvent(true) {
            status = Status.VTODO_IN_PROCESS
        }.let { result ->
            assertEquals(Events.STATUS_TENTATIVE, result.getAsInteger(Events.STATUS))
        }
    }

    @Test
    fun testBuildEvent_Status_None() {
        buildEvent(true) {
        }.let { result ->
            assertNull(result.get(Events.STATUS))
        }
    }

    @Test
    fun testBuildEvent_Opaque_True() {
        buildEvent(true) {
            opaque = true
        }.let { result ->
            assertEquals(Events.AVAILABILITY_BUSY, result.getAsInteger(Events.AVAILABILITY))
        }
    }

    @Test
    fun testBuildEvent_Opaque_False() {
        buildEvent(true) {
            opaque = false
        }.let { result ->
            assertEquals(Events.AVAILABILITY_FREE, result.getAsInteger(Events.AVAILABILITY))
        }
    }

    @Test
    fun testBuildEvent_Classification_Public() {
        buildEvent(true) {
            classification = Clazz.PUBLIC
        }.let { result ->
            assertEquals(Events.ACCESS_PUBLIC, result.getAsInteger(Events.ACCESS_LEVEL))
            assertNull(firstUnknownProperty(result))
        }
    }

    @Test
    fun testBuildEvent_Classification_Private() {
        buildEvent(true) {
            classification = Clazz.PRIVATE
        }.let { result ->
            assertEquals(Events.ACCESS_PRIVATE, result.getAsInteger(Events.ACCESS_LEVEL))
            assertNull(firstUnknownProperty(result))
        }
    }

    @Test
    fun testBuildEvent_Classification_Confidential() {
        buildEvent(true) {
            classification = Clazz.CONFIDENTIAL
        }.let { result ->
            assertEquals(Events.ACCESS_CONFIDENTIAL, result.getAsInteger(Events.ACCESS_LEVEL))
            assertEquals(Clazz.CONFIDENTIAL, firstUnknownProperty(result))
        }
    }

    @Test
    fun testBuildEvent_Classification_Custom() {
        buildEvent(true) {
            classification = Clazz("TOP-SECRET")
        }.let { result ->
            assertEquals(Events.ACCESS_PRIVATE, result.getAsInteger(Events.ACCESS_LEVEL))
            assertEquals(Clazz("TOP-SECRET"), firstUnknownProperty(result))
        }
    }

    @Test
    fun testBuildEvent_Classification_None() {
        buildEvent(true) {
        }.let { result ->
            assertEquals(Events.ACCESS_DEFAULT, result.getAsInteger(Events.ACCESS_LEVEL))
            assertNull(firstUnknownProperty(result))
        }
    }

    @Test
    fun testBuildEvent_UID2445() {
        buildEvent(true) {
            uid = "event1@example.com"
        }.let { result ->
            assertEquals("event1@example.com", result.getAsString(Events.UID_2445))
        }
    }


    private fun firstReminder(row: ContentValues): ContentValues? {
        val id = row.getAsInteger(Events._ID)
        client.query(Reminders.CONTENT_URI.asSyncAdapter(testAccount), null,
                "${Reminders.EVENT_ID}=?", arrayOf(id.toString()), null)?.use { cursor ->
            if (cursor.moveToNext()) {
                val subRow = ContentValues(cursor.count)
                DatabaseUtils.cursorRowToContentValues(cursor, subRow)
                return subRow
            }
        }
        return null
    }

    @Test
    fun testBuildReminder_Trigger_None() {
        buildEvent(true) {
            alarms += VAlarm()
        }.let { result ->
            firstReminder(result)!!.let { reminder ->
                assertEquals(Reminders.METHOD_DEFAULT, reminder.getAsInteger(Reminders.METHOD))
                assertEquals(Reminders.MINUTES_DEFAULT, reminder.getAsInteger(Reminders.MINUTES))
            }
        }
    }

    @Test
    fun testBuildReminder_Trigger_Type_Audio() {
        buildEvent(true) {
            alarms += VAlarm(Duration.ofMinutes(-10)).apply {
                properties += Action.AUDIO
            }
        }.let { result ->
            firstReminder(result)!!.let { reminder ->
                assertEquals(Reminders.METHOD_ALERT, reminder.getAsInteger(Reminders.METHOD))
                assertEquals(10, reminder.getAsInteger(Reminders.MINUTES))
            }
        }
    }

    @Test
    fun testBuildReminder_Trigger_Type_Display() {
        buildEvent(true) {
            alarms += VAlarm(Duration.ofMinutes(-10)).apply {
                properties += Action.DISPLAY
            }
        }.let { result ->
            firstReminder(result)!!.let { reminder ->
                assertEquals(Reminders.METHOD_ALERT, reminder.getAsInteger(Reminders.METHOD))
                assertEquals(10, reminder.getAsInteger(Reminders.MINUTES))
            }
        }
    }

    @Test
    fun testBuildReminder_Trigger_Type_Email() {
        buildEvent(true) {
            alarms += VAlarm(Duration.ofSeconds(-120)).apply {
                properties += Action.EMAIL
            }
        }.let { result ->
            firstReminder(result)!!.let { reminder ->
                assertEquals(Reminders.METHOD_EMAIL, reminder.getAsInteger(Reminders.METHOD))
                assertEquals(2, reminder.getAsInteger(Reminders.MINUTES))
            }
        }
    }

    @Test
    fun testBuildReminder_Trigger_Type_Custom() {
        buildEvent(true) {
            alarms += VAlarm(Duration.ofSeconds(-120)).apply {
                properties += Action("X-CUSTOM")
            }
        }.let { result ->
            firstReminder(result)!!.let { reminder ->
                assertEquals(Reminders.METHOD_DEFAULT, reminder.getAsInteger(Reminders.METHOD))
                assertEquals(2, reminder.getAsInteger(Reminders.MINUTES))
            }
        }
    }

    @Test
    fun testBuildReminder_Trigger_RelStart_Duration() {
        buildEvent(true) {
            alarms += VAlarm(Period.ofDays(-1))
        }.let { result ->
            assertEquals(1440, firstReminder(result)!!.getAsInteger(Reminders.MINUTES))
        }
    }

    @Test
    fun testBuildReminder_Trigger_RelStart_Duration_LessThanOneMinute() {
        buildEvent(true) {
            alarms += VAlarm(Duration.ofSeconds(-10))
        }.let { result ->
            assertEquals(0, firstReminder(result)!!.getAsInteger(Reminders.MINUTES))
        }
    }

    @Test
    fun testBuildReminder_Trigger_RelStart_Duration_Positive() {
        // positive duration -> reminder is AFTER reference time -> negative minutes field
        buildEvent(true) {
            alarms += VAlarm(Duration.ofMinutes(10))
        }.let { result ->
            assertEquals(-10, firstReminder(result)!!.getAsInteger(Reminders.MINUTES))
        }
    }

    @Test
    fun testBuildReminder_Trigger_RelEnd_Duration() {
        buildEvent(false) {
            dtStart = DtStart(DateTime("20200621T120000", tzVienna))
            dtEnd = DtEnd(DateTime("20200621T140000", tzVienna))
            alarms += VAlarm(Period.ofDays(-1)).apply {
                trigger.parameters.add(Related.END)
            }
        }.let { result ->
            assertEquals(1320, firstReminder(result)!!.getAsInteger(Reminders.MINUTES))
        }
    }

    @Test
    fun testBuildReminder_Trigger_RelEnd_Duration_LessThanOneMinute() {
        buildEvent(false) {
            dtStart = DtStart(DateTime("20200621T120000", tzVienna))
            dtEnd = DtEnd(DateTime("20200621T140000", tzVienna))
            alarms += VAlarm(Duration.ofSeconds(-7240)).apply {
                trigger.parameters.add(Related.END)
            }
        }.let { result ->
            assertEquals(0, firstReminder(result)!!.getAsInteger(Reminders.MINUTES))
        }
    }

    @Test
    fun testBuildReminder_Trigger_RelEnd_Duration_Positive() {
        // positive duration -> reminder is AFTER reference time -> negative minutes field
        buildEvent(false) {
            dtStart = DtStart(DateTime("20200621T120000", tzVienna))
            dtEnd = DtEnd(DateTime("20200621T140000", tzVienna))
            alarms += VAlarm(Duration.ofMinutes(10)).apply {
                trigger.parameters.add(Related.END)
            }
        }.let { result ->
            assertEquals(-130, firstReminder(result)!!.getAsInteger(Reminders.MINUTES))
        }
    }

    @Test
    fun testBuildReminder_Trigger_Absolute() {
        buildEvent(false) {
            dtStart = DtStart(DateTime("20200621T120000", tzVienna))
            alarms += VAlarm(DateTime("20200621T110000", tzVienna))
        }.let { result ->
            assertEquals(60, firstReminder(result)!!.getAsInteger(Reminders.MINUTES))
        }
    }

    @Test
    fun testBuildReminder_Trigger_Absolute_OtherTimeZone() {
        buildEvent(false) {
            dtStart = DtStart(DateTime("20200621T120000", tzVienna))
            alarms += VAlarm(DateTime("20200621T110000", tzShanghai))
        }.let { result ->
            assertEquals(420, firstReminder(result)!!.getAsInteger(Reminders.MINUTES))
        }
    }


    private fun firstAttendee(row: ContentValues): ContentValues? {
        val id = row.getAsInteger(Events._ID)
        client.query(Attendees.CONTENT_URI.asSyncAdapter(testAccount), null,
                "${Attendees.EVENT_ID}=?", arrayOf(id.toString()), null)?.use { cursor ->
            if (cursor.moveToNext()) {
                val subRow = ContentValues(cursor.count)
                DatabaseUtils.cursorRowToContentValues(cursor, subRow)
                return subRow
            }
        }
        return null
    }

    @Test
    fun testBuildAttendee_MailTo() {
        buildEvent(true) {
            attendees += Attendee("mailto:attendee1@example.com")
        }.let { result ->
            assertEquals("attendee1@example.com", firstAttendee(result)!!.getAsString(Attendees.ATTENDEE_EMAIL))
        }
    }

    @Test
    fun testBuildAttendee_OtherUri() {
        buildEvent(true) {
            attendees += Attendee("https://example.com/principals/attendee")
        }.let { result ->
            firstAttendee(result)!!.let { attendee ->
                assertEquals("https", attendee.getAsString(Attendees.ATTENDEE_ID_NAMESPACE))
                assertEquals("//example.com/principals/attendee", attendee.getAsString(Attendees.ATTENDEE_IDENTITY))
            }
        }
    }

    @Test
    fun testBuildAttendee_CustomUri_EmailParam() {
        buildEvent(true) {
            attendees += Attendee("sample:uri").apply {
                parameters.add(Email("attendee1@example.com"))
            }
        }.let { result ->
            firstAttendee(result)!!.let { attendee ->
                assertEquals("sample", attendee.getAsString(Attendees.ATTENDEE_ID_NAMESPACE))
                assertEquals("uri", attendee.getAsString(Attendees.ATTENDEE_IDENTITY))
                assertEquals("attendee1@example.com", attendee.getAsString(Attendees.ATTENDEE_EMAIL))
            }
        }
    }

    @Test
    fun testBuildAttendee_Cn() {
        buildEvent(true) {
            attendees += Attendee("mailto:attendee@example.com").apply {
                parameters.add(Cn("Sample Attendee"))
            }
        }.let { result ->
            assertEquals("Sample Attendee", firstAttendee(result)!!.getAsString(Attendees.ATTENDEE_NAME))
        }
    }

    @Test
    fun testBuildAttendee_Individual() {
        for (cuType in arrayOf(CuType.INDIVIDUAL, null)) {
            // REQ-PARTICIPANT (default, includes unknown values)
            for (role in arrayOf(Role.REQ_PARTICIPANT, Role("x-custom-role"), null)) {
                buildEvent(true) {
                    attendees += Attendee("mailto:attendee@example.com").apply {
                        if (cuType != null)
                            parameters.add(cuType)
                        if (role != null)
                            parameters.add(role)
                    }
                }.let { result ->
                    firstAttendee(result)!!.let { attendee ->
                        assertEquals(Attendees.TYPE_REQUIRED, attendee.getAsInteger(Attendees.ATTENDEE_TYPE))
                        assertEquals(Attendees.RELATIONSHIP_ATTENDEE, attendee.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
                    }
                }
            }
            // OPT-PARTICIPANT
            buildEvent(true) {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    if (cuType != null)
                        parameters.add(cuType)
                    parameters.add(Role.OPT_PARTICIPANT)
                }
            }.let { result ->
                firstAttendee(result)!!.let { attendee ->
                    assertEquals(Attendees.TYPE_OPTIONAL, attendee.getAsInteger(Attendees.ATTENDEE_TYPE))
                    assertEquals(Attendees.RELATIONSHIP_ATTENDEE, attendee.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
                }
            }
            // NON-PARTICIPANT
            buildEvent(true) {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    if (cuType != null)
                        parameters.add(cuType)
                    parameters.add(Role.NON_PARTICIPANT)
                }
            }.let { result ->
                firstAttendee(result)!!.let { attendee ->
                    assertEquals(Attendees.TYPE_NONE, attendee.getAsInteger(Attendees.ATTENDEE_TYPE))
                    assertEquals(Attendees.RELATIONSHIP_ATTENDEE, attendee.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
                }
            }
        }
    }

    @Test
    fun testBuildAttendee_Unknown() {
        // REQ-PARTICIPANT (default, includes unknown values)
        for (role in arrayOf(Role.REQ_PARTICIPANT, Role("x-custom-role"), null)) {
            buildEvent(true) {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(CuType.UNKNOWN)
                    if (role != null)
                        parameters.add(role)
                }
            }.let { result ->
                firstAttendee(result)!!.let { attendee ->
                    assertEquals(Attendees.TYPE_REQUIRED, attendee.getAsInteger(Attendees.ATTENDEE_TYPE))
                    assertEquals(Attendees.RELATIONSHIP_NONE, attendee.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
                }
            }
        }
        // OPT-PARTICIPANT
        buildEvent(true) {
            attendees += Attendee("mailto:attendee@example.com").apply {
                parameters.add(CuType.UNKNOWN)
                parameters.add(Role.OPT_PARTICIPANT)
            }
        }.let { result ->
            firstAttendee(result)!!.let { attendee ->
                assertEquals(Attendees.TYPE_OPTIONAL, attendee.getAsInteger(Attendees.ATTENDEE_TYPE))
                assertEquals(Attendees.RELATIONSHIP_NONE, attendee.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
            }
        }
        // NON-PARTICIPANT
        buildEvent(true) {
            attendees += Attendee("mailto:attendee@example.com").apply {
                parameters.add(CuType.UNKNOWN)
                parameters.add(Role.NON_PARTICIPANT)
            }
        }.let { result ->
            firstAttendee(result)!!.let { attendee ->
                assertEquals(Attendees.TYPE_NONE, attendee.getAsInteger(Attendees.ATTENDEE_TYPE))
                assertEquals(Attendees.ATTENDEE_STATUS_NONE, attendee.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
            }
        }
    }

    @Test
    fun testBuildAttendee_Group() {
        // REQ-PARTICIPANT (default, includes unknown values)
        for (role in arrayOf(Role.REQ_PARTICIPANT, Role("x-custom-role"), null)) {
            buildEvent(true) {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(CuType.GROUP)
                    if (role != null)
                        parameters.add(role)
                }
            }.let { result ->
                firstAttendee(result)!!.let { attendee ->
                    assertEquals(Attendees.TYPE_REQUIRED, attendee.getAsInteger(Attendees.ATTENDEE_TYPE))
                    assertEquals(Attendees.RELATIONSHIP_PERFORMER, attendee.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
                }
            }
        }
        // OPT-PARTICIPANT
        buildEvent(true) {
            attendees += Attendee("mailto:attendee@example.com").apply {
                parameters.add(CuType.GROUP)
                parameters.add(Role.OPT_PARTICIPANT)
            }
        }.let { result ->
            firstAttendee(result)!!.let { attendee ->
                assertEquals(Attendees.TYPE_OPTIONAL, attendee.getAsInteger(Attendees.ATTENDEE_TYPE))
                assertEquals(Attendees.RELATIONSHIP_PERFORMER, attendee.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
            }
        }
        // NON-PARTICIPANT
        buildEvent(true) {
            attendees += Attendee("mailto:attendee@example.com").apply {
                parameters.add(CuType.GROUP)
                parameters.add(Role.NON_PARTICIPANT)
            }
        }.let { result ->
            firstAttendee(result)!!.let { attendee ->
                assertEquals(Attendees.TYPE_NONE, attendee.getAsInteger(Attendees.ATTENDEE_TYPE))
                assertEquals(Attendees.RELATIONSHIP_PERFORMER, attendee.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
            }
        }
    }

    @Test
    fun testBuildAttendee_Resource() {
        for (role in arrayOf(null, Role.REQ_PARTICIPANT, Role.OPT_PARTICIPANT, Role.NON_PARTICIPANT, Role("X-CUSTOM-ROLE")))
            buildEvent(true) {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(CuType.RESOURCE)
                    if (role != null)
                        parameters.add(role)
                }
            }.let { result ->
                firstAttendee(result)!!.let { attendee ->
                    assertEquals(Attendees.TYPE_RESOURCE, attendee.getAsInteger(Attendees.ATTENDEE_TYPE))
                    assertEquals(Attendees.RELATIONSHIP_NONE, attendee.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
                }
            }
        // CHAIR
        buildEvent(true) {
            attendees += Attendee("mailto:attendee@example.com").apply {
                parameters.add(CuType.RESOURCE)
                parameters.add(Role.CHAIR)
            }
        }.let { result ->
            firstAttendee(result)!!.let { attendee ->
                assertEquals(Attendees.TYPE_RESOURCE, attendee.getAsInteger(Attendees.ATTENDEE_TYPE))
                assertEquals(Attendees.RELATIONSHIP_SPEAKER, attendee.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
            }
        }
    }

    @Test
    fun testBuildAttendee_Chair() {
        for (cuType in arrayOf(null, CuType.INDIVIDUAL, CuType.UNKNOWN, CuType.GROUP, CuType("x-custom-cutype")))
            buildEvent(true) {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    if (cuType != null)
                        parameters.add(cuType)
                    parameters.add(Role.CHAIR)
                }
            }.let { result ->
                firstAttendee(result)!!.let { attendee ->
                    assertEquals(Attendees.TYPE_REQUIRED, attendee.getAsInteger(Attendees.ATTENDEE_TYPE))
                    assertEquals(Attendees.RELATIONSHIP_SPEAKER, attendee.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
                }
            }
    }

    @Test
    fun testBuildAttendee_Room() {
        for (role in arrayOf(null, Role.CHAIR, Role.REQ_PARTICIPANT, Role.OPT_PARTICIPANT, Role.NON_PARTICIPANT, Role("X-CUSTOM-ROLE")))
            buildEvent(true) {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(CuType.ROOM)
                    if (role != null)
                        parameters.add(role)
                }
            }.let { result ->
                firstAttendee(result)!!.let { attendee ->
                    assertEquals(Attendees.TYPE_RESOURCE, attendee.getAsInteger(Attendees.ATTENDEE_TYPE))
                    assertEquals(Attendees.RELATIONSHIP_PERFORMER, attendee.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
                }
            }
    }

    @Test
    fun testBuildAttendee_Organizer() {
        buildEvent(true) {
            attendees += Attendee(URI("mailto", testAccount.name, null))
        }.let { result ->
            firstAttendee(result)!!.let { attendee ->
                assertEquals(testAccount.name, attendee.getAsString(Attendees.ATTENDEE_EMAIL))
                assertEquals(Attendees.TYPE_REQUIRED, attendee.getAsInteger(Attendees.ATTENDEE_TYPE))
                assertEquals(Attendees.RELATIONSHIP_ORGANIZER, attendee.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP))
            }
        }
    }
    @Test
    fun testBuildAttendee_PartStat_None() {
        buildEvent(true) {
            attendees += Attendee("mailto:attendee@example.com")
        }.let { result ->
            assertEquals(Attendees.ATTENDEE_STATUS_INVITED, firstAttendee(result)!!.getAsInteger(Attendees.ATTENDEE_STATUS))
        }
    }

    @Test
    fun testBuildAttendee_PartStat_NeedsAction() {
        buildEvent(true) {
            attendees += Attendee("mailto:attendee@example.com").apply {
                parameters.add(PartStat.NEEDS_ACTION)
            }
        }.let { result ->
            assertEquals(Attendees.ATTENDEE_STATUS_INVITED, firstAttendee(result)!!.getAsInteger(Attendees.ATTENDEE_STATUS))
        }
    }

    @Test
    fun testBuildAttendee_PartStat_Accepted() {
        buildEvent(true) {
            attendees += Attendee("mailto:attendee@example.com").apply {
                parameters.add(PartStat.ACCEPTED)
            }
        }.let { result ->
            assertEquals(Attendees.ATTENDEE_STATUS_ACCEPTED, firstAttendee(result)!!.getAsInteger(Attendees.ATTENDEE_STATUS))
        }
    }

    @Test
    fun testBuildAttendee_PartStat_Declined() {
        buildEvent(true) {
            attendees += Attendee("mailto:attendee@example.com").apply {
                parameters.add(PartStat.DECLINED)
            }
        }.let { result ->
            assertEquals(Attendees.ATTENDEE_STATUS_DECLINED, firstAttendee(result)!!.getAsInteger(Attendees.ATTENDEE_STATUS))
        }
    }

    @Test
    fun testBuildAttendee_PartStat_Tentative() {
        buildEvent(true) {
            attendees += Attendee("mailto:attendee@example.com").apply {
                parameters.add(PartStat.TENTATIVE)
            }
        }.let { result ->
            assertEquals(Attendees.ATTENDEE_STATUS_TENTATIVE, firstAttendee(result)!!.getAsInteger(Attendees.ATTENDEE_STATUS))
        }
    }

    @Test
    fun testBuildAttendee_PartStat_Delegated() {
        buildEvent(true) {
            attendees += Attendee("mailto:attendee@example.com").apply {
                parameters.add(PartStat.DELEGATED)
            }
        }.let { result ->
            assertEquals(Attendees.ATTENDEE_STATUS_NONE, firstAttendee(result)!!.getAsInteger(Attendees.ATTENDEE_STATUS))
        }
    }

    @Test
    fun testBuildAttendee_PartStat_Custom() {
        buildEvent(true) {
            attendees += Attendee("mailto:attendee@example.com").apply {
                parameters.add(PartStat("X-WILL-ASK"))
            }
        }.let { result ->
            assertEquals(Attendees.ATTENDEE_STATUS_INVITED, firstAttendee(result)!!.getAsInteger(Attendees.ATTENDEE_STATUS))
        }
    }


    @Test
    fun testBuildUnknownProperty() {
        buildEvent(true) {
            val params = ParameterList()
            params.add(Language("en"))
            unknownProperties += XProperty("X-NAME", params, "Custom Value")
        }.let { result ->
            firstUnknownProperty(result)!!.let { property ->
                assertEquals("X-NAME", property.name)
                assertEquals("en", property.getParameter<Language>(Parameter.LANGUAGE).value)
                assertEquals("Custom Value", property.value)
            }
        }
    }

    @Test
    fun testBuildUnknownProperty_NoValue() {
        buildEvent(true) {
            unknownProperties += XProperty("ATTACH", ParameterList(), null)
        }.let { result ->
            // The property should not have been added, so the first unknown property should be null
            assertNull(firstUnknownProperty(result))
        }
    }

    private fun firstException(values: ContentValues): ContentValues? {
        val id = values.getAsInteger(Events._ID)
        client.query(Events.CONTENT_URI.asSyncAdapter(testAccount), null,
                "${Events.ORIGINAL_ID}=?", arrayOf(id.toString()), null)?.use { cursor ->
            if (cursor.moveToNext()) {
                val result = ContentValues(cursor.count)
                DatabaseUtils.cursorRowToContentValues(cursor, result)
                return result
            }
        }
        return null
    }

    @Test
    fun testBuildException_NonAllDay() {
        buildEvent(false) {
            dtStart = DtStart("20200706T193000", tzVienna)
            rRules += RRule("FREQ=DAILY;COUNT=10")
            exceptions += Event().apply {
                recurrenceId = RecurrenceId("20200707T193000", tzVienna)
                dtStart = DtStart("20200706T203000", tzShanghai)
                summary = "Event moved to one hour later"
            }
        }.let { result ->
            assertEquals(1594056600000L, result.getAsLong(Events.DTSTART))
            assertEquals(tzVienna.id, result.getAsString(Events.EVENT_TIMEZONE))
            assertEquals(0, result.getAsInteger(Events.ALL_DAY))
            assertEquals("FREQ=DAILY;COUNT=10", result.getAsString(Events.RRULE))
            firstException(result)!!.let { exception ->
                assertEquals(1594143000000L, exception.getAsLong(Events.ORIGINAL_INSTANCE_TIME))
                assertEquals(0, exception.getAsInteger(Events.ORIGINAL_ALL_DAY))
                assertEquals(1594038600000L, exception.getAsLong(Events.DTSTART))
                assertEquals(tzShanghai.id, exception.getAsString(Events.EVENT_TIMEZONE))
                assertEquals(0, exception.getAsInteger(Events.ALL_DAY))
                assertEquals("Event moved to one hour later", exception.getAsString(Events.TITLE))
            }
        }
    }

    @Test
    fun testBuildException_NonAllDay_RecurrenceIdAllDay() {
        buildEvent(false) {
            dtStart = DtStart("20200706T193000", tzVienna)
            rRules += RRule("FREQ=DAILY;COUNT=10")
            exceptions += Event().apply {
                recurrenceId = RecurrenceId(Date("20200707"))   // illegal! should be rewritten to DateTime("20200707T193000", tzVienna)
                dtStart = DtStart("20200706T203000", tzShanghai)
                summary = "Event moved to one hour later"
            }
        }.let { result ->
            assertEquals(1594056600000L, result.getAsLong(Events.DTSTART))
            assertEquals(tzVienna.id, result.getAsString(Events.EVENT_TIMEZONE))
            assertEquals(0, result.getAsInteger(Events.ALL_DAY))
            assertEquals("FREQ=DAILY;COUNT=10", result.getAsString(Events.RRULE))
            firstException(result)!!.let { exception ->
                assertEquals(1594143000000L, exception.getAsLong(Events.ORIGINAL_INSTANCE_TIME))
                assertEquals(0, exception.getAsInteger(Events.ORIGINAL_ALL_DAY))
                assertEquals(1594038600000L, exception.getAsLong(Events.DTSTART))
                assertEquals(tzShanghai.id, exception.getAsString(Events.EVENT_TIMEZONE))
                assertEquals(0, exception.getAsInteger(Events.ALL_DAY))
                assertEquals("Event moved to one hour later", exception.getAsString(Events.TITLE))
            }
        }
    }

    @Test
    fun testBuildException_AllDay() {
        buildEvent(false) {
            dtStart = DtStart(Date("20200706"))
            rRules += RRule("FREQ=WEEKLY;COUNT=3")
            exceptions += Event().apply {
                recurrenceId = RecurrenceId(Date("20200707"))
                dtStart = DtStart("20200706T123000", tzVienna)
                summary = "Today not an all-day event"
            }
        }.let { result ->
            assertEquals(1593993600000L, result.getAsLong(Events.DTSTART))
            assertEquals(AndroidTimeUtils.TZID_ALLDAY, result.getAsString(Events.EVENT_TIMEZONE))
            assertEquals(1, result.getAsInteger(Events.ALL_DAY))
            assertEquals("FREQ=WEEKLY;COUNT=3", result.getAsString(Events.RRULE))
            firstException(result)!!.let { exception ->
                assertEquals(1594080000000L, exception.getAsLong(Events.ORIGINAL_INSTANCE_TIME))
                assertEquals(1, exception.getAsInteger(Events.ORIGINAL_ALL_DAY))
                assertEquals(1594031400000L, exception.getAsLong(Events.DTSTART))
                assertEquals(0, exception.getAsInteger(Events.ALL_DAY))
                assertEquals("Today not an all-day event", exception.getAsString(Events.TITLE))
            }
        }
    }

    @Test
    fun testBuildException_AllDay_RecurrenceIdNonAllDay() {
        buildEvent(false) {
            dtStart = DtStart(Date("20200706"))
            rRules += RRule("FREQ=WEEKLY;COUNT=3")
            exceptions += Event().apply {
                recurrenceId = RecurrenceId("20200707T000000", tzVienna)     // illegal! should be rewritten to Date("20200707")
                dtStart = DtStart("20200706T123000", tzVienna)
                summary = "Today not an all-day event"
            }
        }.let { result ->
            assertEquals(1593993600000L, result.getAsLong(Events.DTSTART))
            assertEquals(AndroidTimeUtils.TZID_ALLDAY, result.getAsString(Events.EVENT_TIMEZONE))
            assertEquals(1, result.getAsInteger(Events.ALL_DAY))
            assertEquals("FREQ=WEEKLY;COUNT=3", result.getAsString(Events.RRULE))
            firstException(result)!!.let { exception ->
                assertEquals(1594080000000L, exception.getAsLong(Events.ORIGINAL_INSTANCE_TIME))
                assertEquals(1, exception.getAsInteger(Events.ORIGINAL_ALL_DAY))
                assertEquals(1594031400000L, exception.getAsLong(Events.DTSTART))
                assertEquals(0, exception.getAsInteger(Events.ALL_DAY))
                assertEquals("Today not an all-day event", exception.getAsString(Events.TITLE))
            }
        }
    }


    private fun populateAndroidEvent(
        automaticDates: Boolean,
        destinationCalendar: AndroidCalendar = calendar,
        asSyncAdapter: Boolean = false,
        insertCallback: (id: Long) -> Unit = {},
        extendedProperties: Map<String, String> = emptyMap(),
        valuesBuilder: ContentValues.() -> Unit = {}
    ): AndroidEvent {
        val values = ContentValues()
        values.put(Events.CALENDAR_ID, destinationCalendar.id)
        if (automaticDates) {
            values.put(Events.DTSTART, 1592733600000L)  // 21/06/2020 12:00 +0200
            values.put(Events.EVENT_TIMEZONE, "Europe/Berlin")
            values.put(Events.DTEND, 1592742600000L)    // 21/06/2020 14:30 +0200
            values.put(Events.EVENT_END_TIMEZONE, "Europe/Berlin")
        }
        valuesBuilder(values)
        logger.info("Inserting test event: $values")
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

        return destinationCalendar.getEvent(id)!!
    }

    private fun populateEvent(
        automaticDates: Boolean,
        destinationCalendar: AndroidCalendar = calendar,
        asSyncAdapter: Boolean = false,
        insertCallback: (id: Long) -> Unit = {},
        extendedProperties: Map<String, String> = emptyMap(),
        valuesBuilder: ContentValues.() -> Unit = {}
    ): Event {
        return populateAndroidEvent(
            automaticDates,
            destinationCalendar,
            asSyncAdapter,
            insertCallback,
            extendedProperties,
            valuesBuilder
        ).event!!
    }

    @Test
    fun testPopulateEvent_Uid_iCalUid() {
        populateEvent(
            true,
            extendedProperties = mapOf(
                AndroidEvent.EXTNAME_ICAL_UID to "event1@example.com"
            )
        ).let { result ->
            assertEquals("event1@example.com", result.uid)
        }
    }

    @Test
    fun testPopulateEvent_Uid_UID_2445() {
        populateEvent(true) {
            put(Events.UID_2445, "event1@example.com")
        }.let { result ->
            assertEquals("event1@example.com", result.uid)
        }
    }

    @Test
    fun testPopulateEvent_Uid_UID_2445_and_iCalUid() {
        populateEvent(
            true,
            extendedProperties = mapOf(
                AndroidEvent.EXTNAME_ICAL_UID to "event1@example.com"
            )
        ) {
            put(Events.UID_2445, "event2@example.com")
        }.let { result ->
            assertEquals("event2@example.com", result.uid)
        }
    }


    @Test
    fun testPopulateEvent_Sequence_Int() {
        populateEvent(true, asSyncAdapter = true) {
            put(AndroidEvent.COLUMN_SEQUENCE, 5)
        }.let { result ->
            assertEquals(5, result.sequence)
        }
    }

    @Test
    fun testPopulateEvent_Sequence_Null() {
        populateEvent(true, asSyncAdapter = true) {
            putNull(AndroidEvent.COLUMN_SEQUENCE)
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
            extendedProperties = mapOf(AndroidEvent.EXTNAME_URL to "https://example.com")
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
            logger.info("Inserting test reminder: $reminderValues")
            client.insert(Reminders.CONTENT_URI.asSyncAdapter(testAccount), reminderValues)
        }).let { result ->
            return result.alarms.firstOrNull()
        }
    }

    @Test
    fun testPopulateReminder_TypeEmail_AccountNameEmail() {
        // account name looks like an email address
        assertEquals("ical4android@example.com", testAccount.name)

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


    private fun populateAttendee(builder: ContentValues.() -> Unit): Attendee? {
        populateEvent(true, insertCallback = { id ->
            val attendeeValues = ContentValues()
            attendeeValues.put(Attendees.EVENT_ID, id)
            builder(attendeeValues)
            logger.info("Inserting test attendee: $attendeeValues")
            client.insert(Attendees.CONTENT_URI.asSyncAdapter(testAccount), attendeeValues)
        }).let { result ->
            return result.attendees.firstOrNull()
        }
    }

    @Test
    fun testPopulateAttendee_Email() {
        populateAttendee {
            put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
        }!!.let { attendee ->
            assertEquals(URI("mailto:attendee@example.com"), attendee.calAddress)
        }
    }

    @Test
    fun testPopulateAttendee_OtherUri() {
        populateAttendee {
            put(Attendees.ATTENDEE_ID_NAMESPACE, "https")
            put(Attendees.ATTENDEE_IDENTITY, "//example.com/principals/attendee")
        }!!.let { attendee ->
            assertEquals(URI("https://example.com/principals/attendee"), attendee.calAddress)
        }
    }

    @Test
    fun testPopulateAttendee_EmailAndOtherUri() {
        populateAttendee {
            put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
            put(Attendees.ATTENDEE_ID_NAMESPACE, "https")
            put(Attendees.ATTENDEE_IDENTITY, "//example.com/principals/attendee")
        }!!.let { attendee ->
            assertEquals(URI("https://example.com/principals/attendee"), attendee.calAddress)
            assertEquals("attendee@example.com", attendee.getParameter<Email>(Parameter.EMAIL).value)
        }
    }

    @Test
    fun testPopulateAttendee_AttendeeOrganizer() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_ATTENDEE, Attendees.RELATIONSHIP_ORGANIZER))
            for (type in arrayOf(Attendees.TYPE_REQUIRED, Attendees.TYPE_OPTIONAL, Attendees.TYPE_NONE, null))
                populateAttendee {
                    put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
                    put(Attendees.ATTENDEE_RELATIONSHIP, relationship)
                    if (type != null)
                        put(Attendees.ATTENDEE_TYPE, type as Int?)
                }!!.let { attendee ->
                    assertNull(attendee.getParameter(Parameter.CUTYPE))
                }
    }

    @Test
    fun testPopulateAttendee_Performer() {
        for (type in arrayOf(Attendees.TYPE_REQUIRED, Attendees.TYPE_OPTIONAL, Attendees.TYPE_NONE, null))
            populateAttendee {
                put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
                put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_PERFORMER)
                if (type != null)
                    put(Attendees.ATTENDEE_TYPE, type as Int?)
            }!!.let { attendee ->
                assertEquals(CuType.GROUP, attendee.getParameter<CuType>(Parameter.CUTYPE))
            }
    }

    @Test
    fun testPopulateAttendee_Speaker() {
        for (type in arrayOf(Attendees.TYPE_REQUIRED, Attendees.TYPE_OPTIONAL, Attendees.TYPE_NONE, null))
            populateAttendee {
                put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
                put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_SPEAKER)
                if (type != null)
                    put(Attendees.ATTENDEE_TYPE, type as Int?)
            }!!.let { attendee ->
                assertNull(attendee.getParameter(Parameter.CUTYPE))
                assertEquals(Role.CHAIR, attendee.getParameter<Role>(Parameter.ROLE))
            }
        // TYPE_RESOURCE
        populateAttendee {
            put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_SPEAKER)
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_RESOURCE)
        }!!.let { attendee ->
            assertEquals(CuType.RESOURCE, attendee.getParameter<CuType>(Parameter.CUTYPE))
            assertEquals(Role.CHAIR, attendee.getParameter<Role>(Parameter.ROLE))
        }
    }

    @Test
    fun testPopulateAttendee_RelNone() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_NONE, null))
            for (type in arrayOf(Attendees.TYPE_REQUIRED, Attendees.TYPE_OPTIONAL, Attendees.TYPE_NONE, null))
                populateAttendee {
                    put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
                    put(Attendees.ATTENDEE_RELATIONSHIP, relationship)
                    if (type != null)
                        put(Attendees.ATTENDEE_TYPE, type as Int?)
                }!!.let { attendee ->
                    assertEquals(CuType.UNKNOWN, attendee.getParameter<CuType>(Parameter.CUTYPE))
                }
    }

    @Test
    fun testPopulateAttendee_TypeNone() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_ATTENDEE, Attendees.RELATIONSHIP_ORGANIZER, Attendees.RELATIONSHIP_PERFORMER, Attendees.RELATIONSHIP_NONE, null))
            populateAttendee {
                put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
                put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE)
                if (relationship != null)
                    put(Attendees.ATTENDEE_RELATIONSHIP, relationship)
            }!!.let { attendee ->
                assertNull(attendee.getParameter<Role>(Parameter.ROLE))
            }
    }

    @Test
    fun testPopulateAttendee_Required() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_ATTENDEE, Attendees.RELATIONSHIP_ORGANIZER, Attendees.RELATIONSHIP_PERFORMER, Attendees.RELATIONSHIP_NONE, null))
            populateAttendee {
                put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
                put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
                if (relationship != null)
                    put(Attendees.ATTENDEE_RELATIONSHIP, relationship)
            }!!.let { attendee ->
                assertNull(attendee.getParameter(Parameter.ROLE))
            }
    }

    @Test
    fun testPopulateAttendee_Optional() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_ATTENDEE, Attendees.RELATIONSHIP_ORGANIZER, Attendees.RELATIONSHIP_PERFORMER, Attendees.RELATIONSHIP_NONE, null))
            populateAttendee {
                put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
                put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_OPTIONAL)
                if (relationship != null)
                    put(Attendees.ATTENDEE_RELATIONSHIP, relationship)
            }!!.let { attendee ->
                assertEquals(Role.OPT_PARTICIPANT, attendee.getParameter<Role>(Parameter.ROLE))
            }
    }

    @Test
    fun testPopulateAttendee_Resource() {
        for (relationship in arrayOf(Attendees.RELATIONSHIP_ATTENDEE, Attendees.RELATIONSHIP_ORGANIZER, Attendees.RELATIONSHIP_NONE, null))
            populateAttendee {
                put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
                put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_RESOURCE)
                if (relationship != null)
                    put(Attendees.ATTENDEE_RELATIONSHIP, relationship)
            }!!.let { attendee ->
                assertEquals(CuType.RESOURCE, attendee.getParameter<CuType>(Parameter.CUTYPE))
            }
        // RELATIONSHIP_PERFORMER
        populateAttendee {
            put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
            put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_RESOURCE)
            put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_PERFORMER)
        }!!.let { attendee ->
            assertEquals(CuType.ROOM, attendee.getParameter<CuType>(Parameter.CUTYPE))
        }
    }

    @Test
    fun testPopulateAttendee_Status_Null() {
        populateAttendee {
            put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
        }!!.let { attendee ->
            assertNull(attendee.getParameter(Parameter.PARTSTAT))
        }
    }

    @Test
    fun testPopulateAttendee_Status_Invited() {
        populateAttendee {
            put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
            put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_INVITED)
        }!!.let { attendee ->
            assertEquals(PartStat.NEEDS_ACTION, attendee.getParameter(Parameter.PARTSTAT))
        }
    }

    @Test
    fun testPopulateAttendee_Status_Accepted() {
        populateAttendee {
            put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
            put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_ACCEPTED)
        }!!.let { attendee ->
            assertEquals(PartStat.ACCEPTED, attendee.getParameter(Parameter.PARTSTAT))
        }
    }

    @Test
    fun testPopulateAttendee_Status_Declined() {
        populateAttendee {
            put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
            put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_DECLINED)
        }!!.let { attendee ->
            assertEquals(PartStat.DECLINED, attendee.getParameter(Parameter.PARTSTAT))
        }
    }

    @Test
    fun testPopulateAttendee_Status_Tentative() {
        populateAttendee {
            put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
            put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_TENTATIVE)
        }!!.let { attendee ->
            assertEquals(PartStat.TENTATIVE, attendee.getParameter(Parameter.PARTSTAT))
        }
    }

    @Test
    fun testPopulateAttendee_Status_None() {
        populateAttendee {
            put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
            put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_NONE)
        }!!.let { attendee ->
            assertNull(attendee.getParameter(Parameter.PARTSTAT))
        }
    }

    @Test
    fun testPopulateAttendee_Rsvp() {
        populateAttendee {
            put(Attendees.ATTENDEE_EMAIL, "attendee@example.com")
        }!!.let { attendee ->
            assertTrue(attendee.getParameter<Rsvp>(Parameter.RSVP).rsvp)
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
    fun testUpdateEvent() {
        // add test event without reminder
        val event = Event()
        event.uid = "sample1@testAddEvent"
        event.summary = "Sample event"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")
        event.organizer = Organizer(URI("mailto:organizer@example.com"))
        val uri = AndroidEvent(calendar, event, "update-event").add()

        // update test event in calendar
        val testEvent = calendar.getEvent(ContentUris.parseId(uri))!!
        val event2 = testEvent.event!!
        event2.summary = "Updated event"
        // add data rows
        event2.alarms += VAlarm(Duration.parse("-P1DT2H3M4S"))
        event2.attendees += Attendee(URI("mailto:user@example.com"))
        val uri2 = testEvent.update(event2)

        // event should have been updated
        assertEquals(ContentUris.parseId(uri), ContentUris.parseId(uri2))

        // read again and verify result
        val updatedEvent = calendar.getEvent(ContentUris.parseId(uri2))!!
        try {
            val event3 = updatedEvent.event!!
            assertEquals(event2.summary, event3.summary)
            assertEquals(1, event3.alarms.size)
            assertEquals(1, event3.attendees.size)
        } finally {
            updatedEvent.delete()
        }
    }

    @Test
    fun testUpdateEvent_ResetColor() {
        // add event with color
        val event = Event().apply {
            uid = "sample1@testAddEvent"
            dtStart = DtStart(DateTime())
            color = Css3Color.silver
        }
        val uri = AndroidEvent(calendar, event, "reset-color").add()
        val id = ContentUris.parseId(uri)

        // verify that it has color
        val beforeUpdate = calendar.getEvent(id)!!
        assertNotNull(beforeUpdate.event?.color)

        // update: reset color
        event.color = null
        beforeUpdate.update(event)

        // verify that it doesn't have color anymore
        val afterUpdate = calendar.getEvent(id)!!
        assertNull(afterUpdate.event!!.color)
    }

    @Test
    fun testUpdateEvent_UpdateStatusFromNull() {
        val event = Event()
        event.uid = "sample1@testAddEvent"
        event.summary = "Sample event with STATUS"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")
        val uri = AndroidEvent(calendar, event, "update-status-from-null").add()

        // update test event in calendar
        val testEvent = calendar.getEvent(ContentUris.parseId(uri))!!
        val event2 = testEvent.event!!
        event2.summary = "Sample event without STATUS"
        event2.status = Status.VEVENT_CONFIRMED
        val uri2 = testEvent.update(event2)

        // event should have been updated
        assertEquals(ContentUris.parseId(uri), ContentUris.parseId(uri2))

        // read again and verify result
        val updatedEvent = calendar.getEvent(ContentUris.parseId(uri2))!!
        try {
            val event3 = updatedEvent.event!!
            assertEquals(Status.VEVENT_CONFIRMED, event3.status)
        } finally {
            updatedEvent.delete()
        }
    }

    @Test
    fun testUpdateEvent_UpdateStatusToNull() {
        val event = Event()
        event.uid = "sample1@testAddEvent"
        event.summary = "Sample event with STATUS"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")
        event.status = Status.VEVENT_CONFIRMED
        val uri = AndroidEvent(calendar, event, "update-status-to-null").add()

        // update test event in calendar
        val testEvent = calendar.getEvent(ContentUris.parseId(uri))!!
        val event2 = testEvent.event!!
        event2.summary = "Sample event without STATUS"
        event2.status = null
        val uri2 = testEvent.update(event2)

        // event should have been deleted and inserted again
        assertNotEquals(ContentUris.parseId(uri), ContentUris.parseId(uri2))

        // read again and verify result
        val updatedEvent = calendar.getEvent(ContentUris.parseId(uri2))!!
        try {
            val event3 = updatedEvent.event!!
            assertNull(event3.status)
        } finally {
            updatedEvent.delete()
        }
    }



    @Test
    fun testTransaction() {
        val event = Event()
        event.uid = "sample1@testTransaction"
        event.summary = "an event"
        event.dtStart = DtStart("20150502T120000Z")
        event.dtEnd = DtEnd("20150502T130000Z")
        for (i in 0 until 20)
            event.attendees += Attendee(URI("mailto:att$i@example.com"))
        val uri = AndroidEvent(calendar, event, "transaction").add()

        val testEvent = calendar.getEvent(ContentUris.parseId(uri))!!
        try {
            assertEquals(20, testEvent.event!!.attendees.size)
        } finally {
            testEvent.delete()
        }
    }


    // companion object

    @Test
    fun testMarkEventAsDeleted() {
        // Create event
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "A fine event"
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        // Delete event
        AndroidEvent.markAsDeleted(client, testAccount, localEvent.id!!)

        // Get the status of whether the event is deleted
        client.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, localEvent.id!!).asSyncAdapter(testAccount),
            arrayOf(Events.DELETED),
            null,
            null, null
        )!!.use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }


    @Test
    fun testNumDirectInstances_SingleInstance() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 1 instance"
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(1, AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumDirectInstances_Recurring() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 5 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=5"))
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(5, AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumDirectInstances_Recurring_Endless() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event without end"
            rRules.add(RRule("FREQ=DAILY"))
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertNull(AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    // flaky, needs InitCalendarProviderRule
    fun testNumDirectInstances_Recurring_LateEnd() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 53 years"
            rRules.add(RRule("FREQ=YEARLY;UNTIL=20740119T010203Z"))     // year 2074 is not supported by Android <11 Calendar Storage
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            assertEquals(52, AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!))
        else
            assertNull(AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumDirectInstances_Recurring_ManyInstances() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 2 years"
            rRules.add(RRule("FREQ=DAILY;UNTIL=20240120T010203Z"))
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()
        val number = AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!)

        // Some android versions (i.e. <=Q and S) return 365*2 instances (wrong, 365*2+1 => correct),
        // but we are satisfied with either result for now
        assertTrue(number == 365 * 2 || number == 365 * 2 + 1)
    }

    @Test
    fun testNumDirectInstances_RecurringWithExdate() {
        val event = Event().apply {
            dtStart = DtStart(Date("20220120T010203Z"))
            summary = "Event with 5 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=5"))
            exDates.add(ExDate(DateList("20220121T010203Z", Value.DATE_TIME)))
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(4, AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumDirectInstances_RecurringWithExceptions() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 5 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=5"))
            exceptions.add(Event().apply {
                recurrenceId = RecurrenceId("20220122T010203Z")
                dtStart = DtStart("20220122T130203Z")
                summary = "Exception on 3rd day"
            })
            exceptions.add(Event().apply {
                recurrenceId = RecurrenceId("20220124T010203Z")
                dtStart = DtStart("20220122T160203Z")
                summary = "Exception on 5th day"
            })
        }
        val localEvent = AndroidEvent(calendar, event, "filename.ics", null, null, 0)
        localEvent.add()

        assertEquals(5 - 2, AndroidEvent.numDirectInstances(client, testAccount, localEvent.id!!))
    }


    @Test
    fun testNumInstances_SingleInstance() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 1 instance"
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(1, AndroidEvent.numInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumInstances_Recurring() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 5 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=5"))
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(5, AndroidEvent.numInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumInstances_Recurring_Endless() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with infinite instances"
            rRules.add(RRule("FREQ=YEARLY"))
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertNull(AndroidEvent.numInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumInstances_Recurring_LateEnd() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event over 22 years"
            rRules.add(RRule("FREQ=YEARLY;UNTIL=20740119T010203Z"))     // year 2074 not supported by Android <11 Calendar Storage
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            assertEquals(52, AndroidEvent.numInstances(client, testAccount, localEvent.id!!))
        else
            assertNull(AndroidEvent.numInstances(client, testAccount, localEvent.id!!))
    }

    @Test
    fun testNumInstances_Recurring_ManyInstances() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event over two years"
            rRules.add(RRule("FREQ=DAILY;UNTIL=20240120T010203Z"))
        }
        val localEvent = AndroidEvent(calendar, event, null, null, null, 0)
        localEvent.add()

        assertEquals(
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q)
                365 * 2       // Android <10: does not include UNTIL (incorrect!)
            else
                365 * 2 + 1,  // Android ≥10: includes UNTIL (correct)
            AndroidEvent.numInstances(client, testAccount, localEvent.id!!)
        )
    }

    @Test
    fun testNumInstances_RecurringWithExceptions() {
        val event = Event().apply {
            dtStart = DtStart("20220120T010203Z")
            summary = "Event with 6 instances"
            rRules.add(RRule("FREQ=DAILY;COUNT=6"))
            exceptions.add(Event().apply {
                recurrenceId = RecurrenceId("20220122T010203Z")
                dtStart = DtStart("20220122T130203Z")
                summary = "Exception on 3rd day"
            })
            exceptions.add(Event().apply {
                recurrenceId = RecurrenceId("20220124T010203Z")
                dtStart = DtStart("20220122T160203Z")
                summary = "Exception on 5th day"
            })
        }
        val localEvent = AndroidEvent(calendar, event, "filename.ics", null, null, 0)
        localEvent.add()

        calendar.getEvent(localEvent.id!!)!!

        assertEquals(6, AndroidEvent.numInstances(client, testAccount, localEvent.id!!))
    }

}