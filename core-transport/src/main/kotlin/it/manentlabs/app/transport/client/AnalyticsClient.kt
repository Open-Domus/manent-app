// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package it.manentlabs.app.transport.client

import it.manentlabs.app.transport.dto.AnalyticsIngestBody
import it.manentlabs.app.transport.dto.AnalyticsIngestResponse
import it.manentlabs.app.transport.dto.SyncStateBody
import it.manentlabs.app.transport.dto.SyncStateResponse
import it.manentlabs.app.transport.GatewayException
import it.manentlabs.app.transport.http.GatewayHttp
import it.manentlabs.app.transport.http.getJson
import it.manentlabs.app.transport.http.postJson
import kotlinx.serialization.json.JsonElement

/**
 * The structured-source push surface: analytics ingest plus the opaque sync-state
 * cursor. This is the generic gateway capability a device-hosted source (Health
 * Connect on Android, Apple Health on iOS) drains its pages through — the Android
 * port of the iOS `GatewayClient.ingestAnalyticsRecords` / `get`/`setSyncState`.
 *
 * Requires a token carrying a `write:*` scope for ingest and `admin` for
 * sync-state (the android device kind's default grant has both).
 */
class AnalyticsClient(private val http: GatewayHttp) {

    /** `POST /analytics/ingest` — upsert one page of one table, applying any tombstones. */
    suspend fun ingest(body: AnalyticsIngestBody): AnalyticsIngestResponse =
        http.postJson("analytics/ingest", body)

    /** `GET /sync-state/:sourceId` — the persisted cursor, or null before the first sync. */
    suspend fun getSyncState(sourceId: String): SyncStateResponse? = try {
        http.getJson<SyncStateResponse>("sync-state/$sourceId")
    } catch (_: GatewayException.NotFound) {
        null
    }

    /** `POST /sync-state/:sourceId` — persist the cursor (+ optional display label/icon). */
    suspend fun setSyncState(sourceId: String, body: SyncStateBody) {
        // Response shape isn't load-bearing; decode as a bare element and discard.
        http.postJson<SyncStateBody, JsonElement>("sync-state/$sourceId", body)
    }
}
