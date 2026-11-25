// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import android.util.Log
import it.manentlabs.app.audio.AacWriter
import it.manentlabs.app.audio.M4aReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/** A part an input has finished writing, on its way to the outbox. */
data class ClosedPart(
    val seq: Int,
    /** The audio; null for a wearable gap, which is filed silent but keeps its length. */
    val file: File?,
    val startedAtMs: Long,
    val durationMs: Long,
    val interrupted: Boolean,
)

/**
 * Every write to the outbox in one queue, run one after the other in the
 * order asked. It belongs to the app rather than to the recording service, so
 * a part closed while the system tears the service down is still filed, and a
 * recording's close never overtakes a part closed just before it.
 */
@Singleton
class RecordingJournal @Inject constructor(
    private val outbox: Outbox,
    private val storage: RecordingStorage,
    private val checker: SpeechChecker,
    private val uploads: UploadScheduler,
) {
    private val queue = Executors.newSingleThreadExecutor { Thread(it, "Manent:Journal") }

    /** Whether each running recording's latest part ended mid-speech. Memory only, by design. */
    private val endedInSpeech = HashMap<String, Boolean>()

    /** The recording being made now, which recovery and the sweep leave alone. */
    private var active: String? = null

    /** Which part numbers each running recording has filed, and the parts that failed to file. Memory only. */
    private class Filings {
        val filed = HashSet<Int>()
        val failed = HashMap<Int, ClosedPart>()
    }

    private val filings = HashMap<String, Filings>()

    fun open(session: OutboxSession): Deferred<Unit> = enqueue {
        active = session.id
        filings[session.id] = Filings()
        outbox.addSession(session)
        storage.sessionDir(session.id).mkdirs()
        uploads.request()
    }

    /** Checks a closed part for speech and files it, silent when nothing was said. */
    fun file(sessionId: String, part: ClosedPart): Deferred<FilingPolicy.Decision> = enqueue {
        try {
            fileNow(sessionId, part).also { filings[sessionId]?.filed?.add(part.seq) }
        } catch (e: Exception) {
            filings[sessionId]?.failed?.put(part.seq, part)
            throw e
        }
    }

    private fun fileNow(sessionId: String, part: ClosedPart): FilingPolicy.Decision {
        val file = part.file
        val hasAudio = file != null && file.length() > 0
        val verdict = if (hasAudio) checker.check(file!!) else SpeechVerdict.NoSpeech
        val decision = FilingPolicy.decide(hasAudio, part.durationMs, verdict, endedInSpeech[sessionId] == true)
        if (decision.silent) file?.delete()
        outbox.addChunk(
            OutboxChunk(
                sessionId = sessionId,
                seq = part.seq,
                startedAtMs = part.startedAtMs,
                durationMs = if (file != null && !hasAudio) 0 else part.durationMs,
                silent = decision.silent,
                interrupted = part.interrupted,
                path = file?.path?.takeUnless { decision.silent },
                mimeType = AacWriter.MIME_TYPE.takeUnless { decision.silent },
            ),
        )
        endedInSpeech[sessionId] = decision.endsInSpeech
        uploads.request()
        return decision
    }

    /**
     * Marks a recording stopped with its final count, after every part queued
     * before it, first filing a placeholder for any part that failed to file.
     * An all-day episode in which nothing was said is forgotten.
     */
    fun close(sessionId: String, chunkCount: Int, endedAtMs: Long): Deferred<Unit> = enqueue {
        if (active == sessionId) active = null
        endedInSpeech.remove(sessionId)
        filings.remove(sessionId)?.let { fillHoles(sessionId, chunkCount, it) }
        outbox.stop(sessionId, chunkCount, endedAtMs)
        val session = outbox.session(sessionId)
        if (session != null && session.ambient && !session.hasAudio && !session.created) {
            forget(sessionId)
        } else {
            uploads.request()
        }
    }

    private fun fillHoles(sessionId: String, chunkCount: Int, filings: Filings) {
        val fallbackStart = outbox.session(sessionId)?.let { it.lastFiledEndMs ?: it.startedAtMs } ?: return
        val holes = ChunkHoles.placeholders(chunkCount, filings.filed, filings.failed, fallbackStart)
        filings.failed.values.forEach { it.file?.delete() }
        for (hole in holes) {
            Log.w(TAG, "Part ${hole.seq} was never filed; sending it as silence")
            outbox.addChunk(
                OutboxChunk(
                    sessionId = sessionId,
                    seq = hole.seq,
                    startedAtMs = hole.startedAtMs,
                    durationMs = hole.durationMs,
                    silent = true,
                    interrupted = true,
                    path = null,
                    mimeType = null,
                ),
            )
        }
    }

    /** Throws away a recording whose input never opened. */
    fun discard(sessionId: String): Deferred<Unit> = enqueue {
        if (active == sessionId) active = null
        endedInSpeech.remove(sessionId)
        filings.remove(sessionId)
        forget(sessionId)
    }

    /** Deletes a recording on this phone only. */
    fun delete(sessionId: String): Deferred<Unit> = enqueue { forget(sessionId) }

    /**
     * Closes recordings a dead process left open, filing the parts it left on
     * disk. Returns whether anything changed.
     */
    fun recover(): Deferred<Boolean> = enqueue {
        var changed = false
        for (session in outbox.unstopped().filter { it.id != active }) {
            val files = storage.partFiles(session.id).mapValues { (seq, part) ->
                if (part.sizeBytes > 0) part.copy(playable = decodable(storage.partFile(session.id, seq))) else part
            }
            val plan = RecoveryPlan.of(session, files)
            if (plan.discard) {
                forget(session.id)
            } else {
                for (part in plan.parts) {
                    val file = storage.partFile(session.id, part.seq)
                    if (part.silent) file.delete()
                    outbox.addChunk(
                        OutboxChunk(
                            sessionId = session.id,
                            seq = part.seq,
                            startedAtMs = part.startedAtMs,
                            durationMs = part.durationMs,
                            silent = part.silent,
                            interrupted = part.interrupted,
                            path = file.path.takeUnless { part.silent },
                            mimeType = AacWriter.MIME_TYPE.takeUnless { part.silent },
                        ),
                    )
                }
                outbox.stop(session.id, plan.chunkCount, plan.endedAtMs)
            }
            Log.i(TAG, "Recovered a recording left open (${plan.parts.size} parts)")
            changed = true
        }
        if (changed) uploads.request()
        changed
    }

    private fun decodable(file: File): Boolean = try {
        M4aReader.read(file)
        true
    } catch (_: IOException) {
        Log.w(TAG, "A part left by a dead process cannot be played; filed as silence")
        false
    }

    /** Removes recording folders no outbox row names: leftovers of a delete the process died in. */
    fun sweep(): Deferred<Unit> = enqueue {
        val known = outbox.sessions().map { it.id }.toSet() + listOfNotNull(active)
        storage.sessionDirNames().filter { it !in known }.forEach(storage::deleteSession)
    }

    private fun forget(sessionId: String) {
        outbox.deleteSession(sessionId)
        storage.deleteSession(sessionId)
    }

    private fun <T> enqueue(block: () -> T): Deferred<T> {
        val result = CompletableDeferred<T>()
        queue.execute {
            try {
                result.complete(block())
            } catch (e: Exception) {
                Log.e(TAG, "Outbox write failed", e)
                result.completeExceptionally(e)
            }
        }
        return result
    }

    private companion object {
        const val TAG = "Manent:Journal"
    }
}
