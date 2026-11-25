// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package it.manentlabs.app.transport.client

import it.manentlabs.app.transport.dto.DocumentInputDto
import it.manentlabs.app.transport.dto.IngestDocumentsBody
import it.manentlabs.app.transport.dto.IngestDocumentsResponse
import it.manentlabs.app.transport.dto.ReconcileDocumentsBody
import it.manentlabs.app.transport.dto.ReconcileDocumentsResponse
import it.manentlabs.app.transport.http.GatewayHttp
import it.manentlabs.app.transport.http.postJson

/**
 * The document-ingest push surface: full-text-searchable content, alongside
 * the structured rows [AnalyticsClient] pushes. This is the generic gateway
 * capability a device-hosted source drains its documents through — e.g. the
 * Android port of the iOS `GatewayClient.ingestDocuments`, used by call log
 * (`android-call-log:local`) to push its day-aggregate documents.
 *
 * Requires a token carrying a `write:*` scope, or a `write:<sourceType>` scope
 * matching every document's source type.
 */
class DocumentsClient(private val http: GatewayHttp) {

    /** `POST /documents` — upsert a batch of documents by (providerId, sourceId, externalId). */
    suspend fun ingest(documents: List<DocumentInputDto>): IngestDocumentsResponse =
        http.postJson("documents", IngestDocumentsBody(documents))

    /**
     * `POST /documents/reconcile` — tell the gateway everything
     * `(providerId, sourceId)` currently holds. What the snapshot omits is
     * recorded with a deadline rather than deleted; see [ReconcileDocumentsBody].
     */
    suspend fun reconcile(
        providerId: String,
        sourceId: String,
        presentExternalIds: List<String>,
    ): ReconcileDocumentsResponse = http.postJson(
        "documents/reconcile",
        ReconcileDocumentsBody(providerId = providerId, sourceId = sourceId, presentExternalIds = presentExternalIds),
    )
}
