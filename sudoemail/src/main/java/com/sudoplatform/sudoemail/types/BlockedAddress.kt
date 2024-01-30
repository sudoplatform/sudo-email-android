/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

enum class BlockedAddressHashAlgorithm {
    SHA256,
}

/**
 * Representation of a blocked email address used in the Sudo Platform Email SDK
 *
 * @property createdAt [Date] When the address was blocked
 * @property hashedBlockedValue [String] The hashed value of the blocked address
 * @property hashAlgorithm [BlockedAddressHashAlgorithm] The algorithm used for the hash
 * @property owner [String] Identifier of the owner of the blocklist
 * @property owners[List<Owner>] List of identifiers of the user/accounts associated with this blocklist
 * @property sealedValue [String] The sealed value of the blocked address
 * @property updatedAt [Date] When the block was updated
 * @property
 */
@Parcelize
data class BlockedAddress(
    val createdAt: Date,
    val hashedBlockedValue: String,
    val hashAlgorithm: BlockedAddressHashAlgorithm,
    val owner: String,
    val owners: List<Owner>,
    val sealedValue: String,
    val updatedAt: Date,
) : Parcelable
