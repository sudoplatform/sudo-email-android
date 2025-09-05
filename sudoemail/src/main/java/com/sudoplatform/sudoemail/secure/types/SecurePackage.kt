/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.secure.types

import com.sudoplatform.sudoemail.types.EmailAttachment

/**
 * Representation of a package containing a set of keys and secure email data.
 *
 * @property keyAttachments [Set<EmailAttachment>] Set of email attachments representing the
 * keys to decrypt the secure email body.
 * @property bodyAttachment [EmailAttachment] List of email attachments representing the
 * secure email body.
 */
internal data class SecurePackage(
    val keyAttachments: Set<EmailAttachment>,
    val bodyAttachment: EmailAttachment,
) {
    fun toList(): List<EmailAttachment> = keyAttachments.toList() + bodyAttachment
}
