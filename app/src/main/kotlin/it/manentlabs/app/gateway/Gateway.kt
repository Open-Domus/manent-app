// SPDX-License-Identifier: MIT

package it.manentlabs.app.gateway

import it.manentlabs.app.security.SecretBox

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's link to its Omnesis gateway: the one pairing, the API bound to it,
 * and what the gateway last said about itself.
 *
 * Pairing, unpairing and status checks all go through here, so every screen
 * and worker sees the same pairing. Whenever the pairing changes the API is
 * rebuilt and the cached status starts over.
 */
@Singleton
class Gateway @Inject constructor(
    private val store: PairingStore,
    private val memory: AvailabilityMemory,
    private val connector: GatewayConnector,
) {
    private class Session(val pairing: Pairing, val api: GatewayApi)

    private val changes = Mutex()
    private var session: Session? = null
    private var attempt = 0L
    private var inFlight: Job? = null
    private var lastAnswer: GatewayStatus? = null

    private val _paired = MutableStateFlow<PairedGateway?>(null)
    private val _api = MutableStateFlow<GatewayApi?>(null)
    private val _status = MutableStateFlow<StatusCheck>(StatusCheck.Pending)
    private val _availability = MutableStateFlow(RecordingAvailability.AVAILABLE)
    private val _transcriberBlock = MutableStateFlow<TranscriberBlock?>(TranscriberBlock.NOT_PAIRED)

    /** The paired gateway's name and address; null while unpaired. */
    val paired: StateFlow<PairedGateway?> = _paired.asStateFlow()

    /** The endpoints of the current pairing; null while unpaired. */
    val api: StateFlow<GatewayApi?> = _api.asStateFlow()

    /** The last status check of the current pairing. */
    val status: StateFlow<StatusCheck> = _status.asStateFlow()

    /**
     * Whether a recording may start. While unpaired it is always
     * [RecordingAvailability.AVAILABLE], since recordings wait on the phone;
     * once paired it is the gateway's last answer for this pairing, or
     * [RecordingAvailability.UNAVAILABLE] if it was never asked. An unreachable
     * gateway leaves it as it was.
     */
    val recordingAvailability: StateFlow<RecordingAvailability> = _availability.asStateFlow()

    /** Why [transcribe] cannot be used now, or null when it can. */
    val transcriberBlock: StateFlow<TranscriberBlock?> = _transcriberBlock.asStateFlow()

    init {
        install(store.load())
    }

    /**
     * Asks the gateway for its status and updates [status] and
     * [recordingAvailability]. Returns null when unpaired or when the check
     * failed, so an upload drain can simply test `refreshStatus()?.acceptsRecordings == true`.
     */
    suspend fun refreshStatus(): GatewayStatus? {
        val asked = changes.withLock {
            session?.also { _status.value = StatusCheck.Pending }
        } ?: return null
        val answer = try {
            asked.api.status()
        } catch (e: GatewayError) {
            changes.withLock { if (session === asked) _status.value = StatusCheck.Failed(e) }
            return null
        }
        changes.withLock {
            if (session !== asked) return null
            lastAnswer = answer
            _status.value = StatusCheck.Answered(answer)
            _availability.value = answer.recordingAvailability
            memory.remember(asked.pairing.key, answer.recordingAvailability)
            _transcriberBlock.value = transcriberBlock(asked)
        }
        return answer
    }

    /** Pairs from a pasted or scanned payload, replacing any current pairing on success. */
    suspend fun pairFromPayload(payload: String): PairingResult =
        try {
            pair(PairingTarget.fromPayload(payload))
        } catch (e: PairingInputException) {
            PairingResult.Invalid(e.problem)
        }

    /** Pairs from the "by hand" fields, replacing any current pairing on success. */
    suspend fun pairManually(url: String, pairingCode: String, fingerprint: String): PairingResult =
        try {
            pair(PairingTarget.fromManualEntry(url, pairingCode, fingerprint))
        } catch (e: PairingInputException) {
            PairingResult.Invalid(e.problem)
        }

    /**
     * Forgets the pairing on this phone only; the gateway keeps the device
     * until its owner revokes it. A pairing still in progress is abandoned.
     * Recordings stay queued on the phone.
     */
    suspend fun unpair() {
        changes.withLock {
            supersede()
            withContext(Dispatchers.IO) { store.forget() }
            memory.forget()
            install(null)
        }
    }

    /**
     * Transcribes a short WAV for a voice command. Null when unpaired, when the
     * gateway has no transcript to give, or on any failure; failures are only
     * logged.
     */
    suspend fun transcribe(wav: File, locale: String?): String? {
        val api = _api.value ?: return null
        return try {
            api.transcribe(wav, locale)
        } catch (e: GatewayError) {
            Log.w(TAG, "Transcription failed: ${e::class.simpleName}: ${e.message}")
            null
        }
    }

    private suspend fun pair(target: PairingTarget): PairingResult = supervisorScope {
        val previous = changes.withLock { session?.pairing?.deviceId }
        val installId = try {
            withContext(Dispatchers.IO) { store.installId() }
        } catch (e: SecretBox.SealingFailed) {
            Log.w(TAG, "No install id: ${e.cause?.let { it::class.simpleName }}")
            return@supervisorScope PairingResult.NotSaved
        }
        val exchange = async(Dispatchers.IO, start = CoroutineStart.LAZY) {
            PairingExchange.pair(connector.forPairing(target.trust), target, installId, previous)
        }
        val mine = changes.withLock {
            supersede()
            inFlight = exchange
            attempt
        }
        exchange.start()
        val pairing = try {
            exchange.await()
        } catch (e: GatewayError) {
            return@supervisorScope PairingResult.Failed(e)
        } catch (e: CancellationException) {
            if (isActive) return@supervisorScope PairingResult.Superseded
            throw e
        }
        changes.withLock {
            if (attempt != mine) return@supervisorScope PairingResult.Superseded
            try {
                withContext(Dispatchers.IO) { store.save(pairing) }
            } catch (e: SecretBox.SealingFailed) {
                inFlight = null
                return@supervisorScope PairingResult.NotSaved
            }
            memory.forget()
            inFlight = null
            install(pairing)
        }
        PairingResult.Paired(PairedGateway(pairing.name, pairing.url))
    }

    /** Cancels the pairing attempt in progress, if any, so it cannot store its result. */
    private fun supersede() {
        attempt++
        inFlight?.cancel()
        inFlight = null
    }

    private fun install(pairing: Pairing?) {
        val next = pairing?.let { Session(it, connector.open(it)) }
        session = next
        lastAnswer = null
        _paired.value = pairing?.let { PairedGateway(it.name, it.url) }
        _api.value = next?.api
        _status.value = StatusCheck.Pending
        _availability.value = if (pairing == null) {
            RecordingAvailability.AVAILABLE
        } else {
            memory.recall(pairing.key) ?: RecordingAvailability.UNAVAILABLE
        }
        _transcriberBlock.value = transcriberBlock(next)
    }

    private fun transcriberBlock(current: Session?): TranscriberBlock? = when {
        current == null -> TranscriberBlock.NOT_PAIRED
        lastAnswer?.dictationEnabled == false -> TranscriberBlock.DICTATION_OFF
        else -> null
    }

    private companion object {
        const val TAG = "Manent:Gateway"
    }
}
