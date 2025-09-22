/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Attendees
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import at.bitfire.synctools.test.assertContentValuesEqual
import io.mockk.every
import io.mockk.mockk
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.CuType
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.Role
import net.fortuna.ical4j.model.property.Attendee
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI

@RunWith(RobolectricTestRunner::class)
class AttendeesBuilderTest {

    private val accountName = "owner@example.com"
    private val mockCalendar = mockk<AndroidCalendar> {
        every { ownerAccount } returns accountName
    }

    private val builder = AttendeesBuilder(mockCalendar)

    @Test
    fun `Attendee is email address`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("mailto:attendee1@example.com")
            },
            main = Event(),
            to = result
        )
        assertAttendee(result, Attendees.ATTENDEE_EMAIL to "attendee1@example.com")
    }

    @Test
    fun `Attendee is HTTPS URL`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("https://example.com/principals/attendee")
            },
            main = Event(),
            to = result
        )
        assertAttendee(result,
            Attendees.ATTENDEE_ID_NAMESPACE to "https",
            Attendees.ATTENDEE_IDENTITY to "//example.com/principals/attendee"
        )
    }

    @Test
    fun `Attendee is custom URI with EMAIL parameter`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("sample:uri").apply {
                    parameters.add(Email("attendee1@example.com"))
                }
            },
            main = Event(),
            to = result
        )
        assertAttendee(result,
            Attendees.ATTENDEE_ID_NAMESPACE to "sample",
            Attendees.ATTENDEE_IDENTITY to "uri",
            Attendees.ATTENDEE_EMAIL to "attendee1@example.com"
        )
    }

    @Test
    fun `Attendee has CN parameter`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(Cn("Sample Attendee"))
                }
            },
            main = Event(),
            to = result
        )
        assertAttendee(result, Attendees.ATTENDEE_NAME to "Sample Attendee")
    }

    @Test
    fun `Attendee has user-type INDIVIDUAL`() {
        for (cuType in arrayOf(CuType.INDIVIDUAL, null)) {
            // REQ-PARTICIPANT (default, includes unknown values)
            for (role in arrayOf(Role.REQ_PARTICIPANT, Role("x-custom-role"), null)) {
                val reqParticipant = Entity(ContentValues())
                builder.build(
                    from = Event().apply {
                        attendees += Attendee("mailto:attendee@example.com").apply {
                            if (cuType != null)
                                parameters.add(cuType)
                            if (role != null)
                                parameters.add(role)
                        }
                    },
                    main = Event(),
                    to = reqParticipant
                )
                assertAttendee(reqParticipant,
                    Attendees.ATTENDEE_TYPE to Attendees.TYPE_REQUIRED,
                    Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_ATTENDEE
                )
            }

            // OPT-PARTICIPANT
            val optParticipant = Entity(ContentValues())
            builder.build(
                from = Event().apply {
                    attendees += Attendee("mailto:attendee@example.com").apply {
                        if (cuType != null)
                            parameters.add(cuType)
                        parameters.add(Role.OPT_PARTICIPANT)
                    }
                },
                main = Event(),
                to = optParticipant
            )
            assertAttendee(optParticipant,
                Attendees.ATTENDEE_TYPE to Attendees.TYPE_OPTIONAL,
                Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_ATTENDEE
            )

            // NON-PARTICIPANT
            val nonParticipant = Entity(ContentValues())
            builder.build(
                from = Event().apply {
                    attendees += Attendee("mailto:attendee@example.com").apply {
                        if (cuType != null)
                            parameters.add(cuType)
                        parameters.add(Role.NON_PARTICIPANT)
                    }
                },
                main = Event(),
                to = nonParticipant
            )
            assertAttendee(nonParticipant,
                Attendees.ATTENDEE_TYPE to Attendees.TYPE_NONE,
                Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_ATTENDEE
            )
        }
    }

    @Test
    fun `Attendee has user-type UNKNOWN`() {
        // REQ-PARTICIPANT (default, includes unknown values)
        for (role in arrayOf(Role.REQ_PARTICIPANT, Role("x-custom-role"), null)) {
            val reqParticipant = Entity(ContentValues())
            builder.build(
                from = Event().apply {
                    attendees += Attendee("mailto:attendee@example.com").apply {
                        parameters.add(CuType.UNKNOWN)
                        if (role != null)
                            parameters.add(role)
                    }
                },
                main = Event(),
                to = reqParticipant
            )
            assertAttendee(
                reqParticipant,
                Attendees.ATTENDEE_TYPE to Attendees.TYPE_REQUIRED,
                Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_NONE
            )
        }

        // OPT-PARTICIPANT
        val optParticipant = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(CuType.UNKNOWN)
                    parameters.add(Role.OPT_PARTICIPANT)
                }
            },
            main = Event(),
            to = optParticipant
        )
        assertAttendee(
            optParticipant,
            Attendees.ATTENDEE_TYPE to Attendees.TYPE_OPTIONAL,
            Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_NONE
        )

        // NON-PARTICIPANT
        val nonParticipant = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(CuType.UNKNOWN)
                    parameters.add(Role.NON_PARTICIPANT)
                }
            },
            main = Event(),
            to = nonParticipant
        )
        assertAttendee(
            nonParticipant,
            Attendees.ATTENDEE_TYPE to Attendees.TYPE_NONE,
            Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_NONE
        )
    }

    @Test
    fun `Attendee has user-type GROUP`() {
        // REQ-PARTICIPANT (default, includes unknown values)
        for (role in arrayOf(Role.REQ_PARTICIPANT, Role("x-custom-role"), null)) {
            val reqParticipant = Entity(ContentValues())
            builder.build(
                from = Event().apply {
                    attendees += Attendee("mailto:attendee@example.com").apply {
                        parameters.add(CuType.GROUP)
                        if (role != null)
                            parameters.add(role)
                    }
                },
                main = Event(),
                to = reqParticipant
            )
            assertAttendee(
                reqParticipant,
                Attendees.ATTENDEE_TYPE to Attendees.TYPE_REQUIRED,
                Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_PERFORMER
            )
        }

        // OPT-PARTICIPANT
        val optParticipant = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(CuType.GROUP)
                    parameters.add(Role.OPT_PARTICIPANT)
                }
            },
            main = Event(),
            to = optParticipant
        )
        assertAttendee(
            optParticipant,
            Attendees.ATTENDEE_TYPE to Attendees.TYPE_OPTIONAL,
            Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_PERFORMER
        )

        // NON-PARTICIPANT
        val nonParticipant = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(CuType.GROUP)
                    parameters.add(Role.NON_PARTICIPANT)
                }
            },
            main = Event(),
            to = nonParticipant
        )
        assertAttendee(
            nonParticipant,
            Attendees.ATTENDEE_TYPE to Attendees.TYPE_NONE,
            Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_PERFORMER
        )
    }

    @Test
    fun `Attendee has user-type RESOURCE`() {
        for (role in arrayOf(null, Role.REQ_PARTICIPANT, Role.OPT_PARTICIPANT, Role.NON_PARTICIPANT, Role("X-CUSTOM-ROLE"))) {
            val result = Entity(ContentValues())
            builder.build(
                from = Event().apply {
                    attendees += Attendee("mailto:attendee@example.com").apply {
                        parameters.add(CuType.RESOURCE)
                        if (role != null)
                            parameters.add(role)
                    }
                },
                main = Event(),
                to = result
            )
            assertAttendee(
                result,
                Attendees.ATTENDEE_TYPE to Attendees.TYPE_RESOURCE,
                Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_NONE
            )
        }

        // CHAIR
        val result = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(CuType.RESOURCE)
                    parameters.add(Role.CHAIR)
                }
            },
            main = Event(),
            to = result
        )
        assertAttendee(
            result,
            Attendees.ATTENDEE_TYPE to Attendees.TYPE_RESOURCE,
            Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_SPEAKER
        )
    }

    @Test
    fun `Attendee has user-type ROOM`() {
        for (role in arrayOf(null, Role.CHAIR, Role.REQ_PARTICIPANT, Role.OPT_PARTICIPANT, Role.NON_PARTICIPANT, Role("X-CUSTOM-ROLE"))) {
            val result = Entity(ContentValues())
            builder.build(
                from = Event().apply {
                    attendees += Attendee("mailto:attendee@example.com").apply {
                        parameters.add(CuType.ROOM)
                        if (role != null)
                            parameters.add(role)
                    }
                },
                main = Event(),
                to = result
            )
            assertAttendee(
                result,
                Attendees.ATTENDEE_TYPE to Attendees.TYPE_RESOURCE,
                Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_PERFORMER
            )
        }
    }

    @Test
    fun `Attendee has role CHAIR`() {
        for (cuType in arrayOf(null, CuType.INDIVIDUAL, CuType.UNKNOWN, CuType.GROUP, CuType("x-custom-cutype"))) {
            val result = Entity(ContentValues())
            builder.build(
                from = Event().apply {
                    attendees += Attendee("mailto:attendee@example.com").apply {
                        if (cuType != null)
                            parameters.add(cuType)
                        parameters.add(Role.CHAIR)
                    }
                },
                main = Event(),
                to = result
            )
            assertAttendee(
                result,
                Attendees.ATTENDEE_TYPE to Attendees.TYPE_REQUIRED,
                Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_SPEAKER
            )
        }
    }

    @Test
    fun `Attendee is ORGANIZER`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee(URI("mailto", accountName, null))
            },
            main = Event(),
            to = result
        )
        assertAttendee(
            result,
            Attendees.ATTENDEE_EMAIL to accountName,
            Attendees.ATTENDEE_TYPE to Attendees.TYPE_REQUIRED,
            Attendees.ATTENDEE_RELATIONSHIP to Attendees.RELATIONSHIP_ORGANIZER
        )
    }

    @Test
    fun `Attendee has no participation status`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("mailto:attendee@example.com")
            },
            main = Event(),
            to = result
        )
        assertAttendee(result, Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_INVITED)
    }

    @Test
    fun `Attendee has participation status NEEDS-ACTION`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(PartStat.NEEDS_ACTION)
                }
            },
            main = Event(),
            to = result
        )
        assertAttendee(result, Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_INVITED)
    }

    @Test
    fun `Attendee has participation status ACCEPTED`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(PartStat.ACCEPTED)
                }
            },
            main = Event(),
            to = result
        )
        assertAttendee(result, Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_ACCEPTED)
    }

    @Test
    fun `Attendee has participation status DECLINED`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(PartStat.DECLINED)
                }
            },
            main = Event(),
            to = result
        )
        assertAttendee(result, Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_DECLINED)
    }

    @Test
    fun `Attendee has participation status TENTATIVE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(PartStat.TENTATIVE)
                }
            },
            main = Event(),
            to = result
        )
        assertAttendee(result, Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_TENTATIVE)
    }

    @Test
    fun `Attendee has participation status DELEGATED`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(PartStat.DELEGATED)
                }
            },
            main = Event(),
            to = result
        )
        assertAttendee(result, Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_NONE)
    }

    @Test
    fun `Attendee has custom participation status`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Event().apply {
                attendees += Attendee("mailto:attendee@example.com").apply {
                    parameters.add(PartStat("X-WILL-ASK"))
                }
            },
            main = Event(),
            to = result
        )
        assertAttendee(result, Attendees.ATTENDEE_STATUS to Attendees.ATTENDEE_STATUS_INVITED)
    }


    // helpers

    private fun assertAttendee(result: Entity, vararg values: Pair<String, Any?>) {
        assertEquals(1, result.subValues.size)
        assertContentValuesEqual(
            contentValuesOf(*values),
            result.subValues.first { it.uri == Attendees.CONTENT_URI }.values,
            onlyFieldsInExpected = true
        )
    }

}