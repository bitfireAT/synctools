/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar.validation

import com.google.common.io.CharStreams
import org.junit.Test
import java.io.Reader
import java.io.Writer
import java.util.UUID

class ICalPreprocessorInstrumentedTest {

    private class VCalendarReaderGenerator(val eventCount: Int) : Reader() {
        private var stage = 0 // 0 = header, 1 = events, 2 = footer, 3 = done
        private var eventIdx = 0
        private var current: String? = null
        private var pos = 0

        override fun reset() {
            stage = 0
            eventIdx = 0
            current = null
            pos = 0
        }

        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            var charsRead = 0
            while (charsRead < len) {
                if (current == null || pos >= current!!.length) {
                    current = when (stage) {
                        0 -> {
                            stage = 1
                            """
                        BEGIN:VCALENDAR
                        PRODID:-//xyz Corp//NONSGML PDA Calendar Version 1.0//EN
                        VERSION:2.0
                        """.trimIndent() + "\n"
                        }
                        1 -> {
                            if (eventIdx < eventCount) {
                                val event = """
                                BEGIN:VEVENT
                                DTSTAMP:19960704T120000Z
                                UID:${UUID.randomUUID()}
                                ORGANIZER:mailto:jsmith@example.com
                                DTSTART:19960918T143000Z
                                DTEND:19960920T220000Z
                                STATUS:CONFIRMED
                                CATEGORIES:CONFERENCE
                                SUMMARY:Event $eventIdx
                                DESCRIPTION:Event $eventIdx description
                                END:VEVENT
                            """.trimIndent() + "\n"
                                eventIdx++
                                event
                            } else {
                                stage = 2
                                null
                            }
                        }
                        2 -> {
                            stage = 3
                            "END:VCALENDAR\n"
                        }
                        else -> return if (charsRead == 0) -1 else charsRead
                    }
                    pos = 0
                    if (current == null) continue // move to next stage
                }
                val charsLeft = current!!.length - pos
                val toRead = minOf(len - charsRead, charsLeft)
                current!!.toCharArray(pos, pos + toRead).copyInto(cbuf, off + charsRead)
                pos += toRead
                charsRead += toRead
            }
            return charsRead
        }

        override fun close() {
            // No resources to release
            current = null
        }
    }

    @Test
    fun testParse_SuperLargeFiles() {
        val preprocessor = ICalPreprocessor()
        val reader = VCalendarReaderGenerator(eventCount = 100_000)
        preprocessor.preprocessStream(reader).use { preprocessed ->
            // consume preprocessed stream
            val start = System.currentTimeMillis()
            CharStreams.copy(preprocessed, Writer.nullWriter())
            val end = System.currentTimeMillis()

            // no exception called
            System.err.println("testParse_SuperLargeFiles took ${(end - start) / 1000} seconds")
        }
   }
}