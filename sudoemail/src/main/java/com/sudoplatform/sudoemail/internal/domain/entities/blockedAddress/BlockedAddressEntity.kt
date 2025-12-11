/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress

import android.os.Parcelable
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import kotlinx.parcelize.Parcelize

/**
 * Hash algorithm used to hash blocked email addresses.
 */
enum class BlockedAddressHashAlgorithmEntity {
    /** SHA-256 hash algorithm */
    SHA256,
}

/**
 * Action to take on incoming messages from blocked addresses
 */
enum class BlockedEmailAddressActionEntity {
    /** Do nothing. Message will not appear in user's account */
    DROP,

    /** Message will be redirected to SPAM folder, if available */
    SPAM,
}

/**
 * Core entity representation of a blocked email address used in the Sudo Platform Email SDK
 *
 * @property hashedBlockedValue [String] The hashed value of the blocked address
 * @property sealedValue [SealedAttributeEntity] The sealed value of the blocked address
 * @property action [BlockedEmailAddressActionEntity] The action to take on incoming messages from the blocked address
 * @property emailAddressId [String] If present, the blocked address is only blocked for this email address.
 */
@Parcelize
internal data class BlockedAddressEntity(
    val hashedBlockedValue: String,
    val sealedValue: SealedAttributeEntity,
    val action: BlockedEmailAddressActionEntity,
    val emailAddressId: String?,
) : Parcelable
