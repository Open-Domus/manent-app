// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package it.manentlabs.app.pairing

import it.manentlabs.app.transport.DeviceCapabilities
import kotlinx.serialization.Serializable

/** Request body for `POST /devices/pair`. */
@Serializable
data class PairExchangeBody(
    val pairingCode: String,
    val capabilities: DeviceCapabilities? = null,
)

/** Response from `POST /devices/pair`. Mirrors the iOS `DevicePairResponse`. */
@Serializable
data class DevicePairResponse(
    val device: Device,
    val tokenId: String = "",
    val token: String,
    val scopes: List<String> = emptyList(),
) {
    @Serializable
    data class Device(
        val id: String,
        val name: String = "",
        val kind: String = "",
    )
}
