/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.storage.jtx

import android.content.ContentValues
import at.bitfire.synctools.storage.LocalStorageException
import at.techbee.jtx.JtxContract

/**
 * Represents a locally stored jtx collection (journals, notes, tasks). Communicates with
 * the jtx Board content provider via [provider].
 *
 * @param provider  jtx collection provider
 * @param values    content values as read from the jtx Board provider; [JtxContract.JtxCollection.ID] must be set
 *
 * @throws IllegalArgumentException when [JtxContract.JtxCollection.ID] is not set
 */
class JtxCollection(
    val provider: JtxCollectionProvider,
    val values: ContentValues
) {

    /** see [JtxContract.JtxCollection.ID] */
    val id: Long = values.getAsLong(JtxContract.JtxCollection.ID)
        ?: throw IllegalArgumentException("${JtxContract.JtxCollection.ID} must be available")


    // data fields

    /** see [JtxContract.JtxCollection.URL] */
    val url: String?
        get() = values.getAsString(JtxContract.JtxCollection.URL)

    /** see [JtxContract.JtxCollection.DISPLAYNAME] */
    val displayName: String?
        get() = values.getAsString(JtxContract.JtxCollection.DISPLAYNAME)

    /** see [JtxContract.JtxCollection.DESCRIPTION] */
    val description: String?
        get() = values.getAsString(JtxContract.JtxCollection.DESCRIPTION)

    /** see [JtxContract.JtxCollection.COLOR] */
    val color: Int?
        get() = values.getAsInteger(JtxContract.JtxCollection.COLOR)

    /** see [JtxContract.JtxCollection.SUPPORTSVEVENT] */
    val supportsVEvent: Boolean
        get() = values.getAsBoolean(JtxContract.JtxCollection.SUPPORTSVEVENT) ?: false

    /** see [JtxContract.JtxCollection.SUPPORTSVTODO] */
    val supportsVTodo: Boolean
        get() = values.getAsBoolean(JtxContract.JtxCollection.SUPPORTSVTODO) ?: false

    /** see [JtxContract.JtxCollection.SUPPORTSVJOURNAL] */
    val supportsVJournal: Boolean
        get() = values.getAsBoolean(JtxContract.JtxCollection.SUPPORTSVJOURNAL) ?: false

    /** see [JtxContract.JtxCollection.SYNC_ID] */
    val syncId: Long?
        get() = values.getAsLong(JtxContract.JtxCollection.SYNC_ID)

    /** see [JtxContract.JtxCollection.READONLY] */
    val readonly: Boolean
        get() = values.getAsBoolean(JtxContract.JtxCollection.READONLY) ?: false


    // CRUD Events
    // TODO: Add JtxICalObject CRUD operations
    // i.e. fun addJtxObject(entity: Entity): Long { ... }


    // shortcuts to upper level

    /**
     * Deletes this collection from the jtx Board content provider.
     *
     * @return number of deleted rows
     * @throws LocalStorageException when the content provider returns an error
     */
    fun delete(): Int =
        provider.deleteCollection(id)

    /**
     * Updates this collection in the jtx Board content provider.
     *
     * @param values    values to update
     * @return number of updated rows
     * @throws LocalStorageException when the content provider returns an error
     */
    fun update(values: ContentValues): Int =
        provider.updateCollection(id, values)

}
