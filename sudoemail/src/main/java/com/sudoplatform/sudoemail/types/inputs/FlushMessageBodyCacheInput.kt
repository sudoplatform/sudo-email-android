/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

/**
 * Input object for flushing the local message body cache.
 *
 * Exactly one of [sudoId] or [emailAddressId] should be provided to scope the flush.
 *
 * @property sudoId [String] Optional sudo ID. If provided, all cached message bodies
 *  belonging to this sudo will be removed.
 * @property emailAddressId [String] Optional email address ID. If provided, all cached
 *  message bodies belonging to this email address will be removed.
 */
data class FlushMessageBodyCacheInput(
    val sudoId: String? = null,
    val emailAddressId: String? = null,
)
