/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.secure.types

import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailAttachmentEntity

/**
 * Representation of a package containing a set of keys and secure email data.
 *
 * @property keyAttachments [Set<EmailAttachmentEntity>] Set of email attachments representing the
 * keys to decrypt the secure email body.
 * @property bodyAttachment [EmailAttachmentEntity] List of email attachments representing the
 * secure email body.
 */
internal data class SecurePackage(
    val keyAttachments: Set<EmailAttachmentEntity>,
    val bodyAttachment: EmailAttachmentEntity,
) {
    fun toList(): List<EmailAttachmentEntity> = keyAttachments.toList() + bodyAttachment
}
