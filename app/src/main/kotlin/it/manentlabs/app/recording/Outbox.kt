// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The upload queue: recordings not yet fully sent to the gateway and their
 * parts not yet acknowledged. Survives process death; a recording's parts go
 * with it when it is deleted. Safe to use from several threads.
 */
@Singleton
class Outbox @Inject constructor(@ApplicationContext context: Context) {
    private val helper = Helper(context)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    fun addSession(session: OutboxSession) {
        db.insertOrThrow(SESSIONS, null, ContentValues().apply {
            put("id", session.id)
            put("title", session.title)
            put("started_at", session.startedAtMs)
            put("zone_id", session.zoneId)
            put("utc_offset", session.utcOffsetSeconds)
            put("language", session.language)
            put("ambient", session.ambient)
        })
    }

    /** Files a part and moves the recording's filed count and end along with it. */
    fun addChunk(chunk: OutboxChunk) = db.inTransaction {
        insertWithOnConflict(CHUNKS, null, ContentValues().apply {
            put("session_id", chunk.sessionId)
            put("seq", chunk.seq)
            put("started_at", chunk.startedAtMs)
            put("duration_ms", chunk.durationMs)
            put("silent", chunk.silent)
            put("interrupted", chunk.interrupted)
            put("path", chunk.path)
            put("mime", chunk.mimeType)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        execSQL(
            """UPDATE $SESSIONS SET
                 filed_count = MAX(filed_count, ?),
                 last_filed_end = MAX(COALESCE(last_filed_end, 0), ?),
                 has_audio = has_audio OR ?
               WHERE id = ?""",
            arrayOf<Any>(chunk.seq + 1, chunk.startedAtMs + chunk.durationMs, if (chunk.silent) 0 else 1, chunk.sessionId),
        )
    }

    fun stop(sessionId: String, chunkCount: Int, endedAtMs: Long) =
        update(sessionId, ContentValues().apply {
            put("chunk_count", chunkCount)
            put("ended_at", endedAtMs)
        })

    fun setCreated(sessionId: String, created: Boolean) = update(sessionId, ContentValues().apply { put("created", created) })

    fun refuse(sessionId: String, reason: String) = update(sessionId, ContentValues().apply { put("refusal", reason) })

    fun removeChunk(sessionId: String, seq: Int) {
        db.delete(CHUNKS, "session_id = ? AND seq = ?", arrayOf(sessionId, seq.toString()))
    }

    fun deleteSession(sessionId: String) {
        db.delete(SESSIONS, "id = ?", arrayOf(sessionId))
    }

    fun session(sessionId: String): OutboxSession? =
        db.query(SESSIONS, null, "id = ?", arrayOf(sessionId), null, null, null).use { c ->
            if (c.moveToFirst()) c.session() else null
        }

    /** Every recording, oldest first. */
    fun sessions(): List<OutboxSession> =
        db.query(SESSIONS, null, null, null, null, null, "started_at ASC").use { c -> c.all { session() } }

    /** Recordings that were never stopped: the process died while they ran, or one runs now. */
    fun unstopped(): List<OutboxSession> =
        db.query(SESSIONS, null, "chunk_count IS NULL", null, null, null, "started_at ASC").use { c -> c.all { session() } }

    /** The parts of a recording still to send, in order. */
    fun chunks(sessionId: String): List<OutboxChunk> =
        db.query(CHUNKS, null, "session_id = ?", arrayOf(sessionId), null, null, "seq ASC").use { c -> c.all { chunk() } }

    /** Every recording with how many of its parts holding audio wait to be sent. */
    fun localRecordings(): List<LocalRecording> {
        val unsent = db.rawQuery("SELECT session_id, COUNT(*) FROM $CHUNKS WHERE silent = 0 GROUP BY session_id", null).use { c ->
            c.all { getString(0) to getInt(1) }.toMap()
        }
        return sessions().map { LocalRecording(it, unsent[it.id] ?: 0) }
    }

    private fun update(sessionId: String, values: ContentValues) {
        db.update(SESSIONS, values, "id = ?", arrayOf(sessionId))
    }

    private fun Cursor.session() = OutboxSession(
        id = string("id")!!,
        title = string("title"),
        startedAtMs = long("started_at")!!,
        zoneId = string("zone_id")!!,
        utcOffsetSeconds = long("utc_offset")!!.toInt(),
        language = string("language")!!,
        ambient = flag("ambient"),
        chunkCount = long("chunk_count")?.toInt(),
        endedAtMs = long("ended_at"),
        created = flag("created"),
        refusal = string("refusal"),
        filedCount = long("filed_count")!!.toInt(),
        lastFiledEndMs = long("last_filed_end"),
        hasAudio = flag("has_audio"),
    )

    private fun Cursor.chunk() = OutboxChunk(
        sessionId = string("session_id")!!,
        seq = long("seq")!!.toInt(),
        startedAtMs = long("started_at")!!,
        durationMs = long("duration_ms")!!,
        silent = flag("silent"),
        interrupted = flag("interrupted"),
        path = string("path"),
        mimeType = string("mime"),
    )

    private fun Cursor.string(column: String): String? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }

    private fun Cursor.long(column: String): Long? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getLong(it) }

    private fun Cursor.flag(column: String): Boolean = getLong(getColumnIndexOrThrow(column)) != 0L

    private inline fun <T> Cursor.all(row: Cursor.() -> T): List<T> {
        val rows = ArrayList<T>(count)
        while (moveToNext()) rows += row()
        return rows
    }

    private inline fun SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    /**
     * Version 1 of this app's own schema. It has a new file name: the outbox
     * of earlier builds is not read.
     */
    private class Helper(context: Context) : SQLiteOpenHelper(context, NAME, null, 1) {
        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE $SESSIONS (
                     id TEXT PRIMARY KEY NOT NULL,
                     title TEXT,
                     started_at INTEGER NOT NULL,
                     zone_id TEXT NOT NULL,
                     utc_offset INTEGER NOT NULL,
                     language TEXT NOT NULL,
                     ambient INTEGER NOT NULL DEFAULT 0,
                     chunk_count INTEGER,
                     ended_at INTEGER,
                     created INTEGER NOT NULL DEFAULT 0,
                     refusal TEXT,
                     filed_count INTEGER NOT NULL DEFAULT 0,
                     last_filed_end INTEGER,
                     has_audio INTEGER NOT NULL DEFAULT 0
                   )""",
            )
            db.execSQL(
                """CREATE TABLE $CHUNKS (
                     session_id TEXT NOT NULL REFERENCES $SESSIONS(id) ON DELETE CASCADE,
                     seq INTEGER NOT NULL,
                     started_at INTEGER NOT NULL,
                     duration_ms INTEGER NOT NULL,
                     silent INTEGER NOT NULL,
                     interrupted INTEGER NOT NULL,
                     path TEXT,
                     mime TEXT,
                     PRIMARY KEY (session_id, seq)
                   )""",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val NAME = "manent-recordings.db"
        const val SESSIONS = "sessions"
        const val CHUNKS = "chunks"
    }
}
