// SPDX-License-Identifier: MIT

package it.manentlabs.app.wearable.omi

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Runs GATT operations one at a time, each waiting for its own callback.
 *
 * Android's GATT client silently drops an operation started while another is
 * in flight, so every read, write, MTU request, discovery and RSSI read goes
 * through here. Only a callback of the same [Kind] for the same characteristic
 * completes the waiting operation. One that gets no answer in [timeoutMs]
 * leaves the stack's own queue stuck, so [onTimeout] is told and the owner
 * drops the link.
 */
class GattQueue(
    private val timeoutMs: Long = 5_000,
    private val onTimeout: (Kind) -> Unit,
) {
    enum class Kind { READ, WRITE, DESCRIPTOR_WRITE, MTU, DISCOVER, RSSI }

    /** A successful answer: the value read, or the number (MTU, RSSI) reported. */
    class Answer(val value: ByteArray = ByteArray(0), val number: Int = 0)

    private class Pending(val kind: Kind, val key: UUID?) {
        val result = CompletableDeferred<Answer?>()
    }

    private val mutex = Mutex()

    @Volatile
    private var pending: Pending? = null

    /**
     * Starts one operation with [start] and waits for its answer. Returns null
     * when it could not start (including a revoked permission), failed, timed
     * out or was cut short by [failPending].
     */
    suspend fun run(kind: Kind, key: UUID? = null, start: () -> Boolean): Answer? = mutex.withLock {
        val op = Pending(kind, key)
        pending = op
        try {
            val started = try {
                start()
            } catch (_: SecurityException) {
                false
            }
            if (!started) return@withLock null
            val answer = withTimeoutOrNull(timeoutMs) { op.result.await() }
            if (!op.result.isCompleted) onTimeout(kind)
            answer
        } finally {
            if (pending === op) pending = null
        }
    }

    /** Delivers a callback. [answer] is null for a failure status. */
    fun complete(kind: Kind, key: UUID?, answer: Answer?) {
        val op = pending ?: return
        if (op.kind == kind && op.key == key) op.result.complete(answer)
    }

    /** Fails the operation in flight, if any: the link it was waiting on is gone. */
    fun failPending() {
        pending?.result?.complete(null)
    }
}
