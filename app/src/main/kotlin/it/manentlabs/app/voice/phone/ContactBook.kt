// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

import android.content.Context
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Finds a contact's number by spoken name. Read on the phone only; nothing is kept or sent. */
@Singleton
class ContactBook @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: PhonePermissions,
) {
    sealed interface Lookup {
        data class Found(val number: String) : Lookup
        data object NotAllowed : Lookup
        data object NoOne : Lookup
        data object Several : Lookup
    }

    fun find(name: String): Lookup {
        if (!permissions.canReadContacts()) return Lookup.NotAllowed
        val contacts = withNumbers()
        return when (val match = NameMatching.contact(name, contacts.map { it.second })) {
            NameMatching.ContactMatch.None -> Lookup.NoOne
            NameMatching.ContactMatch.Several -> Lookup.Several
            is NameMatching.ContactMatch.One -> number(contacts[match.index].first)?.let(Lookup::Found) ?: Lookup.NoOne
        }
    }

    /** Every contact with a phone number: id and display name. */
    private fun withNumbers(): List<Pair<Long, String>> {
        val columns = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        val where = "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1"
        return context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, columns, where, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    add(cursor.getLong(0) to name)
                }
            }
        }.orEmpty()
    }

    /** The contact's main number, else its first. */
    private fun number(contactId: Long): String? {
        val columns = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY)
        val where = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        return context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, columns, where, arrayOf(contactId.toString()), null,
        )?.use { cursor ->
            var first: String? = null
            while (cursor.moveToNext()) {
                val number = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                if (cursor.getInt(1) != 0) return@use number
                if (first == null) first = number
            }
            first
        }
    }
}
