/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

/*class AndroidCalendarTest {

    companion object {

        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )

        lateinit var provider: ContentProviderClient

        @BeforeClass
        @JvmStatic
        fun connectProvider() {
            provider = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver.acquireContentProviderClient(
                CalendarContract.AUTHORITY)!!
        }

        @AfterClass
        @JvmStatic
        fun closeProvider() {
            provider.closeCompat()
        }

    }

    private val testAccount = Account("ical4android.AndroidCalendarTest", CalendarContract.ACCOUNT_TYPE_LOCAL)

    @Before
    fun prepare() {
        // make sure there are no colors for testAccount
        AndroidCalendar.removeColors(provider, testAccount)
        Assert.assertEquals(0, countColors(testAccount))
    }


    @Test
    fun testManageCalendars() {
        // create calendar
        val info = ContentValues()
        info.put(CalendarContract.Calendars.NAME, "TestCalendar")
        info.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "ical4android Test Calendar")
        info.put(CalendarContract.Calendars.VISIBLE, 0)
        info.put(CalendarContract.Calendars.SYNC_EVENTS, 0)
        val uri = AndroidCalendar.create(testAccount, provider, info)
        Assert.assertNotNull(uri)

        // query calendar
        val calendar = AndroidCalendar.findByID(testAccount, provider, ContentUris.parseId(uri))
        Assert.assertNotNull(calendar)

        // delete calendar
        Assert.assertTrue(calendar.delete())
    }


    @Test
    fun testInsertColors() {
        AndroidCalendar.insertColors(provider, testAccount)
        Assert.assertEquals(Css3Color.entries.size, countColors(testAccount))
    }

    @Test
    fun testInsertColors_AlreadyThere() {
        AndroidCalendar.insertColors(provider, testAccount)
        AndroidCalendar.insertColors(provider, testAccount)
        Assert.assertEquals(Css3Color.entries.size, countColors(testAccount))
    }

    @Test
    fun testRemoveColors() {
        AndroidCalendar.insertColors(provider, testAccount)

        // insert an event with that color
        val cal = TestCalendar.findOrCreate(testAccount, provider)
        try {
            // add event with color
            AndroidEvent(cal, Event().apply {
                dtStart = DtStart("20210314T204200Z")
                dtEnd = DtEnd("20210314T204230Z")
                color = Css3Color.limegreen
                summary = "Test event with color"
            }, "remove-colors").add()

            AndroidCalendar.removeColors(provider, testAccount)
            Assert.assertEquals(0, countColors(testAccount))
        } finally {
            cal.delete()
        }
    }

    private fun countColors(account: Account): Int {
        val uri = CalendarContract.Colors.CONTENT_URI.asSyncAdapter(account)
        provider.query(uri, null, null, null, null)!!.use { cursor ->
            cursor.moveToNext()
            return cursor.count
        }
    }

}*/