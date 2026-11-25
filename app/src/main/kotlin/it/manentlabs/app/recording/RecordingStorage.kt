// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manentlabs.app.audio.AacWriter
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where recorded audio lives: one folder per recording under the no-backup
 * files, so audio never leaves the phone through a backup, and a folder for
 * marked-moment clips on their way to the gateway.
 */
@Singleton
class RecordingStorage @Inject constructor(@ApplicationContext context: Context) {
    private val root = File(context.noBackupFilesDir, "recordings")
    private val moments = File(context.noBackupFilesDir, "marked-moments")

    fun sessionDir(sessionId: String): File = File(root, sessionId)

    fun partFile(sessionId: String, seq: Int): File = File(sessionDir(sessionId), PartNames.name(seq))

    fun newMomentFile(): File {
        moments.mkdirs()
        return File(moments, "moment-${UUID.randomUUID()}.${AacWriter.EXTENSION}")
    }

    /** The part files of a recording on disk, by number. */
    fun partFiles(sessionId: String): Map<Int, RecoveryPlan.DiskPart> =
        sessionDir(sessionId).listFiles().orEmpty().mapNotNull { file ->
            PartNames.seqOf(file.name)?.let { it to RecoveryPlan.DiskPart(file.length(), file.lastModified()) }
        }.toMap()

    fun deleteSession(sessionId: String) {
        sessionDir(sessionId).deleteRecursively()
    }

    /** Folder names under the recordings root, each meant to be a recording id. */
    fun sessionDirNames(): List<String> = root.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }
}

/** Part file names: `chunk-NNNNN.m4a`, the number zero-padded to five digits. */
object PartNames {
    private val PATTERN = Regex("""chunk-(\d{5})\.${AacWriter.EXTENSION}""")

    fun name(seq: Int): String = String.format(Locale.ROOT, "chunk-%05d.%s", seq, AacWriter.EXTENSION)

    fun seqOf(name: String): Int? = PATTERN.matchEntire(name)?.groupValues?.get(1)?.toInt()
}
