/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.calendar

import androidx.test.filters.SmallTest
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.parameter.XParameter
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Uid
import org.json.JSONException
import org.junit.Assert
import org.junit.Test

class UnknownPropertyTest {

    @Test
    @SmallTest
    fun testFromJsonString() {
        val prop = UnknownProperty.fromJsonString("[ \"UID\", \"PropValue\" ]")
        Assert.assertTrue(prop is Uid)
        Assert.assertEquals("UID", prop.name)
        Assert.assertEquals("PropValue", prop.value)
    }

    @Test
    @SmallTest
    fun testFromJsonStringWithParameters() {
        val prop = UnknownProperty.fromJsonString("[ \"ATTENDEE\", \"PropValue\", { \"x-param1\": \"value1\", \"x-param2\": \"value2\" } ]")
        Assert.assertTrue(prop is Attendee)
        Assert.assertEquals("ATTENDEE", prop.name)
        Assert.assertEquals("PropValue", prop.value)
        Assert.assertEquals(2, prop.parameters.size())
        Assert.assertEquals("value1", prop.parameters.getParameter<Parameter>("x-param1").value)
        Assert.assertEquals("value2", prop.parameters.getParameter<Parameter>("x-param2").value)
    }

    @Test(expected = JSONException::class)
    @SmallTest
    fun testFromInvalidJsonString() {
        UnknownProperty.fromJsonString("This isn't JSON")
    }


    @Test
    @SmallTest
    fun testToJsonString() {
        val attendee = Attendee("mailto:test@test.at")
        Assert.assertEquals(
            "ATTENDEE:mailto:test@test.at",
            attendee.toString().trim()
        )

        attendee.parameters.add(Rsvp(true))
        attendee.parameters.add(XParameter("X-My-Param", "SomeValue"))
        Assert.assertEquals(
            "ATTENDEE;RSVP=TRUE;X-My-Param=SomeValue:mailto:test@test.at",
            attendee.toString().trim()
        )
    }

}