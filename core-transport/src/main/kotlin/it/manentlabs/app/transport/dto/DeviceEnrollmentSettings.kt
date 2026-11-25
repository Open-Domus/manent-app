// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package it.manentlabs.app.transport.dto

import kotlinx.serialization.Serializable

/** The gateway's local choice about new devices (`CloudRelayManagementStatus.deviceEnrollment`). */
@Serializable
data class DeviceEnrollmentSettings(
    /** `automatic` or `ask`. */
    val policy: String = "ask",
)

@Serializable
internal data class DeviceEnrollmentPolicyBody(val policy: String)
