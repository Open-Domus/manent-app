// SPDX-License-Identifier: MIT

package it.manentlabs.app.voice.phone

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The permissions phone actions rely on. "Display over other apps" is what
 * lets Android start an app from the background, where commands arrive;
 * nothing is ever drawn over other apps. Contacts and calls are only for
 * calling people by name.
 *
 * The overlay permission changes in the system settings, with no broadcast,
 * so [refresh] is called when the app comes back to the screen.
 */
@Singleton
class PhonePermissions @Inject constructor(@ApplicationContext private val context: Context) {
    private val _overlay = MutableStateFlow(Settings.canDrawOverlays(context))
    val overlay: StateFlow<Boolean> = _overlay.asStateFlow()

    fun refresh(): Boolean = Settings.canDrawOverlays(context).also { _overlay.value = it }

    fun canReadContacts(): Boolean = granted(Manifest.permission.READ_CONTACTS)

    fun canCall(): Boolean = granted(Manifest.permission.CALL_PHONE)

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** Asked for together, from the Calls row. */
        val CALLING = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)

        /** Opens the "display over other apps" page for this app, or the general one where that page is missing. */
        fun openOverlaySettings(context: Context) {
            val own = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
            try {
                context.startActivity(own)
            } catch (_: ActivityNotFoundException) {
                try {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                } catch (_: ActivityNotFoundException) {
                }
            }
        }
    }
}
