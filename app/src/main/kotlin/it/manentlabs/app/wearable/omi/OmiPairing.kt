// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.Parcelable
import android.util.Log
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Associates the pendant through the system's companion-device chooser, which
 * finds it without location or scan permissions, and lets the system wake the
 * app when the pendant comes in range (Android 12+).
 */
@Singleton
class OmiPairing @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: OmiPairingStore,
    private val link: OmiLink,
) {
    /** How starting the chooser went: [Chooser] to launch, or a failure with the system's message, if any. */
    sealed interface Start {
        data class Chooser(val sender: IntentSender) : Start
        data object Unsupported : Start
        data class Failed(val message: String?) : Start
    }

    private val manager: CompanionDeviceManager?
        get() = if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            context.getSystemService(CompanionDeviceManager::class.java)
        } else {
            null
        }

    val isAssociated: Boolean get() = store.address != null

    /** Asks the system to look for pendants; [onResult] runs once, on the main thread. */
    @Suppress("DEPRECATION")
    fun associate(onResult: (Start) -> Unit) {
        val cdm = manager ?: return onResult(Start.Unsupported)
        val filter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(ScanFilter.Builder().setServiceUuid(ParcelUuid(OmiGatt.AUDIO_SERVICE)).build())
            .build()
        val request = AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(false).build()
        val callback = object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(sender: IntentSender) = onResult(Start.Chooser(sender))
            override fun onDeviceFound(sender: IntentSender) = onResult(Start.Chooser(sender))
            override fun onFailure(error: CharSequence?) = onResult(Start.Failed(error?.toString()))
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                cdm.associate(request, context.mainExecutor, callback)
            } else {
                cdm.associate(request, callback, Handler(Looper.getMainLooper()))
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Association could not start", e)
            onResult(Start.Failed(e.message))
        }
    }

    /** Takes the chooser's answer. A cancelled chooser, or one with no device, changes nothing. */
    fun onChooserResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) return
        val (address, id) = chosen(data) ?: return
        store.save(address, id)
        observePresence(address)
        link.markAssociated()
        OmiLinkService.start(context)
    }

    /** Ends the association and forgets the pendant entirely. */
    @Suppress("DEPRECATION")
    fun forget() {
        OmiLinkService.stop(context)
        val address = store.address
        val id = store.associationId
        val cdm = manager
        if (cdm != null && address != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { cdm.stopObservingDevicePresence(address) }
                    .onFailure { Log.w(TAG, "Could not stop observing presence", it) }
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && id != null) cdm.disassociate(id) else cdm.disassociate(address)
            }.onFailure { Log.w(TAG, "Could not disassociate", it) }
        }
        store.clear()
        link.disconnect()
    }

    /**
     * Presence observation does not survive every app update, so it is armed
     * again whenever the pendant's screen opens, and the link service started.
     */
    fun rearm() {
        val address = store.address ?: return
        if (!hasBluetoothConnect(context)) return
        observePresence(address)
        OmiLinkService.start(context)
    }

    @Suppress("DEPRECATION")
    private fun observePresence(address: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val cdm = manager ?: return
        runCatching { cdm.startObservingDevicePresence(address) }
            .onFailure { Log.w(TAG, "Could not observe presence", it) }
    }

    @Suppress("DEPRECATION")
    private fun chosen(data: Intent): Pair<String, Int?>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val info = IntentCompat.getParcelableExtra(data, CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
            val address = info?.deviceMacAddress?.toString()
            if (info != null && address != null) return address.uppercase() to info.id
        }
        val address = when (val device = IntentCompat.getParcelableExtra(data, CompanionDeviceManager.EXTRA_DEVICE, Parcelable::class.java)) {
            is ScanResult -> device.device.address
            is BluetoothDevice -> device.address
            else -> null
        }
        return address?.let { it.uppercase() to null }
    }
}
