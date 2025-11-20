/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android.impl

import android.content.ContentValues
import at.bitfire.ical4android.DmfsTask
import at.bitfire.ical4android.DmfsTaskFactory
import at.bitfire.ical4android.DmfsTaskList
import at.bitfire.ical4android.Task

class TestTask: DmfsTask {

    constructor(taskList: DmfsTaskList<DmfsTask>, values: ContentValues)
        : super(taskList, values)

    constructor(taskList: TestTaskList, task: Task)
        : super(taskList, task, "6c2710c3-f82c-4dfa-8738-186b82c35c08", null, null, 0)

    object Factory: DmfsTaskFactory<TestTask> {
        override fun fromProvider(taskList: DmfsTaskList<DmfsTask>, values: ContentValues) =
                TestTask(taskList, values)
    }

}
