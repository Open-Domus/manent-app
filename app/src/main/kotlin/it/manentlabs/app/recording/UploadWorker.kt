// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The `recording-upload` job: repairs recordings a dead process left open,
 * then drains until a pass sends nothing and nobody asked for another, so
 * parts filed while it ran are not stranded.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun journal(): RecordingJournal
        fun uploader(): Uploader
        fun scheduler(): UploadScheduler
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        deps.journal().recover().await()
        while (true) {
            val asked = deps.scheduler().requestCount
            when (deps.uploader().drain()) {
                Uploader.Pass.RETRY -> return Result.retry()
                Uploader.Pass.SENT -> Unit
                Uploader.Pass.IDLE -> if (deps.scheduler().requestCount == asked) return Result.success()
            }
        }
    }
}
