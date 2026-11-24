// SPDX-License-Identifier: MIT

package it.manentlabs.app.omnesis

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.omnesis.android.feature.recording.RecordingAvailabilityMemory
import dev.omnesis.android.feature.recording.ui.RecordingAvailability
import dev.omnesis.android.pairing.EncryptedPreferencesStore
import dev.omnesis.android.pairing.PairedGatewayTransport
import dev.omnesis.android.pairing.Pairing
import dev.omnesis.android.pairing.PairingService
import dev.omnesis.android.transport.DeviceCapabilities
import dev.omnesis.android.transport.client.GatewayClient
import dev.omnesis.android.transport.client.NotesClient
import dev.omnesis.android.transport.client.RecordingsClient
import dev.omnesis.android.transport.client.SearchClient
import dev.omnesis.android.transport.dto.StatusSnapshot
import dev.omnesis.android.transport.http.GatewayHttp
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The clients of one pairing with the gateway. */
class GatewaySession(
    val pairing: Pairing,
    val http: GatewayHttp,
) {
    val gateway = GatewayClient(http)
    val recordings = RecordingsClient(http)
    val search = SearchClient(http)
    val notes = NotesClient(http)
    val transcriber = GatewayTranscriber(http)
}

/** What the paired gateway said about itself the last time it was asked. */
data class GatewayStatus(
    val reachable: Boolean,
    val experimental: Boolean = false,
    val recordings: Boolean = false,
    val dictationEnabled: Boolean? = null,
    val error: String? = null,
)

/**
 * This app's one pairing with an Omnesis gateway, as its own device. It is a
 * slim stand-in for the Omnesis app's SessionManager: no device socket, no
 * hosted sources, only the HTTP clients that recording, speakers, marked
 * moments and voice-command transcription use.
 */
@Singleton
class GatewaySessions @Inject constructor(
    @ApplicationContext context: Context,
    private val availabilityMemory: RecordingAvailabilityMemory,
) : RecordingAvailability {
    private val pairingService = PairingService(
        store = EncryptedPreferencesStore(context),
        deviceName = "Manent",
        deviceCapabilities = DeviceCapabilities.android(emptyList()),
    )

    @Volatile
    var session: GatewaySession? = null
        private set

    private val _paired = MutableStateFlow<Pairing?>(null)
    val paired: StateFlow<Pairing?> = _paired.asStateFlow()

    private val _status = MutableStateFlow<GatewayStatus?>(null)
    val status: StateFlow<GatewayStatus?> = _status.asStateFlow()

    private val _availability = MutableStateFlow(RecordingAvailability.Availability.UNAVAILABLE)
    override val state: StateFlow<RecordingAvailability.Availability> = _availability.asStateFlow()

    init {
        rebuild()
    }

    /** Pair from a QR payload, pasted or scanned (`{"v":2,"gatewayUrl":…,"pairingCode":…}`). */
    suspend fun pairFromPayload(raw: String): Pairing = pairingService.pair(raw.trim()).also { rebuild() }

    /** Pair from the gateway's address and a code from `omnesis devices pair`. */
    suspend fun pairManually(url: String, code: String, fingerprint: String?): Pairing =
        pairingService.pairManually(url.trim(), code.trim(), fingerprint?.trim()?.ifBlank { null }).also { rebuild() }

    fun unpair() {
        pairingService.unpair()
        rebuild()
    }

    /** Ask the gateway what it offers now; recordings wait until it says it takes them. */
    suspend fun refreshStatus(): GatewayStatus? {
        val current = session ?: return null
        val result = try {
            val status = current.gateway.status()
            _availability.value = availability(status).also {
                availabilityMemory.remember(current.pairing.availabilityKey(), it)
            }
            GatewayStatus(
                reachable = true,
                experimental = status.experimental,
                recordings = status.recordings,
                dictationEnabled = status.dictation?.enabled,
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Status probe failed: ${e::class.simpleName}: ${e.message}")
            GatewayStatus(reachable = false, error = e.message ?: e::class.simpleName)
        }
        if (session === current) _status.value = result
        return result
    }

    private fun rebuild() {
        val pairing = pairingService.current()
        _paired.value = pairing
        _status.value = null
        if (pairing == null) {
            session = null
            availabilityMemory.forget()
            _availability.value = RecordingAvailability.Availability.UNAVAILABLE
            return
        }
        // A recording may start while the gateway is out of reach: start from the last answer.
        _availability.value = availabilityMemory.lastKnown(pairing.availabilityKey())
        val client = PairedGatewayTransport.http(pairing)
        session = GatewaySession(pairing, GatewayHttp(client, pairing.url, pairing.token, pairing.pairingGeneration))
    }

    private companion object {
        const val TAG = "Manent:Session"
    }
}

/** Recordings need experimental mode, the `recordings` capability and transcription left on. */
internal fun availability(status: StatusSnapshot): RecordingAvailability.Availability = when {
    !status.experimental || !status.recordings -> RecordingAvailability.Availability.UNAVAILABLE
    status.dictation?.enabled == false -> RecordingAvailability.Availability.TRANSCRIPTION_OFF
    else -> RecordingAvailability.Availability.AVAILABLE
}

internal fun Pairing.availabilityKey(): String = pairingGeneration ?: "$url\u001f${deviceId.orEmpty()}"
