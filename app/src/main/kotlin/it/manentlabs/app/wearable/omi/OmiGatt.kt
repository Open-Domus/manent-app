// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import java.util.UUID

/**
 * The pendant's GATT layout, as the open-source Omi firmware defines it.
 * These identifiers are fixed by the firmware; nothing here is ours to change.
 */
object OmiGatt {
    val AUDIO_SERVICE: UUID = UUID.fromString("19b10000-e8f2-537e-4f6c-d104768a1214")
    val AUDIO_DATA: UUID = UUID.fromString("19b10001-e8f2-537e-4f6c-d104768a1214")
    val AUDIO_CODEC: UUID = UUID.fromString("19b10002-e8f2-537e-4f6c-d104768a1214")

    val SETTINGS_SERVICE: UUID = UUID.fromString("19b10010-e8f2-537e-4f6c-d104768a1214")

    /** Read only: the firmware saves a write to flash on its Bluetooth thread and the link drops. */
    val LED_DIM: UUID = UUID.fromString("19b10011-e8f2-537e-4f6c-d104768a1214")
    val MIC_GAIN: UUID = UUID.fromString("19b10012-e8f2-537e-4f6c-d104768a1214")

    /** Read only: subscribing to it dropped the link on firmware 3.0.21. */
    val CHARGING: UUID = UUID.fromString("19b10013-e8f2-537e-4f6c-d104768a1214")

    /** Present on newer firmware only. */
    val CAPTURE_MUTE: UUID = UUID.fromString("19b10015-e8f2-537e-4f6c-d104768a1214")

    val FEATURES_SERVICE: UUID = UUID.fromString("19b10020-e8f2-537e-4f6c-d104768a1214")
    val FEATURES: UUID = UUID.fromString("19b10021-e8f2-537e-4f6c-d104768a1214")

    val TIME_SERVICE: UUID = UUID.fromString("19b10030-e8f2-537e-4f6c-d104768a1214")
    val TIME_SYNC: UUID = UUID.fromString("19b10031-e8f2-537e-4f6c-d104768a1214")

    val BUTTON_SERVICE: UUID = UUID.fromString("23ba7924-0000-1000-7450-346eac492e92")
    val BUTTON: UUID = UUID.fromString("23ba7925-0000-1000-7450-346eac492e92")

    val HAPTIC_SERVICE: UUID = UUID.fromString("cab1ab95-2ea5-4f4d-bb56-874b72cfc984")
    val HAPTIC: UUID = UUID.fromString("cab1ab96-2ea5-4f4d-bb56-874b72cfc984")

    val STORAGE_SERVICE: UUID = UUID.fromString("30295780-4301-eabd-2904-2849adfeae43")
    val STORAGE_STATUS: UUID = UUID.fromString("30295782-4301-eabd-2904-2849adfeae43")

    val BATTERY_SERVICE: UUID = standard(0x180f)
    val BATTERY_LEVEL: UUID = standard(0x2a19)

    val DEVICE_INFO_SERVICE: UUID = standard(0x180a)
    val MODEL: UUID = standard(0x2a24)
    val SERIAL: UUID = standard(0x2a25)
    val FIRMWARE_REVISION: UUID = standard(0x2a26)
    val HARDWARE_REVISION: UUID = standard(0x2a27)
    val MANUFACTURER: UUID = standard(0x2a29)

    /** Client characteristic configuration: turns notifications on and off. */
    val CCC: UUID = standard(0x2902)

    /** Opus at 16 kHz, mono, 20 ms frames: the only audio format the app reads. */
    const val CODEC_OPUS_16K = 21

    const val REQUESTED_MTU = 512

    private fun standard(short: Int): UUID =
        UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(short))
}
