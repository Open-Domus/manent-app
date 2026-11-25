// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manentlabs.app.audio.AudioHub
import it.manentlabs.app.gateway.Gateway
import it.manentlabs.app.recording.StartPolicy.Phase
import it.manentlabs.app.wearable.CaptureAllDaySetting
import it.manentlabs.app.wearable.RecorderControl
import it.manentlabs.app.wearable.RecorderState
import it.manentlabs.app.wearable.RecordingInput
import it.manentlabs.app.wearable.Wearable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's one recorder: starts, pauses, resumes and stops recordings from
 * the phone or the wearable, runs all-day episodes back to back, and keeps
 * [status] for the screen, the notification and the pendant.
 *
 * Every decision runs on one control thread, so requests from the screen,
 * the notification and the pendant never race. Inputs open and close on that
 * thread too (briefly blocking it), so nothing can interleave with them. Outbox writes go through the
 * [RecordingJournal]; the [RecordingService] only keeps the process in the
 * foreground while something records.
 */
@Singleton
class Recorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val journal: RecordingJournal,
    private val storage: RecordingStorage,
    private val allDay: CaptureAllDaySetting,
    private val wearable: Wearable,
    private val hub: AudioHub,
    private val detectors: VoiceDetectors,
    private val moments: MomentSender,
    private val gateway: Gateway,
) : RecorderControl {
    private val control = Executors.newSingleThreadExecutor { Thread(it, "Manent:Recorder") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + control)

    private val _status = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
    val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    override val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** True while a recording is starting, running or closing: the service must stay in the foreground. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    override val captureAllDay: StateFlow<Boolean> get() = allDay.on

    /** The wearable's name for the app's text. */
    val wearableName: String get() = wearable.audio.name

    /** One recording in progress. Touched on the control thread only. */
    private inner class Live(val request: StartRequest, val id: String, startedAtMs: Long) {
        val numbers = ChunkNumbers()
        val clock = EpisodeClock(startedAtMs)
        lateinit var source: InputSource
        var recordedBeforeMs = 0L
        var runningSince: Long? = null
        var filed = 0
        var inputLost = false
        var userPaused = false
        var watcher: Job? = null
        var ticker: Job? = null

        fun elapsedMs(): Long = recordedBeforeMs + (runningSince?.let { SystemClock.elapsedRealtime() - it } ?: 0)
    }

    private var phase = Phase.IDLE
    private var current: StartRequest? = null
    private var live: Live? = null
    private var queued: StartRequest? = null
    private var stopAfterStart = false

    @Volatile private var markable: WearableInput? = null

    private val foregroundWaits = ConcurrentHashMap<Long, CompletableDeferred<Boolean>>()
    private val tokens = AtomicLong()

    /**
     * The token of the start that last put the service in the foreground, for
     * as long as something records; 0 once the recorder is idle. Control thread only.
     */
    private var foregroundToken = 0L

    /** Whether recordings may start now: a gateway is paired and takes them. */
    val gate: RecordingGate get() = RecordingGate.of(gateway.paired.value != null, gateway.recordingAvailability.value)

    /**
     * Asks for a recording. False when it is refused at once: no gateway to
     * take it, or a wearable that is not linked.
     */
    fun start(request: StartRequest): Boolean {
        if (gate != RecordingGate.OPEN) return false
        if (request.input == RecordingInput.WEARABLE && !wearable.audio.ready.value) return false
        scope.launch { onRequest(request) }
        return true
    }

    override fun startWearableRecording(): Boolean = start(StartRequest(RecordingInput.WEARABLE, title = null, ambient = false))

    override fun startAmbient(): Boolean = start(StartRequest(RecordingInput.WEARABLE, title = null, ambient = true))

    override fun stop() {
        scope.launch { stopNow() }
    }

    /** Stop as the user asks it from the screen or notification: stopping an episode also turns all-day capture off. */
    fun stopFromUser() {
        scope.launch {
            if (current?.ambient == true) allDay.set(false)
            stopNow()
        }
    }

    fun pause() {
        scope.launch { pauseNow() }
    }

    fun resume() {
        scope.launch { resumeNow() }
    }

    override fun setCaptureAllDay(on: Boolean) = allDay.set(on)

    override suspend fun markMoment(): Boolean = markable?.markMoment() ?: false

    /** The service's answer to going foreground for the start with [token]. */
    fun onForeground(token: Long, entered: Boolean) {
        foregroundWaits.remove(token)?.complete(entered)
    }

    /**
     * The service instance that last entered the foreground for [token] is
     * being torn down: close the recording, keeping what it holds. A service
     * that is going away because the recorder went idle carries an older token,
     * and a recording begun since then is left alone: its own start brings a
     * service back to the foreground.
     */
    fun onServiceDestroyed(token: Long) {
        scope.launch {
            if (token == 0L || token != foregroundToken) return@launch
            queued = null
            stopNow()
        }
    }

    private suspend fun onRequest(request: StartRequest) {
        when (StartPolicy.decide(phase, current?.ambient == true, queued?.ambient, request)) {
            StartPolicy.Decision.BEGIN -> begin(request)
            StartPolicy.Decision.REPLACE_EPISODE -> {
                queued = request
                if (phase == Phase.STARTING) stopAfterStart = true else closeLive()
            }
            StartPolicy.Decision.TAKE_NEXT_EPISODE -> queued = request
            StartPolicy.Decision.IGNORE -> Log.i(TAG, "Start ignored: a recording is already in progress")
        }
    }

    private suspend fun stopNow() {
        when (phase) {
            Phase.STARTING -> stopAfterStart = true
            Phase.RUNNING -> closeLive()
            Phase.IDLE, Phase.CLOSING -> Unit
        }
    }

    private suspend fun begin(request: StartRequest) {
        phase = Phase.STARTING
        current = request
        _busy.value = true
        if (gate != RecordingGate.OPEN) {
            Log.i(TAG, "Start refused: no gateway takes recordings")
            return settle()
        }
        var entered = enterForeground(request.input)
        if (entered && request.input == RecordingInput.WEARABLE && !wearable.audio.ready.value) {
            Log.i(TAG, "Wearable start refused: not linked")
            entered = false
        }
        if (!entered) return settle()

        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val session = OutboxSession(
            id = UUID.randomUUID().toString(),
            title = request.title?.trim()?.takeIf { it.isNotEmpty() },
            startedAtMs = now,
            zoneId = zone.id,
            utcOffsetSeconds = zone.rules.getOffset(Instant.ofEpochMilli(now)).totalSeconds,
            language = Locale.getDefault().toLanguageTag(),
            ambient = request.ambient,
        )
        journal.open(session)
        val l = Live(request, session.id, now)
        l.source = sourceFor(l)
        live = l
        phase = Phase.RUNNING
        publish()
        if (!openRun(l)) {
            Log.w(TAG, "Input did not open; recording discarded")
            l.source.release()
            journal.discard(session.id)
            live = null
            return settle()
        }
        l.ticker = scope.launch { watchLimits(l) }
        if (stopAfterStart) {
            stopAfterStart = false
            closeLive()
        }
    }

    /** Nothing records any more: start what waited, or let the service go. */
    private suspend fun settle() {
        phase = Phase.IDLE
        current = null
        stopAfterStart = false
        publish()
        val next = queued
        queued = null
        if (next != null && (!next.ambient || allDay.on.value)) {
            begin(next)
        } else {
            foregroundToken = 0
            _busy.value = false
        }
    }

    private fun openRun(l: Live): Boolean {
        if (!l.source.open()) return false
        l.runningSince = SystemClock.elapsedRealtime()
        l.inputLost = false
        l.userPaused = false
        if (l.request.ambient) markable = l.source as? WearableInput
        publish()
        return true
    }

    private fun closeRun(l: Live) {
        markable = null
        l.source.close()
        stopClock(l)
    }

    private fun stopClock(l: Live) {
        l.runningSince?.let { l.recordedBeforeMs += SystemClock.elapsedRealtime() - it }
        l.runningSince = null
    }

    private suspend fun pauseNow() {
        val l = live?.takeIf { phase == Phase.RUNNING } ?: return
        when {
            l.runningSince != null -> closeRun(l)
            l.inputLost -> {
                l.watcher?.cancel()
                l.inputLost = false
            }
            else -> return
        }
        l.userPaused = true
        publish()
    }

    private suspend fun resumeNow() {
        val l = live?.takeIf { phase == Phase.RUNNING && it.runningSince == null } ?: return
        l.watcher?.cancel()
        l.userPaused = false
        if (!openRun(l)) inputLost(l)
        publish()
    }

    private fun onInputLost(l: Live) {
        if (live !== l || phase != Phase.RUNNING || l.runningSince == null) return
        markable = null
        stopClock(l)
        inputLost(l)
        publish()
    }

    private fun inputLost(l: Live) {
        l.inputLost = true
        if (l.request.input == RecordingInput.WEARABLE) watchForReturn(l)
    }

    /**
     * Resumes when the wearable is back, unless the user paused meanwhile;
     * gives up after 10 minutes. "Back" means readiness was seen to drop and
     * then return, so a stale "ready" is not taken for a reconnection.
     */
    private fun watchForReturn(l: Live) {
        l.watcher?.cancel()
        l.watcher = scope.launch {
            val ready = wearable.audio.ready
            val back = withTimeoutOrNull(RecordingLimits.INPUT_LOSS_GIVE_UP_MS) {
                ready.first { !it }
                ready.first { it }
            }
            if (live !== l) return@launch
            if (back == null) {
                Log.i(TAG, "Wearable gone for 10 minutes; recording stopped")
                scope.launch { if (live === l) closeLive() }
            } else if (!l.userPaused && l.runningSince == null) {
                if (!openRun(l)) watchForReturn(l)
            }
        }
    }

    /** Stops at 4 hours, and closes an all-day episode after 90 s without speech, opening the next. */
    private suspend fun watchLimits(l: Live) {
        while (live === l) {
            delay(TICK_MS)
            val limit = l.elapsedMs() >= RecordingLimits.MAX_SESSION_MS
            val quiet = l.request.ambient && l.clock.expired(System.currentTimeMillis())
            if (limit || quiet) {
                scope.launch { if (l.request.ambient) rollOver(l) else if (live === l) closeLive() }
                return
            }
        }
    }

    private suspend fun rollOver(l: Live) {
        if (live !== l || phase != Phase.RUNNING) return
        if (allDay.on.value && queued == null) queued = StartRequest(RecordingInput.WEARABLE, title = null, ambient = true)
        closeLive()
    }

    private suspend fun closeLive() {
        val l = live ?: return
        if (phase != Phase.RUNNING) return
        phase = Phase.CLOSING
        l.watcher?.cancel()
        l.ticker?.cancel()
        if (l.runningSince != null) closeRun(l)
        markable = null
        l.source.release()
        try {
            journal.close(l.id, l.numbers.count, System.currentTimeMillis()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Could not close the recording in the outbox", e)
        }
        live = null
        settle()
    }

    private fun onPartClosed(l: Live, part: ClosedPart) {
        val filing = journal.file(l.id, part)
        scope.launch {
            val decision = try {
                filing.await()
            } catch (_: Exception) {
                return@launch
            }
            l.filed++
            if (decision.heldSpeech) l.clock.onSpeechPart(part.startedAtMs + part.durationMs)
            if (live === l) publish()
        }
    }

    private fun sourceFor(l: Live): InputSource {
        val events = object : InputEvents {
            override fun onPartClosed(part: ClosedPart) = onPartClosed(l, part)

            override fun onInputLost() {
                scope.launch { onInputLost(l) }
            }
        }
        val partFile = { seq: Int -> storage.partFile(l.id, seq) }
        return when (l.request.input) {
            RecordingInput.PHONE -> PhoneInput(context, partFile, l.numbers, events)
            RecordingInput.WEARABLE -> WearableInput(
                hub.open(),
                partFile,
                l.numbers,
                events,
                detectors,
                object : WearableInput.Moments {
                    override fun newClipFile(): File = storage.newMomentFile()

                    override fun onClip(file: File, markedAt: Instant) = moments.send(file, markedAt, l.id)
                },
            )
        }
    }

    private suspend fun enterForeground(input: RecordingInput): Boolean {
        if (!RecordingService.canEnter(context, input)) {
            Log.w(TAG, "Recording refused: a permission its foreground service needs is missing")
            return false
        }
        val token = tokens.incrementAndGet()
        val answer = CompletableDeferred<Boolean>()
        foregroundWaits[token] = answer
        try {
            ContextCompat.startForegroundService(context, RecordingService.enterIntent(context, input, token))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Recording service refused to start", e)
            foregroundWaits.remove(token)
            return false
        }
        val entered = withTimeoutOrNull(FOREGROUND_TIMEOUT_MS) { answer.await() } == true
        foregroundWaits.remove(token)
        if (entered) foregroundToken = token
        return entered
    }

    private fun publish() {
        val l = live
        if (l == null) {
            _status.value = RecordingStatus.Idle
            _state.value = RecorderState.Idle
            return
        }
        _status.value = RecordingStatus.Active(
            sessionId = l.id,
            title = l.request.title?.trim()?.takeIf { it.isNotEmpty() },
            input = l.request.input,
            ambient = l.request.ambient,
            recordedBeforeMs = l.recordedBeforeMs,
            runningSince = l.runningSince,
            partsFiled = l.filed,
            inputLost = l.inputLost,
        )
        _state.value = RecorderState.Active(l.request.input, l.request.ambient)
    }

    private companion object {
        const val TAG = "Manent:Recorder"
        const val TICK_MS = 1_000L
        const val FOREGROUND_TIMEOUT_MS = 10_000L
    }
}
