/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Entity
import android.provider.CalendarContract.ACCOUNT_TYPE_LOCAL
import android.provider.CalendarContract.AUTHORITY
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Reminders
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.util.AndroidTimeUtils
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import at.bitfire.synctools.storage.calendar.EventAndExceptions
import at.bitfire.synctools.test.InitCalendarProviderRule
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.CuType
import net.fortuna.ical4j.model.parameter.Email
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
import net.fortuna.ical4j.util.TimeZones
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URI
import java.time.Period

class LegacyAndroidEventBuilder2Test {

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

    private fun buildEventAndExceptions(automaticDates: Boolean, eventBuilder: Event.() -> Unit): EventAndExceptions {
        val event = Event().apply {
            if (automaticDates)
                dtStart = DtStart(DateTime())
            eventBuilder()
        }

        return LegacyAndroidEventBuilder2(
            calendar = calendar,
            event = event,
            id = null,
            syncId = "some sync ID",
            eTag = null,
            scheduleTag = null,
            flags = 0
        ).build()
    }

    private fun buildEvent(automaticDates: Boolean, eventBuilder: Event.() -> Unit) =
        buildEventAndExceptions(automaticDates, eventBuilder).main

    private fun firstExtendedProperty(entity: Entity) =
        entity.subValues.firstOrNull { it.uri == ExtendedProperties.CONTENT_URI }?.values?.getAsString(ExtendedProperties.VALUE)

    private fun firstUnknownProperty(entity: Entity): Property? {
        val rawValue = firstExtendedProperty(entity)
        return if (rawValue != null)
            UnknownProperty.fromJsonString(rawValue)
        else
            null
    }

