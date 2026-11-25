// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package it.manentlabs.app.transport.client

import it.manentlabs.app.transport.dto.ClaimedNotificationDelivery
import it.manentlabs.app.transport.dto.ConfirmNotificationBody
import it.manentlabs.app.transport.dto.OkResponse
import it.manentlabs.app.transport.http.GatewayHttp
import it.manentlabs.app.transport.http.decodeBody
import it.manentlabs.app.transport.http.postJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Device-scoped notification lease client. Notification text only crosses the paired channel.
 *
 * Every claim says this app routes the pairing and Tailscale join notifications.
 */
class NotificationsClient(private val http: GatewayHttp) {
    suspend fun claim(): ClaimedNotificationDelivery? {
        val request = http.newRequest(http.urlFor("notifications/claim"))
            .header("X-Omnesis-Pairing-Notifications", "1")
            .header("X-Omnesis-Tailscale-Join-Notifications", "1")
            .post("{}".toRequestBody(JSON_MEDIA))
            .build()
        val body = http.execute(request)
        return if (body.isBlank()) null else decodeBody(body)
    }

    suspend fun confirm(id: String) {
        http.postJson<ConfirmNotificationBody, OkResponse>(
            "notifications/confirm",
            ConfirmNotificationBody(id),
        )
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
