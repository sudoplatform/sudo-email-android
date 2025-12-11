/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMessage.transformers

import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailAttachmentEntity
import com.sudoplatform.sudoemail.types.EmailAttachment

/**
 * Transformer for converting email attachment data between API and entity representations.
 */
internal object EmailAttachmentTransformer {
    /**
     * Transforms an API email attachment to entity type.
     *
     * @param attachment [EmailAttachment] The API attachment.
     * @return [EmailAttachmentEntity] The entity.
     */
    fun apiToEntity(attachment: EmailAttachment): EmailAttachmentEntity =
        EmailAttachmentEntity(
            fileName = attachment.fileName,
            contentId = attachment.contentId,
            mimeType = attachment.mimeType,
            inlineAttachment = attachment.inlineAttachment,
            data = attachment.data,
        )

    /**
     * Transforms an [EmailAttachmentEntity] to API type.
     *
     * @param attachment [EmailAttachmentEntity] The entity to transform.
     * @return [EmailAttachment] The API type.
     */
    fun entityToApi(attachment: EmailAttachmentEntity): EmailAttachment =
        EmailAttachment(
            fileName = attachment.fileName,
            contentId = attachment.contentId,
            mimeType = attachment.mimeType,
            inlineAttachment = attachment.inlineAttachment,
            data = attachment.data,
        )
}
