/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.validation

import at.bitfire.ical4android.Event
import at.bitfire.ical4android.EventReader
import net.fortuna.ical4j.model.Date
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.TzId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class EventValidatorTest {

    companion object {
        val tzReg: TimeZoneRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    }
    
    val eventReader = EventReader()


    // DTSTART and DTEND

    @Test
    fun testCorrectStartAndEndTime_NoDtStart_EndDateTime() {
        val event = Event().apply {
            // no dtStart
            dtEnd = DtEnd(DateTime("20000105T000000"))  // DATETIME
        }
        EventValidator.correctStartAndEndTime(event)
        assertEquals(event.dtEnd!!.date, event.dtStart!!.date)
    }

    @Test
    fun testCorrectStartAndEndTime_NoDtStart_EndDate() {
        val event = Event().apply {
            // no dtStart
            dtEnd = DtEnd(Date("20000105"))  // DATE
        }
        EventValidator.correctStartAndEndTime(event)
        assertEquals(event.dtEnd!!.date, event.dtStart!!.date)
    }

    @Test
    fun testCorrectStartAndEndTime_NoDtStart_NoDtEnd() {
        val event = Event(/* no dtStart, no dtEnd */)

        val time = System.currentTimeMillis()
        EventValidator.correctStartAndEndTime(event)

        assertTrue(event.dtStart!!.date.time in (time-1000)..<(time+1000))   // within 2 seconds
        assertNull(event.dtEnd)
    }

    @Test
    fun testCorrectStartAndEndTime_DtEndBeforeDtStart() {
        val event = Event().apply {
            dtStart = DtStart(DateTime("20000105T001100"))              // DATETIME
            dtEnd = DtEnd(DateTime("20000105T000000"))                  // DATETIME
        }
        assertTrue(event.dtStart!!.date.time > event.dtEnd!!.date.time)
        EventValidator.correctStartAndEndTime(event)
        assertNull(event.dtEnd)

        val event1 = eventReader.readEvents(StringReader(
            "BEGIN:VCALENDAR\n" +
               "BEGIN:VEVENT\n" +
               "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
               "DTSTART;VALUE=DATE:20211117\n" +                           // DATE
               "DTEND;VALUE=DATE:20211116\n" +                             // DATE
               "END:VEVENT\n" +
               "END:VCALENDAR")).first()
        assertNull(event1.dtEnd)
    }


    // RRULE UNTIL and DTSTART of same type (DATETIME/DATE)

    @Test
    fun testSameTypeForDtStartAndRruleUntil_DtStartAndRruleUntilAreBothDateTimeOrDate() {
        // should do nothing when types are the same

        val event = Event().apply {
            dtStart = DtStart(DateTime("20211115T001100Z"))               // DATETIME (UTC)
            rRules.add(RRule("FREQ=MONTHLY;UNTIL=20251214T001100Z"))      // DATETIME (UTC)
        }
        assertEquals(DateTime("20211115T001100Z"), event.dtStart!!.date)
        assertEquals("FREQ=MONTHLY;UNTIL=20251214T001100Z", event.rRules.joinToString())
        EventValidator.sameTypeForDtStartAndRruleUntil(event.dtStart!!, event.rRules)
        assertEquals(DateTime("20211115T001100Z"), event.dtStart!!.date)
        assertEquals("FREQ=MONTHLY;UNTIL=20251214T001100Z", event.rRules.joinToString())

        val event1 = eventReader.readEvents(StringReader(
            "BEGIN:VCALENDAR\n" +
               "BEGIN:VEVENT\n" +
               "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
               "DTSTART;VALUE=DATE:20211115\n" +                               // DATE
               "RRULE:FREQ=MONTHLY;UNTIL=20231214;BYMONTHDAY=15\n" +           // DATE
               "END:VEVENT\n" +
               "END:VCALENDAR")).first()
        assertEquals("FREQ=MONTHLY;UNTIL=20231214;BYMONTHDAY=15", event1.rRules.joinToString())

        val event2 = eventReader.readEvents(StringReader(
            "BEGIN:VCALENDAR\n" +
               "BEGIN:VEVENT\n" +
               "UID:381fb26b-2da5-4dd2-94d7-2e0874128aa7\n" +
               "DTSTART;VALUE=DATE:20080215\n" +                               // DATE
               "RRULE:FREQ=YEARLY;UNTIL=20230216;BYMONTHDAY=15\n" +            // DATE
               "END:VEVENT\n" +
               "END:VCALENDAR")).first()
        assertEquals("FREQ=YEARLY;UNTIL=20230216;BYMONTHDAY=15", event2.rRules.joinToString())
    }

    @Test
    fun testSameTypeForDtStartAndRruleUntil_DtStartIsDateAndRruleUntilIsDateTime() {
        // should remove (possibly existing) time in RRULE if DTSTART value is of type DATE (not DATETIME)
        // we want time to be cut off hard, not taking time zones into account risking the date could flip when time is close to midnight

        val event = Event().apply {
            dtStart = DtStart(Date("20211115"))                         // DATE
            rRules.add(RRule("FREQ=MONTHLY;UNTIL=20211214T235959Z"))    // DATETIME (UTC), close to flip up
        }
        assertEquals(
            DateTime("20211214T235959Z"),
            event.rRules.first().recur.until
        )
        EventValidator.sameTypeForDtStartAndRruleUntil(event.dtStart!!, event.rRules)
        assertEquals("FREQ=MONTHLY;UNTIL=20211214", event.rRules.joinToString())

        val event1 = eventReader.readEvents(
            StringReader(
                "BEGIN:VCALENDAR\n" +
                        "BEGIN:VEVENT\n" +
                        "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                        "DTSTART;VALUE=DATE:20211115\n" +                             // DATE
                        "RRULE:FREQ=MONTHLY;UNTIL=20211214T235959;BYMONTHDAY=15\n" +  // DATETIME (no timezone), close to flip up
                        "END:VEVENT\n" +
                        "END:VCALENDAR"
            )
        ).first()
        assertEquals(1639440000000, event1.rRules.first().recur.until.time)
        assertEquals("FREQ=MONTHLY;UNTIL=20211214;BYMONTHDAY=15", event1.rRules.joinToString())
    }

    @Test
    fun testSameTypeForDtStartAndRruleUntil_DtStartIsDateAndRruleUntilIsDateTime_2() {
        val event2 = Event().apply {
            dtStart = DtStart(Date("20080215"))                                                     // DATE (local/system time zone)
            rRules.add(RRule("FREQ=YEARLY;TZID=Europe/Vienna;UNTIL=20230218T000000;BYMONTHDAY=15")) // DATETIME (with timezone), close to flip down
        }
        // NOTE: ical4j will:
        //  - ignore the time zone of the RRULE (TZID=Europe/Vienna)
        //  - use the timezone from DTSTART to determine the time value (timezone of DTSTART is local/system for a DATE)
        //  - take DST into account
        // Because of this when running the test in a different timezone the date may flip down before we cut off time, making the test hard to predict.
        // As it does not happen often, for the sake of simplicity we just accept either
        EventValidator.sameTypeForDtStartAndRruleUntil(event2.dtStart!!, event2.rRules)
        assertTrue(
            "FREQ=YEARLY;UNTIL=20230218;BYMONTHDAY=15" == event2.rRules.joinToString() ||
            "FREQ=YEARLY;UNTIL=20230217;BYMONTHDAY=15" == event2.rRules.joinToString()
        )
    }

    @Test
    fun testSameTypeForDtStartAndRruleUntil_DtStartIsDateTimeWithTzAndRruleUntilIsDate() {
        // should add (possibly missing) time in UNTIL if DTSTART value is of type DATETIME (not just DATE)

        val event = Event().apply {
            dtStart = DtStart(DateTime("20110605T001100Z"))         // DATETIME (UTC)
            rRules.add(RRule("FREQ=MONTHLY;UNTIL=20211214"))        // DATE
        }
        assertEquals("FREQ=MONTHLY;UNTIL=20211214", event.rRules.joinToString())
        EventValidator.sameTypeForDtStartAndRruleUntil(event.dtStart!!, event.rRules)
        assertEquals("FREQ=MONTHLY;UNTIL=20211214T001100Z", event.rRules.joinToString())

        val event1 = eventReader.readEvents(StringReader(
            "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "UID:51d8529a-5844-4609-918b-2891b855e0e8\n" +
                "DTSTART;TZID=America/New_York:20211111T053000\n" +     // DATETIME (with timezone)
                "RRULE:FREQ=MONTHLY;UNTIL=20211214;BYMONTHDAY=15\n" +   // DATE
                "END:VEVENT\n" +
                "END:VCALENDAR")).first()
        assertEquals("FREQ=MONTHLY;UNTIL=20211214T103000Z;BYMONTHDAY=15", event1.rRules.joinToString())
    }

    @Test
    fun testSameTypeForDtStartAndRruleUntil_DtStartIsDateTimeWithoutTzAndRruleUntilIsDate() {
        // should add (possibly missing) time in UNTIL if DTSTART value is of type DATETIME (not just DATE)

        val event = Event().apply {
            dtStart = DtStart(DateTime("20110605T001100Z"))         // DATETIME (UTC)
            rRules.add(RRule("FREQ=MONTHLY;UNTIL=20211214"))        // DATE
        }
        assertEquals("FREQ=MONTHLY;UNTIL=20211214", event.rRules.joinToString())
        EventValidator.sameTypeForDtStartAndRruleUntil(event.dtStart!!, event.rRules)
        assertEquals("FREQ=MONTHLY;UNTIL=20211214T001100Z", event.rRules.joinToString())

        val event2 = eventReader.readEvents(
            StringReader(
                "BEGIN:VCALENDAR\n" +
                        "BEGIN:VEVENT\n" +
                        "UID:381fb26b-2da5-4dd2-94d7-2e0874128aa7\n" +
                        "DTSTART;VALUE=DATETIME:20110605T001100Z\n" +            // DATETIME (UTC)
                        "RRULE:FREQ=YEARLY;UNTIL=20211214;BYMONTHDAY=15\n" +     // DATE
                        "END:VEVENT\n" +
                        "END:VCALENDAR"
            )
        ).first()
        assertEquals("FREQ=YEARLY;UNTIL=20211214T001100Z;BYMONTHDAY=15", event2.rRules.joinToString())
    }


    // RRULE UNTIL time before DTSTART time

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleNoUntil() {
        assertFalse(
            EventValidator.hasUntilBeforeDtStart(
                DtStart(DateTime("20220531T010203")), RRule()
            )
        )
    }


    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_TimeBeforeDtStart_UTC() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(
                DtStart("20220912", tzReg.getTimeZone("UTC")), RRule(
                    Recur.Builder()
                        .frequency(Recur.Frequency.DAILY)
                        .until(DateTime("20220911T235959Z"))
                        .build()
                )
            )
        )
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_TimeBeforeDtStart_noTimezone() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(
                DtStart("20220912"), RRule(
                    Recur.Builder()
                        .frequency(Recur.Frequency.DAILY)
                        .until(DateTime("20220911T235959"))
                        .build()
                )
            )
        )
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_TimeBeforeDtStart_withTimezone() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(
                DtStart("20220912", tzReg.getTimeZone("America/New_York")), RRule(
                    Recur.Builder()
                        .frequency(Recur.Frequency.DAILY)
                        .until(DateTime("20220911T235959", tzReg.getTimeZone("America/New_York")))
                        .build()
                )
            )
        )
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_DateBeforeDtStart() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(
                DtStart("20220531"), RRule(
                    Recur.Builder()
                        .frequency(Recur.Frequency.DAILY)
                        .until(DateTime("20220530T000000"))
                        .build()
                )
            )
        )
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartDate_RRuleUntil_TimeAfterDtStart() {
        assertFalse(
            EventValidator.hasUntilBeforeDtStart(
                DtStart("20200912"), RRule(
                    Recur.Builder()
                        .frequency(Recur.Frequency.DAILY)
                        .until(DateTime("20220912T000001Z"))
                        .build()
                )
            )
        )
    }


    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_DateBeforeDtStart() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(
                DtStart(DateTime("20220531T010203")), RRule(
                    Recur.Builder()
                        .frequency(Recur.Frequency.DAILY)
                        .until(Date("20220530"))
                        .build()
                )
            )
        )
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_TimeBeforeDtStart() {
        assertTrue(
            EventValidator.hasUntilBeforeDtStart(
                DtStart(DateTime("20220531T010203")), RRule(
                    Recur.Builder()
                        .frequency(Recur.Frequency.DAILY)
                        .until(DateTime("20220531T010202"))
                        .build()
                )
            )
        )
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_TimeAtDtStart() {
        assertFalse(
            EventValidator.hasUntilBeforeDtStart(
                DtStart(DateTime("20220531T010203")), RRule(
                    Recur.Builder()
                        .frequency(Recur.Frequency.DAILY)
                        .until(DateTime("20220531T010203"))
                        .build()
                )
            )
        )
    }

    @Test
    fun testHasUntilBeforeDtStart_DtStartTime_RRuleUntil_TimeAfterDtStart() {
        assertFalse(
            EventValidator.hasUntilBeforeDtStart(
                DtStart(DateTime("20220531T010203")), RRule(
                    Recur.Builder()
                        .frequency(Recur.Frequency.DAILY)
                        .until(DateTime("20220531T010204"))
                        .build()
                )
            )
        )
    }

    @Test
    fun testRemoveRecurrencesOfRecurringWithExceptions() {
        // Test manually created event
        val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
        val tz = tzRegistry.getTimeZone("Europe/Paris")
        val manualEvent = Event().apply {
            dtStart = DtStart("20240219T130000", tz)
            dtEnd = DtEnd("20240219T140000", tz)
            summary = "recurring event"
            rRules.add(RRule(Recur.Builder()                        // Should keep this RRULE
                .frequency(Recur.Frequency.DAILY)
                .interval(1)
                .count(5)
                .build()))
            sequence = 0
            uid = "76c08fb1-99a3-41cf-b482-2d3b06648814"
            exceptions.add(Event().apply {
                dtStart = DtStart("20240221T110000", tz)
                dtEnd = DtEnd("20240221T120000", tz)
                recurrenceId = RecurrenceId("20240221T130000", tz)
                sequence = 0
                summary = "exception of recurring event"
                rRules.addAll(listOf(
                    RRule(Recur.Builder()                           // but remove this one
                        .frequency(Recur.Frequency.DAILY)
                        .count(6)
                        .interval(2)
                        .build()),
                    RRule(Recur.Builder()                           // and this one
                        .frequency(Recur.Frequency.DAILY)
                        .count(6)
                        .interval(2)
                        .build())
                ))
                rDates.addAll(listOf(
                    RDate(DateList(Value("19970714T123000Z"))),
                    RDate(
                        DateList(
                            Value("19960403T020000Z"),
                            TimeZone(
                                VTimeZone(
                                    PropertyList<Property>(1).apply {
                                        add(TzId("US-EASTERN"))
                                    }
                                )
                            )
                        )
                    )
                ))
                exDates.addAll(listOf(
                    ExDate(DateList(Value("19970714T123000Z"))),
                    ExDate(
                        DateList(
                            Value("19960403T020000Z"),
                            TimeZone(
                                VTimeZone(
                                    PropertyList<Property>(1).apply {
                                        add(TzId("US-EASTERN"))
                                    }
                                )
                            )
                        )
                    )
                ))
                uid = "76c08fb1-99a3-41cf-b482-2d3b06648814"
            })
        }
        assertTrue(manualEvent.rRules.size == 1)
        assertTrue(manualEvent.exceptions.first().rRules.size == 2)
        assertTrue(manualEvent.exceptions.first().rDates.size == 2)
        assertTrue(manualEvent.exceptions.first().exDates.size == 2)
        EventValidator.removeRecurrenceOfExceptions(manualEvent.exceptions) // Repair the manually created event
        assertTrue(manualEvent.rRules.size == 1)
        assertTrue(manualEvent.exceptions.first().rRules.isEmpty())
        assertTrue(manualEvent.exceptions.first().rDates.isEmpty())
        assertTrue(manualEvent.exceptions.first().exDates.isEmpty())

        // Test event from reader, the reader will repair the event itself
        val eventFromReader = eventReader.readEvents(StringReader(
            "BEGIN:VCALENDAR\n" +
                "BEGIN:VEVENT\n" +
                "DTSTAMP:20240215T102755Z\n" +
                "SUMMARY:recurring event\n" +
                "DTSTART;TZID=Europe/Paris:20240219T130000\n" +
                "DTEND;TZID=Europe/Paris:20240219T140000\n" +
                "RRULE:FREQ=DAILY;INTERVAL=1;COUNT=5\n" +           // Should keep this RRULE
                "UID:76c08fb1-99a3-41cf-b482-2d3b06648814\n" +
                "END:VEVENT\n" +

                // Exception for the recurring event above
                "BEGIN:VEVENT\n" +
                "DTSTAMP:20240215T102908Z\n" +
                "RECURRENCE-ID;TZID=Europe/Paris:20240221T130000\n" +
                "SUMMARY:exception of recurring event\n" +
                "RRULE:FREQ=DAILY;COUNT=6;INTERVAL=2\n" +           // but remove this one
                "RRULE:FREQ=DAILY;COUNT=6;INTERVAL=2\n" +           // and this one
                "EXDATE;TZID=Europe/Paris:20240704T193000\n" +      // also this
                "RDATE;TZID=US-EASTERN:19970714T083000\n" +         // and this
                "DTSTART;TZID=Europe/Paris:20240221T110000\n" +
                "DTEND;TZID=Europe/Paris:20240221T120000\n" +
                "UID:76c08fb1-99a3-41cf-b482-2d3b06648814\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR"
        )).first()
        assertTrue(eventFromReader.rRules.size == 1)
        assertTrue(eventFromReader.exceptions.first().rRules.isEmpty())
        assertTrue(eventFromReader.exceptions.first().rDates.isEmpty())
        assertTrue(eventFromReader.exceptions.first().exDates.isEmpty())
    }

    @Test
    fun testRemoveRRulesWithUntilBeforeDtStart() {
        val dtStart = DtStart(DateTime("20220531T125304"))
        val rruleBefore = RRule(Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(DateTime("20220531T125303"))
            .build())
        val rruleAfter = RRule(Recur.Builder()
            .frequency(Recur.Frequency.DAILY)
            .until(DateTime("20220531T125305"))
            .build())

        val rrules = mutableListOf(
            rruleBefore,
            rruleAfter
        )
        EventValidator.removeRRulesWithUntilBeforeDtStart(dtStart, rrules)
        assertArrayEquals(arrayOf(
            // rRuleBefore has been removed because RRULE UNTIL is before DTSTART
            rruleAfter
        ), rrules.toTypedArray())
    }


    // helpers

    private fun Iterable<RRule>.joinToString() =
        this.joinToString("\n") { rRule -> rRule.value }

}