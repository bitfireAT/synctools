/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import java.util.logging.Level
import java.util.logging.Logger

class LoggingTestRunner: AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)

        val rootLogger = Logger.getLogger("")
        rootLogger.level = Level.ALL
    }

}