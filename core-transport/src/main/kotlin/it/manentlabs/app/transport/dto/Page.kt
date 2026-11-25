// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Lorenzo Farnararo

package it.manentlabs.app.transport.dto

import kotlinx.serialization.Serializable

/** Generic pagination envelope used by every list endpoint. Mirrors the iOS `Page<T>`. */
@Serializable
data class Page<T>(
    val items: List<T> = emptyList(),
    val pageInfo: PageInfo = PageInfo(),
)

@Serializable
data class PageInfo(
    val hasMore: Boolean = false,
    val limit: Int = 0,
    val nextCursor: String? = null,
)
