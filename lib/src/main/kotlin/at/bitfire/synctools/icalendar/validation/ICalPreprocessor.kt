/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar.validation

import androidx.annotation.VisibleForTesting
import com.google.common.io.CharSource
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.transform.rfc5545.CreatedPropertyRule
import net.fortuna.ical4j.transform.rfc5545.DateListPropertyRule
import net.fortuna.ical4j.transform.rfc5545.DatePropertyRule
import net.fortuna.ical4j.transform.rfc5545.Rfc5545PropertyRule
import java.io.BufferedReader
import java.io.Reader
import java.util.logging.Level
import java.util.logging.Logger
import javax.annotation.WillNotClose

/**
 * Applies some rules to increase compatibility of parsed (incoming) iCalendars:
 *
 *   - [CreatedPropertyRule] to make sure CREATED is UTC
 *   - [DatePropertyRule], [DateListPropertyRule] to rename Outlook-specific TZID parameters
 * (like "W. Europe Standard Time" to an Android-friendly name like "Europe/Vienna")
 *
 */
class ICalPreprocessor {

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
            newString = preprocessor.repairLine(newString)
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
     * Closing the returned [Reader] will also close the [original] reader if needed.
     *
     * @param original original iCalendar object. Will be closed after processing.
     * @return A reader that emits the potentially repaired iCalendar object.
     */
    fun preprocessStream(@WillNotClose original: Reader): Reader {
        val chunkedFixedLines = BufferedReader(original)
            .lineSequence()
            .map { line ->      // BufferedReader provides line without line break
                val fixed = applyPreprocessors(line)
                CharSource.wrap(fixed + "\r\n")     // iCalendar uses CR+LF
            }
            .asIterable()
        // we don't close 'original' here because CharSource.concat() will read from it lazily
        return CharSource.concat(chunkedFixedLines).openStream()
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