/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
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
    KEY_EXCHANGE("Secure Data", "application/x-sudomail-key", "securekeyexchangedata@sudomail.com"),
    BODY("Secure Email", "application/x-sudomail-body", "securebody@sudomail.com"),
}
