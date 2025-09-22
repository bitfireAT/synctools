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
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import androidx.core.content.contentValuesOf
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.LegacyAndroidCalendar
import at.bitfire.ical4android.impl.TestCalendar
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import at.bitfire.synctools.test.InitCalendarProviderRule
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule

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


    private fun populateException(mainBuilder: ContentValues.() -> Unit, exceptionBuilder: ContentValues.() -> Unit) =
        populateEvent(false, asSyncAdapter = true, valuesBuilder = mainBuilder, insertCallback = { id ->
            val exceptionValues = ContentValues()
            exceptionValues.put(Events.CALENDAR_ID, calendar.id)
            exceptionBuilder(exceptionValues)
            client.insert(Events.CONTENT_URI.asSyncAdapter(testAccount), exceptionValues)
        })

}