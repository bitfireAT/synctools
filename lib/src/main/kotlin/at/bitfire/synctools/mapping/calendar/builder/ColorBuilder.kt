/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import net.fortuna.ical4j.model.component.VEvent

class ColorBuilder: AndroidEventFieldBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity): Boolean {
        // TODO

        /*
        val color = from.color
        if (color != null) {
            // set event color (if it's available for this account)
            calendar.client.query(Colors.CONTENT_URI.asSyncAdapter(calendar.account), arrayOf(Colors.COLOR_KEY),
                "${Colors.COLOR_KEY}=? AND ${Colors.COLOR_TYPE}=${Colors.TYPE_EVENT}", arrayOf(color.name), null)?.use { cursor ->
                if (cursor.moveToNext())
                    row.put(Events.EVENT_COLOR_KEY, color.name)
                else
                    logger.fine("Ignoring event color \"${color.name}\" (not available for this account)")
            }
        } else {
            // reset color index and value
            row.putNull(Events.EVENT_COLOR_KEY)
            row.putNull(Events.EVENT_COLOR)
        }

        return row
        */

        return true
    }

}