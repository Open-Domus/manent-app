// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** The only runtime permission the pendant needs: Nearby devices (Android 12+). Older versions grant it at install. */
internal fun hasBluetoothConnect(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

internal const val TAG = "Manent:Omi"
