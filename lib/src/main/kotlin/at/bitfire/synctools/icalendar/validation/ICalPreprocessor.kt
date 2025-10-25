/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar.validation

import androidx.annotation.VisibleForTesting
import at.bitfire.synctools.utils.SequenceReader
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.transform.rfc5545.CreatedPropertyRule
import net.fortuna.ical4j.transform.rfc5545.DateListPropertyRule
import net.fortuna.ical4j.transform.rfc5545.DatePropertyRule
import net.fortuna.ical4j.transform.rfc5545.Rfc5545PropertyRule
import java.io.BufferedReader
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.collections.joinToString
import kotlin.sequences.chunked
import kotlin.sequences.map

/**
 * Applies some rules to increase compatibility of parsed (incoming) iCalendars:
 *
 *   - [CreatedPropertyRule] to make sure CREATED is UTC
 *   - [DatePropertyRule], [DateListPropertyRule] to rename Outlook-specific TZID parameters
 * (like "W. Europe Standard Time" to an Android-friendly name like "Europe/Vienna")
 *
 */
class ICalPreprocessor {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private val propertyRules = arrayOf(
        CreatedPropertyRule(),      // make sure CREATED is UTC

        DatePropertyRule(),         // These two rules also replace VTIMEZONEs of the iCalendar ...
        DateListPropertyRule()      // ... by the ical4j VTIMEZONE with the same TZID!
    )

    @VisibleForTesting
    internal val streamPreprocessors = arrayOf(
        FixInvalidUtcOffsetPreprocessor(),  // fix things like TZOFFSET(FROM,TO):+5730
        FixInvalidDayOffsetPreprocessor()   // fix things like DURATION:PT2D
    )

    /**
     * Applies [streamPreprocessors] to some given [lines] by calling `fixString()` repeatedly on each of them.
     * @param lines original iCalendar object as string. This may not contain the full iCalendar,
     * but only a part of it.
     * @return The repaired iCalendar object as string.
     */
    @VisibleForTesting
    fun applyPreprocessors(lines: String): String {
        var newString = lines
        for (preprocessor in streamPreprocessors)
            newString = preprocessor.fixString(newString)
        return newString
    }

    /**
     * Applies [streamPreprocessors] to a given [Reader] that reads an iCalendar object
     * in order to repair some things that must be fixed before parsing.
     *
     * The original reader content is processed in chunks of [chunkSize] lines to avoid loading
     * the whole content into memory at once. If the given [Reader] does not support `reset()`,
     * the whole content will be loaded into memory anyway.
     *
     * @param original original iCalendar object
     * @return The potentially repaired iCalendar object.
     * If [original] supports `reset()`,  the returned [Reader] will be a [SequenceReader].
     * Otherwise, it will be a [StringReader].
     */
    fun preprocessStream(original: Reader, chunkSize: Int = 1_000): Reader {
        val resetSupported = try {
            original.reset()
            true
        } catch(_: IOException) {
            // reset is not supported. String will be loaded into memory completely
            logger.warning("Reader does not support reset()")
            false
        }

        if (resetSupported) {
            val chunkedFixedLines = BufferedReader(original)
                .lineSequence()
                .chunked(chunkSize)
                .map { chunk -> applyPreprocessors(chunk.joinToString("\n")) }
            return SequenceReader(chunkedFixedLines)
        } else {
            // The reader doesn't support reset, so we need to load the whole content into memory
            return StringReader(applyPreprocessors(original.readText()))
        }
    }


    /**
     * Applies the set of rules (see class definition) to a given calendar object.
     *
     * @param calendar the calendar object that is going to be modified
     */
    fun preprocessCalendar(calendar: Calendar) {
        for (component in calendar.components)
            for (property in component.properties)
                applyRules(property)
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyRules(property: Property) {
        propertyRules
            .filter { rule -> rule.supportedType.isAssignableFrom(property::class.java) }
            .forEach {
                val beforeStr = property.toString()
                (it as Rfc5545PropertyRule<Property>).applyTo(property)
                val afterStr = property.toString()
                if (beforeStr != afterStr)
                    Logger.getLogger(javaClass.name).log(Level.FINER, "$beforeStr -> $afterStr")
            }
    }

}