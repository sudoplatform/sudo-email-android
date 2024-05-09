/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import com.sudoplatform.sudoemail.util.DefaultingEnumSerializer
import kotlinx.serialization.Serializable

/**
 * An enumeration depicting the encryption status on an email message
 * in the Sudo Platform Email SDK.
 *
 * @enum EncryptionStatus
 */
@Serializable(with = EncryptionStatusSerializer::class)
enum class EncryptionStatus {
    ENCRYPTED,
    UNENCRYPTED,

    /** API Evolution - if this occurs, it may mean you need to update the library. */
    UNKNOWN,
}

internal object EncryptionStatusSerializer : DefaultingEnumSerializer<EncryptionStatus>(EncryptionStatus.entries, EncryptionStatus.UNKNOWN)
