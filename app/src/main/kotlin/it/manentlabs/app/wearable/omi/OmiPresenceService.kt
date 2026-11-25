// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Bound by the system when the associated pendant comes in range, even with
 * the app's process gone. Starting the link service from here is what makes
 * the pendant's button work after a reboot or a process kill.
 */
@RequiresApi(Build.VERSION_CODES.S)
class OmiPresenceService : CompanionDeviceService() {
    @Deprecated("Superseded by the AssociationInfo variant on Android 13")
    override fun onDeviceAppeared(address: String) = OmiLinkService.start(this)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceAppeared(associationInfo: AssociationInfo) = OmiLinkService.start(this)
}
