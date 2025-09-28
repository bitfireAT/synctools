/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.utils

import java.io.Reader

/**
 * A [Reader] that allows loading data from a [Sequence] of [String]s.
 */
class SequenceReader(private val linesProvider: () -> Sequence<String>) : Reader() {
    constructor(lines: Sequence<String>) : this({ lines })

    private var iterator = linesProvider().iterator()
    private var currentLine: String? = null
    private var currentPos = 0
    private var closed = false

    override fun reset() {
        iterator = linesProvider().iterator()
        currentLine = null
        currentPos = 0
        closed = false
    }

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        check(!closed) { "Reader closed" }
        var charsRead = 0
        while (charsRead < len) {
            if (currentLine == null || currentPos >= currentLine!!.length) {
                if (!iterator.hasNext()) {
                    if (charsRead == 0) return -1
                    break
                }
                currentLine = iterator.next() + "\n"
                currentPos = 0
            }
            val charsToCopy = minOf(len - charsRead, currentLine!!.length - currentPos)
            currentLine!!.toCharArray(currentPos, currentPos + charsToCopy).copyInto(cbuf, off + charsRead)
            currentPos += charsToCopy
            charsRead += charsToCopy
        }
        return charsRead
    }

    override fun close() {
        closed = true
    }
}
