// SPDX-License-Identifier: MIT

package it.manentlabs.app.recording

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks for the outbox to be sent. There is one upload job; asking while it is
 * queued or running joins it, and the running job drains again if it was
 * asked during a pass.
 */
@Singleton
class UploadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: RecordingSettings,
) {
    private val requests = AtomicLong()

    /** Grows with every request, so a running job can tell whether it was asked again. */
    val requestCount: Long get() = requests.get()

    fun request() = enqueue(ExistingWorkPolicy.KEEP)

    /** Replaces the queued or running job, so a changed network rule applies at once. Safe: every call is idempotent. */
    fun restart() = enqueue(ExistingWorkPolicy.REPLACE)

    private fun enqueue(policy: ExistingWorkPolicy) {
        requests.incrementAndGet()
        val network = if (settings.wifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED
        val work = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, work)
    }

    private companion object {
        const val WORK_NAME = "recording-upload"
        const val BACKOFF_SECONDS = 30L
    }
}
