/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.EncryptionStatus
import jakarta.mail.MessagingException
import java.io.IOException

/**
 * Handles the processing of email message data which includes the encoding and
 * parsing of the email message content.
 */
interface EmailMessageDataProcessor {

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
        attachments: List<EmailAttachment>? = null,
        inlineAttachments: List<EmailAttachment>? = null,
        isHtml: Boolean = false,
        encryptionStatus: EncryptionStatus = EncryptionStatus.UNENCRYPTED,
    ): ByteArray

    /**
     * Parse a [ByteArray] of data to an email message.
     */
    @Throws(MessagingException::class, IOException::class)
    fun parseInternetMessageData(rfc822Data: ByteArray): SimplifiedEmailMessage
}
