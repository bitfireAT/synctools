/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.exception

import android.net.Uri

/**
 * Represents an invalid local resource (for instance, an Android event).
 *
 * @param message       detail message
 * @param ex            cause
 * @param contentUri    android content provider URI of the local resource (like `content://provider/entry`)
 */
class InvalidLocalResourceException(
    message: String,
    ex: Throwable? = null,
    val contentUri: Uri? = null
) : InvalidResourceException(message, ex)