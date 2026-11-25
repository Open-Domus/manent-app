// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package it.manentlabs.app.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import it.manentlabs.app.transport.dto.SourceSyncStatus

/**
 * Pairing-scoped read seam for a device-hosted source's authoritative gateway status.
 * Feature modules name only their own source id; the app supplies the current session.
 */
fun interface SourceSyncStatusReader {
    suspend fun read(sourceId: String): SourceSyncStatus?

    /** A live stream in the app; the default keeps feature tests and offline hosts simple. */
    fun observe(sourceId: String): Flow<SourceSyncStatus?> = flow { emit(read(sourceId)) }
}
