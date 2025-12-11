/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.util

import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressPublicInfoEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailAttachmentEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SimplifiedEmailMessageEntity
import jakarta.mail.MessagingException
import java.io.IOException

/**
 * Handles the processing of email message data which includes the encoding and
 * parsing of the email message content.
 */
internal interface EmailMessageDataProcessor {
    /**
     * Encode an email message to a [ByteArray] of data.
     */
    @Throws(MessagingException::class, IOException::class)
    fun encodeToInternetMessageData(
        from: String,
        to: List<String>,
        cc: List<String>? = null,
        bcc: List<String>? = null,
        subject: String? = null,
        body: String? = null,
        attachments: List<EmailAttachmentEntity>? = null,
        inlineAttachments: List<EmailAttachmentEntity>? = null,
        isHtml: Boolean = false,
        encryptionStatus: EncryptionStatusEntity = EncryptionStatusEntity.UNENCRYPTED,
        replyingMessageId: String? = null,
        forwardingMessageId: String? = null,
    ): ByteArray

    /**
     * Parse a [ByteArray] of data to an email message.
     */
    @Throws(MessagingException::class, IOException::class)
    fun parseInternetMessageData(rfc822Data: ByteArray): SimplifiedEmailMessageEntity

    suspend fun processMessageData(
        messageData: SimplifiedEmailMessageEntity,
        encryptionStatus: EncryptionStatusEntity,
        emailAddressesPublicInfo: List<EmailAddressPublicInfoEntity> = emptyList(),
    ): ByteArray
}
