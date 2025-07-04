/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.exception

import java.net.URI

/**
 * Represents an invalid remote resource (for instance, a calendar object resource).
 *
 * @param message   detail message
 * @param ex        cause
 * @param url       URL of the invalid resource
 */
class InvalidRemoteResourceException(
    message: String,
    ex: Throwable? = null,
    val url: URI? = null
): InvalidResourceException(message, ex)