    @Test
    fun testBuildEvent_NonAllDay_NoDtEnd_NoDuration_NonRecurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzVienna)
        }
        assertEquals(0, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals(1591007400000L, entity.entityValues.getAsLong(Events.DTEND))
        assertEquals(tzVienna.id, entity.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun testBuildEvent_NonAllDay_NoDtEnd_NoDuration_Recurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzVienna)
            rRules += RRule("FREQ=DAILY;COUNT=5")
            rRules += RRule("FREQ=WEEKLY;COUNT=10")
            rDates += RDate(DateList("20210601T123000", Value.DATE_TIME, tzVienna))
        }
        assertEquals(0, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals("P0D", entity.entityValues.getAsString(Events.DURATION))
        assertNull(entity.entityValues.get(Events.DTEND))
        assertNull(entity.entityValues.get(Events.EVENT_END_TIMEZONE))

        assertEquals("FREQ=DAILY;COUNT=5\nFREQ=WEEKLY;COUNT=10", entity.entityValues.getAsString(Events.RRULE))
        assertEquals("${tzVienna.id};20200601T123000,20210601T123000", entity.entityValues.getAsString(Events.RDATE))
    }

    @Test
    fun testBuildEvent_NonAllDay_NoDtEnd_Duration_NonRecurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzVienna)
            duration = Duration(null, "PT1H30M")
        }
        assertEquals(0, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals(1591007400000L + 90*60000, entity.entityValues.getAsLong(Events.DTEND))
        assertEquals(tzVienna.id, entity.entityValues.get(Events.EVENT_END_TIMEZONE))
        assertNull(entity.entityValues.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_NonAllDayUtc_NoDtEnd_Duration_NonRecurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart(DateTime("20200601T103000Z").apply { isUtc = true })
            duration = Duration(null, "PT1H30M")
        }
        assertEquals(0, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(TimeZones.getUtcTimeZone().id, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals(1591007400000L + 90*60000, entity.entityValues.getAsLong(Events.DTEND))
        assertEquals(TimeZones.getUtcTimeZone().id, entity.entityValues.get(Events.EVENT_END_TIMEZONE))
        assertNull(entity.entityValues.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_NonAllDay_NoDtEnd_Duration_Recurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzVienna)
            duration = Duration(null, "PT1H30M")
            rDates += RDate(DateList("20200602T113000", Value.DATE_TIME, tzVienna))
        }
        assertEquals(0, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals("PT1H30M", entity.entityValues.getAsString(Events.DURATION))
        assertNull(entity.entityValues.get(Events.DTEND))
        assertNull(entity.entityValues.get(Events.EVENT_END_TIMEZONE))

        assertEquals("${tzVienna.id};20200601T123000,20200602T113000", entity.entityValues.get(Events.RDATE))
    }

    @Test
    fun testBuildEvent_NonAllDay_DtEnd_NoDuration_NonRecurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzVienna)
            dtEnd = DtEnd("20200602T143000", tzShanghai)
        }
        assertEquals(0, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals(1591079400000L, entity.entityValues.getAsLong(Events.DTEND))
        assertEquals(tzShanghai.id, entity.entityValues.get(Events.EVENT_END_TIMEZONE))
        assertNull(entity.entityValues.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_NonAllDay_DtEnd_NoDuration_Recurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzShanghai)
            dtEnd = DtEnd("20200601T123000", tzVienna)
            rDates += RDate(DateList("20200701T123000,20200702T123000", Value.DATE_TIME, tzVienna))
            rDates += RDate(DateList("20200801T123000,20200802T123000", Value.DATE_TIME, tzShanghai))
        }
        assertEquals(0, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1590985800000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(tzShanghai.id, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals("PT6H", entity.entityValues.getAsString(Events.DURATION))
        assertNull(entity.entityValues.get(Events.DTEND))
        assertNull(entity.entityValues.get(Events.EVENT_END_TIMEZONE))

        assertEquals("${tzShanghai.id};20200601T123000,20200701T183000,20200702T183000,20200801T123000,20200802T123000", entity.entityValues.getAsString(Events.RDATE))
    }

    @Test
    fun testBuildEvent_NonAllDay_DtEnd_NoDuration_Recurring_InfiniteRruleAndRdate() {
        val entity  = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzShanghai)
            dtEnd = DtEnd("20200601T123000", tzVienna)
            rRules += RRule(
                Recur("FREQ=DAILY;INTERVAL=2")
            )
            rDates += RDate(DateList("20200701T123000,20200702T123000", Value.DATE_TIME, tzVienna))
        }

        assertNull(entity.entityValues.get(Events.RDATE))
        assertEquals("FREQ=DAILY;INTERVAL=2", entity.entityValues.get(Events.RRULE))
    }

    @Test
    fun testBuildEvent_NonAllDay_DtEnd_Duration_NonRecurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzVienna)
            dtEnd = DtEnd("20200601T143000", tzVienna)
            duration = Duration(null, "PT1S")
        }
        assertEquals(0, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals(1591014600000L, entity.entityValues.getAsLong(Events.DTEND))
        assertEquals(tzVienna.id, entity.entityValues.get(Events.EVENT_END_TIMEZONE))
        assertNull(entity.entityValues.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_NonAllDay_DtEnd_Duration_Recurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzVienna)
            dtEnd = DtEnd("20200601T143000", tzVienna)
            duration = Duration(null, "PT10S")
            rRules += RRule("FREQ=MONTHLY;COUNT=1")
        }
        assertEquals(0, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1591007400000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(tzVienna.id, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals("PT2H", entity.entityValues.getAsString(Events.DURATION))
        assertNull(entity.entityValues.get(Events.DTEND))
        assertNull(entity.entityValues.get(Events.EVENT_END_TIMEZONE))

        assertEquals("FREQ=MONTHLY;COUNT=1", entity.entityValues.get(Events.RRULE))
    }

    @Test
    fun testBuildEvent_AllDay_NoDtEnd_NoDuration_NonRecurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
        }
        assertEquals(1, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals(1591056000000L, entity.entityValues.getAsLong(Events.DTEND))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, entity.entityValues.get(Events.EVENT_END_TIMEZONE))
        assertNull(entity.entityValues.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_AllDay_NoDtEnd_NoDuration_Recurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
            rRules += RRule("FREQ=MONTHLY;COUNT=3")
        }
        assertEquals(1, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals("P1D", entity.entityValues.getAsString(Events.DURATION))
        assertNull(entity.entityValues.get(Events.DTEND))
        assertNull(entity.entityValues.get(Events.EVENT_END_TIMEZONE))

        assertEquals("FREQ=MONTHLY;COUNT=3", entity.entityValues.get(Events.RRULE))
    }

    @Test
    fun testBuildEvent_AllDay_NoDtEnd_Duration_NonRecurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
            duration = Duration(null, "P2W1D")
        }
        assertEquals(1, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals(1592265600000L, entity.entityValues.getAsLong(Events.DTEND))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, entity.entityValues.get(Events.EVENT_END_TIMEZONE))
        assertNull(entity.entityValues.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_AllDay_NoDtEnd_Duration_Recurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
            duration = Duration(null, "P2D")
            rRules += RRule("FREQ=YEARLY;BYMONTH=4;BYDAY=-1SU")
        }
        assertEquals(1, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals("P2D", entity.entityValues.getAsString(Events.DURATION))
        assertNull(entity.entityValues.get(Events.DTEND))
        assertNull(entity.entityValues.get(Events.EVENT_END_TIMEZONE))

        assertEquals("FREQ=YEARLY;BYMONTH=4;BYDAY=-1SU", entity.entityValues.get(Events.RRULE))
    }

    @Test
    fun testBuildEvent_AllDay_DtEnd_NoDuration_NonRecurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
            dtEnd = DtEnd(Date("20200701"))
        }
        assertEquals(1, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals(1593561600000L, entity.entityValues.getAsLong(Events.DTEND))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, entity.entityValues.get(Events.EVENT_END_TIMEZONE))
        assertNull(entity.entityValues.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_AllDay_DtEnd_NoDuration_Recurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
            dtEnd = DtEnd(Date("20200701"))
            rDates += RDate(DateList("20210601", Value.DATE))
            rDates += RDate(DateList("20220601T120030", Value.DATE_TIME))
        }
        assertEquals(1, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals("P30D", entity.entityValues.getAsString(Events.DURATION))
        assertNull(entity.entityValues.get(Events.DTEND))
        assertNull(entity.entityValues.get(Events.EVENT_END_TIMEZONE))

        assertEquals("20200601T000000Z,20210601T000000Z,20220601T000000Z", entity.entityValues.get(Events.RDATE))
    }

    @Test
    fun testBuildEvent_AllDay_DtEnd_Duration_NonRecurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
            dtEnd = DtEnd(Date("20200701"))
            duration = Duration(null, "PT5M")
        }
        assertEquals(1, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals(1593561600000L, entity.entityValues.getAsLong(Events.DTEND))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, entity.entityValues.get(Events.EVENT_END_TIMEZONE))
        assertNull(entity.entityValues.get(Events.DURATION))
    }

    @Test
    fun testBuildEvent_AllDay_DtEnd_Duration_Recurring() {
        val entity  = buildEvent(false) {
            dtStart = DtStart(Date("20200601"))
            dtEnd = DtEnd(Date("20200701"))
            duration = Duration(null, "PT1M")
            rRules += RRule("FREQ=DAILY;COUNT=1")
        }
        assertEquals(1, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1590969600000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(AndroidTimeUtils.TZID_ALLDAY, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals("P30D", entity.entityValues.getAsString(Events.DURATION))
        assertNull(entity.entityValues.get(Events.DTEND))
        assertNull(entity.entityValues.get(Events.EVENT_END_TIMEZONE))

        assertEquals("FREQ=DAILY;COUNT=1", entity.entityValues.get(Events.RRULE))
    }

    @Test
    fun testBuildEvent_FloatingTimes() {
        val entity  = buildEvent(false) {
            dtStart = DtStart("20200601T123000")
            dtEnd = DtEnd("20200601T123001")
        }
        assertEquals(0, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(DateTime("20200601T123000", tzDefault).time, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(tzIdDefault, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals(DateTime("20200601T123001", tzDefault).time, entity.entityValues.getAsLong(Events.DTEND))
        assertEquals(tzIdDefault, entity.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun testBuildEvent_FloatingTimesInRecurrenceDates() {
        val entity  = buildEvent(false) {
            dtStart = DtStart("20200601T123000", tzShanghai)
            duration = Duration(null, "PT5M30S")
            rDates += RDate(DateList("20200602T113000", Value.DATE_TIME))
            exDates += ExDate(DateList("20200602T113000", Value.DATE_TIME))
        }
        assertEquals(0, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1590985800000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(tzShanghai.id, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals("PT5M30S", entity.entityValues.getAsString(Events.DURATION))
        assertNull(entity.entityValues.get(Events.EVENT_END_TIMEZONE))

        val rewritten = DateTime("20200602T113000")
        rewritten.timeZone = tzShanghai
        assertEquals("${tzShanghai.id};20200601T123000,$rewritten", entity.entityValues.get(Events.RDATE))
        assertEquals("$tzIdDefault;20200602T113000", entity.entityValues.get(Events.EXDATE))
    }

    @Test
    fun testBuildEvent_UTC() {
        val entity  = buildEvent(false) {
            dtStart = DtStart(DateTime(1591014600000L), true)
            dtEnd = DtEnd(DateTime(1591021801000L), true)
        }
        assertEquals(0, entity.entityValues.getAsInteger(Events.ALL_DAY))

        assertEquals(1591014600000L, entity.entityValues.getAsLong(Events.DTSTART))
        assertEquals(TimeZones.UTC_ID, entity.entityValues.get(Events.EVENT_TIMEZONE))

        assertEquals(1591021801000L, entity.entityValues.getAsLong(Events.DTEND))
        assertEquals(TimeZones.UTC_ID, entity.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun testBuildEvent_Color_WhenNotAvailable() {
        buildEvent(true) {
            color = Css3Color.darkseagreen
        }.let { result ->
            assertNull(result.entityValues.getAsString(Events.CALENDAR_COLOR_KEY))
        }
    }

    @Test
    fun testBuildEvent_Color_WhenAvailable() {
        val provider = AndroidCalendarProvider(testAccount, client)
        provider.provideCss3ColorIndices()
        buildEvent(true) {
            color = Css3Color.darkseagreen
        }.let { result ->
            assertEquals(Css3Color.darkseagreen.name, result.entityValues.getAsString(Events.EVENT_COLOR_KEY))
        }
    }

    @Test
    fun testBuildEvent_Organizer_NotGroupScheduled() {
        buildEvent(true) {
            organizer = Organizer("mailto:organizer@example.com")
        }.let { result ->
            assertNull(result.entityValues.getAsString(Events.ORGANIZER))
        }
    }

    @Test
    fun testBuildEvent_Organizer_MailTo() {
        buildEvent(true) {
            organizer = Organizer("mailto:organizer@example.com")
            attendees += Attendee("mailto:attendee@example.com")
        }.let { result ->
            assertEquals("organizer@example.com", result.entityValues.getAsString(Events.ORGANIZER))
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
            assertEquals("organizer@example.com", result.entityValues.getAsString(Events.ORGANIZER))
        }
    }

    @Test
    fun testBuildEvent_Organizer_NotEmail() {
        buildEvent(true) {
            organizer = Organizer("local-id:user")
            attendees += Attendee("mailto:attendee@example.com")
        }.let { result ->
            assertNull(result.entityValues.getAsString(Events.ORGANIZER))
        }
    }

    @Test
    fun testBuildEvent_Status_Confirmed() {
        buildEvent(true) {
            status = Status.VEVENT_CONFIRMED
        }.let { result ->
            assertEquals(Events.STATUS_CONFIRMED, result.entityValues.getAsInteger(Events.STATUS))
        }
    }

    @Test
    fun testBuildEvent_Status_Cancelled() {
        buildEvent(true) {
            status = Status.VEVENT_CANCELLED
        }.let { result ->
            assertEquals(Events.STATUS_CANCELED, result.entityValues.getAsInteger(Events.STATUS))
        }
    }

    @Test
    fun testBuildEvent_Status_Tentative() {
        buildEvent(true) {
            status = Status.VEVENT_TENTATIVE
        }.let { result ->
            assertEquals(Events.STATUS_TENTATIVE, result.entityValues.getAsInteger(Events.STATUS))
        }
    }

    @Test
    fun testBuildEvent_Status_Invalid() {
        buildEvent(true) {
            status = Status.VTODO_IN_PROCESS
        }.let { result ->
            assertEquals(Events.STATUS_TENTATIVE, result.entityValues.getAsInteger(Events.STATUS))
        }
    }

    @Test
    fun testBuildEvent_Status_None() {
        buildEvent(true) {
        }.let { result ->
            assertNull(result.entityValues.getAsInteger(Events.STATUS))
        }
    }

    @Test
    fun testBuildEvent_Opaque_True() {
        buildEvent(true) {
            opaque = true
        }.let { result ->
            assertEquals(Events.AVAILABILITY_BUSY, result.entityValues.getAsInteger(Events.AVAILABILITY))
        }
    }

    @Test
    fun testBuildEvent_Opaque_False() {
        buildEvent(true) {
            opaque = false
        }.let { result ->
            assertEquals(Events.AVAILABILITY_FREE, result.entityValues.getAsInteger(Events.AVAILABILITY))
        }
    }

    @Test
    fun testBuildEvent_Classification_Public() {
        buildEvent(true) {
            classification = Clazz.PUBLIC
        }.let { result ->
            assertEquals(Events.ACCESS_PUBLIC, result.entityValues.getAsInteger(Events.ACCESS_LEVEL))
            assertNull(firstUnknownProperty(result))
        }
    }

    @Test
    fun testBuildEvent_Classification_Private() {
        buildEvent(true) {
            classification = Clazz.PRIVATE
        }.let { result ->
            assertEquals(Events.ACCESS_PRIVATE, result.entityValues.getAsInteger(Events.ACCESS_LEVEL))
            assertNull(firstUnknownProperty(result))
        }
    }

    @Test
    fun testBuildEvent_Classification_Confidential() {
        buildEvent(true) {
            classification = Clazz.CONFIDENTIAL
        }.let { result ->
            assertEquals(Events.ACCESS_CONFIDENTIAL, result.entityValues.getAsInteger(Events.ACCESS_LEVEL))
            assertEquals(Clazz.CONFIDENTIAL, firstUnknownProperty(result))
        }
    }

    @Test
    fun testBuildEvent_Classification_Custom() {
        buildEvent(true) {
            classification = Clazz("TOP-SECRET")
        }.let { result ->
            assertEquals(Events.ACCESS_PRIVATE, result.entityValues.getAsInteger(Events.ACCESS_LEVEL))
            assertEquals(Clazz("TOP-SECRET"), firstUnknownProperty(result))
        }
    }

    @Test
    fun testBuildEvent_Classification_None() {
        buildEvent(true) {
        }.let { result ->
            assertEquals(Events.ACCESS_DEFAULT, result.entityValues.getAsInteger(Events.ACCESS_LEVEL))
            assertNull(firstUnknownProperty(result))
        }
    }

    @Test
    fun testBuildEvent_UID2445() {
        buildEvent(true) {
            uid = "event1@example.com"
        }.let { result ->
            assertEquals("event1@example.com", result.entityValues.getAsString(Events.UID_2445))
        }
    }


    private fun firstReminder(entity: Entity) =
        entity.subValues.firstOrNull { it.uri == Reminders.CONTENT_URI }?.values

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


    private fun firstAttendee(entity: Entity) =
        entity.subValues.firstOrNull { it.uri == Attendees.CONTENT_URI }?.values

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


    // exceptions → test with EventAndException

    private fun firstException(eventAndExceptions: EventAndExceptions) =
        eventAndExceptions.exceptions.first()

    @Test
    fun testBuildException_NonAllDay() {
        buildEventAndExceptions(false) {
            dtStart = DtStart("20200706T193000", tzVienna)
            rRules += RRule("FREQ=DAILY;COUNT=10")
            exceptions += Event().apply {
                recurrenceId = RecurrenceId("20200707T193000", tzVienna)
                dtStart = DtStart("20200706T203000", tzShanghai)
                summary = "Event moved to one hour later"
            }
        }.let { result ->
            val values = result.main.entityValues
            assertEquals(1594056600000L, values.getAsLong(Events.DTSTART))
            assertEquals(tzVienna.id, values.getAsString(Events.EVENT_TIMEZONE))
            assertEquals(0, values.getAsInteger(Events.ALL_DAY))
            assertEquals("FREQ=DAILY;COUNT=10", values.getAsString(Events.RRULE))
            firstException(result).let { exceptionEntity ->
                val exception = exceptionEntity.entityValues
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
        buildEventAndExceptions(false) {
            dtStart = DtStart("20200706T193000", tzVienna)
            rRules += RRule("FREQ=DAILY;COUNT=10")
            exceptions += Event().apply {
                recurrenceId = RecurrenceId(Date("20200707"))   // illegal! should be rewritten to DateTime("20200707T193000", tzVienna)
                dtStart = DtStart("20200706T203000", tzShanghai)
                summary = "Event moved to one hour later"
            }
        }.let { result ->
            val values = result.main.entityValues
            assertEquals(1594056600000L, values.getAsLong(Events.DTSTART))
            assertEquals(tzVienna.id, values.getAsString(Events.EVENT_TIMEZONE))
            assertEquals(0, values.getAsInteger(Events.ALL_DAY))
            assertEquals("FREQ=DAILY;COUNT=10", values.getAsString(Events.RRULE))
            firstException(result).let { exceptionEntity ->
                val exception = exceptionEntity.entityValues
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
        buildEventAndExceptions(false) {
            dtStart = DtStart(Date("20200706"))
            rRules += RRule("FREQ=WEEKLY;COUNT=3")
            exceptions += Event().apply {
                recurrenceId = RecurrenceId(Date("20200707"))
                dtStart = DtStart("20200706T123000", tzVienna)
                summary = "Today not an all-day event"
            }
        }.let { result ->
            val values = result.main.entityValues
            assertEquals(1593993600000L, values.getAsLong(Events.DTSTART))
            assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.getAsString(Events.EVENT_TIMEZONE))
            assertEquals(1, values.getAsInteger(Events.ALL_DAY))
            assertEquals("FREQ=WEEKLY;COUNT=3", values.getAsString(Events.RRULE))
            firstException(result).let { exceptionEntity ->
                val exception = exceptionEntity.entityValues
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
        buildEventAndExceptions(false) {
            dtStart = DtStart(Date("20200706"))
            rRules += RRule("FREQ=WEEKLY;COUNT=3")
            exceptions += Event().apply {
                recurrenceId = RecurrenceId("20200707T000000", tzVienna)     // illegal! should be rewritten to Date("20200707")
                dtStart = DtStart("20200706T123000", tzVienna)
                summary = "Today not an all-day event"
            }
        }.let { result ->
            val values = result.main.entityValues
            assertEquals(1593993600000L, values.getAsLong(Events.DTSTART))
            assertEquals(AndroidTimeUtils.TZID_ALLDAY, values.getAsString(Events.EVENT_TIMEZONE))
            assertEquals(1, values.getAsInteger(Events.ALL_DAY))
            assertEquals("FREQ=WEEKLY;COUNT=3", values.getAsString(Events.RRULE))
            firstException(result).let { exceptionEntity ->
                val exception = exceptionEntity.entityValues
                assertEquals(1594080000000L, exception.getAsLong(Events.ORIGINAL_INSTANCE_TIME))
                assertEquals(1, exception.getAsInteger(Events.ORIGINAL_ALL_DAY))
                assertEquals(1594031400000L, exception.getAsLong(Events.DTSTART))
                assertEquals(0, exception.getAsInteger(Events.ALL_DAY))
                assertEquals("Today not an all-day event", exception.getAsString(Events.TITLE))
            }
        }
    }

}