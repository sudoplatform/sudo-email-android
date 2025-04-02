/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.secure.types

/**
 * The types of attachments in an email message that is end-to-end encrypted.
 *
 * @property fileName [String] Name of the attachment file.
 * @property mimeType [String] The attachment mimeType.
 * @property contentId [String] The content identifier associated with the attachment.
 *
 * @enum SecureEmailAttachmentType
 */
internal enum class SecureEmailAttachmentType(
    val fileName: String,
    val mimeType: String,
    val contentId: String,
) {
    KEY_EXCHANGE(
        "Secure Data",
        "application/x-sudoplatform-key",
        "securekeyexchangedata@sudoplatform.com",
    ),
    BODY("Secure Email", "application/x-sudoplatform-body", "securebody@sudoplatform.com"),
}

internal val LEGACY_KEY_EXCHANGE_CONTENT_ID =
    "securekeyexhangedata@sudomail.com" // Intentional misspelling of 'exchange' to match legacy system
internal val LEGACY_KEY_EXCHANGE_MIME_TYPE = "application/x-sudomail-key"
internal val LEGACY_BODY_CONTENT_ID = "securebody@sudomail.com"
internal val LEGACY_BODY_MIME_TYPE = "application/x-sudomail-body"
