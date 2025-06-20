/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.vcard4android.contactrow

import android.content.ContentProviderClient
import android.content.ContentValues
import android.net.Uri
import android.provider.ContactsContract.RawContacts
import at.bitfire.synctools.storage.ContactsBatchOperation
import at.bitfire.vcard4android.AndroidContact
import at.bitfire.vcard4android.Contact
import java.util.logging.Level
import java.util.logging.Logger

class ContactProcessor(
    val provider: ContentProviderClient?
) {

    private val dataRowHandlers = mutableMapOf<String, MutableList<DataRowHandler>>()
    private val defaultDataRowHandlers = arrayOf(
        EmailHandler,
        EventHandler,
        ImHandler,
        NicknameHandler,
        NoteHandler,
        OrganizationHandler,
        PhoneHandler,
        PhotoHandler(provider),
        RelationHandler,
        SipAddressHandler,
        StructuredNameHandler,
        StructuredPostalHandler,
        WebsiteHandler
    )

    private val dataRowBuilderFactories = mutableListOf<DataRowBuilder.Factory<*>>(
        EmailBuilder.Factory,
        EventBuilder.Factory,
        ImBuilder.Factory,
        NicknameBuilder.Factory,
        NoteBuilder.Factory,
        OrganizationBuilder.Factory,
        PhoneBuilder.Factory,
        PhotoBuilder.Factory,
        RelationBuilder.Factory,
        SipAddressBuilder.Factory,
        StructuredNameBuilder.Factory,
        StructuredPostalBuilder.Factory,
        WebsiteBuilder.Factory
    )


    init {
        for (handler in defaultDataRowHandlers)
            registerHandler(handler)
    }


    fun registerHandler(handler: DataRowHandler) {
        val mimeType = handler.forMimeType()
        val handlers = dataRowHandlers[mimeType] ?: run {
            val newList = mutableListOf<DataRowHandler>()
            dataRowHandlers[mimeType] = newList
            newList
        }

        handlers += handler
    }

    fun registerBuilderFactory(factory: DataRowBuilder.Factory<*>) {
        dataRowBuilderFactories += factory
    }


    fun handleRawContact(values: ContentValues, contact: Contact) {
        contact.uid = values.getAsString(AndroidContact.COLUMN_UID)
    }

    fun handleDataRow(values: ContentValues, contact: Contact) {
        val mimeType = values.getAsString(RawContacts.Data.MIMETYPE)

        val handlers = dataRowHandlers[mimeType].orEmpty()
        if (handlers.isNotEmpty())
            for (handler in handlers)
                handler.handle(values, contact)
        else {
            val logger = Logger.getLogger(javaClass.name)
            logger.log(Level.WARNING, "No registered handler for $mimeType", values)
        }
    }


    fun insertDataRows(dataRowUri: Uri, rawContactId: Long?, contact: Contact, batch: ContactsBatchOperation, readOnly: Boolean) {
        for (factory in dataRowBuilderFactories) {
            val builder = factory.newInstance(dataRowUri, rawContactId, contact, readOnly)
            batch += builder.build()
        }
    }


    fun builderMimeTypes(): Set<String> {
        val mimeTypes = mutableSetOf<String>()
        for (factory in dataRowBuilderFactories)
            mimeTypes += factory.mimeType()
        return mimeTypes
    }

}