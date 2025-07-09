/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL
import android.provider.CalendarContract.AUTHORITY
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Reminders
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import at.bitfire.synctools.storage.toContentValues
import at.bitfire.synctools.test.InitCalendarProviderRule
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.CuType
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.parameter.Role
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Clazz
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.XProperty
import net.fortuna.ical4j.util.TimeZones
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URI
import java.time.Period
import java.util.UUID

class LegacyAndroidEventBuilderTest {

    @get:Rule
    val initCalendarProviderRule = InitCalendarProviderRule.initialize()

    private val testAccount = Account(javaClass.name, ACCOUNT_TYPE_LOCAL)

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzIdDefault = java.util.TimeZone.getDefault().id
    private val tzDefault = tzRegistry.getTimeZone(tzIdDefault)
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")
    private val tzShanghai = tzRegistry.getTimeZone("Asia/Shanghai")

    lateinit var client: ContentProviderClient
    lateinit var calendar: AndroidCalendar

    @Before
    fun setUp() {
        val context = getInstrumentation().targetContext
        client = context.contentResolver.acquireContentProviderClient(AUTHORITY)!!
        calendar = TestCalendar.findOrCreate(testAccount, client)
    }

    @After
    fun tearDown() {
        calendar.delete()
        client.closeCompat()
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
            return cursor.toContentValues()
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
            if (cursor.moveToNext())
                return cursor.toContentValues()
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
            alarms += VAlarm(java.time.Duration.ofMinutes(-10)).apply {
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
            alarms += VAlarm(java.time.Duration.ofMinutes(-10)).apply {
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
            alarms += VAlarm(java.time.Duration.ofSeconds(-120)).apply {
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
            alarms += VAlarm(java.time.Duration.ofSeconds(-120)).apply {
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
            alarms += VAlarm(java.time.Duration.ofSeconds(-10))
        }.let { result ->
            assertEquals(0, firstReminder(result)!!.getAsInteger(Reminders.MINUTES))
        }
    }

    @Test
    fun testBuildReminder_Trigger_RelStart_Duration_Positive() {
        // positive duration -> reminder is AFTER reference time -> negative minutes field
        buildEvent(true) {
            alarms += VAlarm(java.time.Duration.ofMinutes(10))
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
            alarms += VAlarm(java.time.Duration.ofSeconds(-7240)).apply {
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
            alarms += VAlarm(java.time.Duration.ofMinutes(10)).apply {
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
            if (cursor.moveToNext())
                return cursor.toContentValues()
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
            if (cursor.moveToNext())
                return cursor.toContentValues()
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

}