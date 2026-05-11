/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache

/**
 * Input for [EmailMessageBodyCache.flush].
 * Exactly one of [sudoId] or [emailAddressId] must be provided.
 *
 * @property sudoId [String] Remove all cache entries belonging to this sudo ID.
 * @property emailAddressId [String] Remove all cache entries belonging to this email address ID.
 */
internal data class CacheFlushInput(
    val sudoId: String? = null,
    val emailAddressId: String? = null,
)
