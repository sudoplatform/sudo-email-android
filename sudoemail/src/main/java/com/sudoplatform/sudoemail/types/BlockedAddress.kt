/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
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
 * Action to take on incoming messages from blocked addresses
 *
 */
enum class BlockedEmailAddressAction {
    /** Do nothing. Message will not appear in user's account */
    DROP,

    /** Message will be redirected to SPAM folder, if available */
    SPAM,
}

/**
 * Level at which the email address is blocked
 */
enum class BlockedEmailAddressLevel {
    /** Block just the specific email address */
    ADDRESS,

    /** Block all email addresses from the same domain */
    DOMAIN,
}

/**
 * Representation of a blocked email address used in the Sudo Platform Email SDK
 *
 * @property createdAt [Date] When the address was blocked
 * @property hashedBlockedValue [String] The hashed value of the blocked address
 * @property hashAlgorithm [BlockedAddressHashAlgorithm] The algorithm used for the hash
 * @property owner [String] Identifier of the owner of the blocklist
 * @property owners [List<Owner>] List of identifiers of the user/accounts associated with this blocklist
 * @property sealedValue [String] The sealed value of the blocked address
 * @property action [BlockedEmailAddressAction] The action to take on incoming messages from the blocked address
 * @property emailAddressId [String] If present, the blocked address is only blocked for this email address.
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
    val action: BlockedEmailAddressAction,
    val emailAddressId: String?,
    val updatedAt: Date,
) : Parcelable

/**
 * The Sudo Platform SDK representation of the status of the retrieval of a particular blocked address
 *
 * If the status is 'Failed' there will be an error showing the cause.
 */
sealed class UnsealedBlockedAddressStatus {
    object Completed : UnsealedBlockedAddressStatus()

    data class Failed(
        val cause: Exception,
    ) : UnsealedBlockedAddressStatus()
}

/**
 * The Sudo Platform SDK representation of an unsealed blocked address
 *
 * @interface UnsealedBlockedAddress
 * @property hashedBlockedValue [List<String>] The hashed value of the blocked address. This can be used to unblock the address in the event that unsealing fails
 * @property address [String] The plaintext address that has been blocked.
 * @property status [UnsealedBlockedAddressStatus] The status of the unsealing operation. If 'Failed' the plaintext address will be empty but the hashed value will still available
 * @property action [BlockedEmailAddressAction] The action to take on incoming messages from the blocked address
 * @property emailAddressId [String] If present, the blocked address is only blocked for this email address.
 */
data class UnsealedBlockedAddress(
    val hashedBlockedValue: String,
    val address: String,
    val status: UnsealedBlockedAddressStatus,
    val action: BlockedEmailAddressAction,
    val emailAddressId: String?,
)
