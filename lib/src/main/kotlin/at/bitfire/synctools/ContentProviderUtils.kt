/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils

/**
 * Removes blank (empty or only white-space) [String] values from [ContentValues].
 *
 * @return the modified object (which is the same object as passed in; for chaining)
 */
fun ContentValues.removeBlank(): ContentValues {
    val iter = keySet().iterator()
    while (iter.hasNext()) {
        val obj = this[iter.next()]
        if (obj is CharSequence && obj.isBlank())
            iter.remove()
    }
    return this
}

/**
 * Returns the entire contents of the current row as a [ContentValues] object.
 *
 * @param  removeBlankRows  whether rows with blank values should be removed
 *
 * @return entire contents of the current row
 */
fun Cursor.toContentValues(removeBlankRows: Boolean = true): ContentValues {
    val values = ContentValues(columnCount)
    DatabaseUtils.cursorRowToContentValues(this, values)

    if (removeBlankRows)
        values.removeBlank()

    return values
}