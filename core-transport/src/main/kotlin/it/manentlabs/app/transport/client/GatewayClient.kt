// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package it.manentlabs.app.transport.client

import it.manentlabs.app.transport.GatewayException
import it.manentlabs.app.transport.dto.IndexStats
import it.manentlabs.app.transport.dto.StatusSnapshot
import it.manentlabs.app.transport.dto.Whoami
import it.manentlabs.app.transport.http.GatewayHttp
import it.manentlabs.app.transport.http.getJson

/** Health/status/identity. Mirrors the iOS `GatewayClient` read surface. */
class GatewayClient(private val http: GatewayHttp) {

    /** Unauthenticated reachability probe. */
    suspend fun health(): Boolean = try {
        http.execute(http.newRequest(http.urlFor("health")).get().build())
        true
    } catch (e: GatewayException) {
        false
    }

    suspend fun status(): StatusSnapshot = http.getJson("status")

    suspend fun whoami(): Whoami = http.getJson("whoami")

    suspend fun indexStats(): IndexStats = http.getJson("index/stats")
}
