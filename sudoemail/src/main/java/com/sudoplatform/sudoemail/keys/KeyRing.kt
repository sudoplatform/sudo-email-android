/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import com.sudoplatform.sudouser.PublicKey

/**
 * A container of public keys.
 *
 * @property id [String] Identifier of the key ring
 * @property keys [List<PublicKey>] The public keys contained in the key ring
 */
internal data class KeyRing(
    val id: String,
    val keys: List<PublicKey> = emptyList(),
)
