/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

/**
 * Defines constants relating to the Sudo Platform Email SDK.
 */
internal object Constants {
    const val SERVICE_NAME: String = "emService"

    /** Default algorithm used when creating/registering public keys. */
    const val DEFAULT_PUBLIC_KEY_ALGORITHM = "RSAEncryptionOAEPAESCBC"

    const val UUID_REGEX_PATTERN = "[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}"
}
