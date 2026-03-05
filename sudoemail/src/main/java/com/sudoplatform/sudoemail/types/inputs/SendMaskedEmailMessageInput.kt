/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.inputs

import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.EmailMask
import com.sudoplatform.sudoemail.types.InternetMessageFormatHeader

/**
 * Input object containing information required to send an email message.
 *
 * @property senderEmailMaskId [String] Identifier of the [EmailMask] being used to
 *  send the email. The identifier must match the identifier of the mask of the `from` field
 *  in the RFC 6854 data.
 * @property emailMessageHeader [InternetMessageFormatHeader] The email message headers.
 * @property body [String] The text body of the email message.
 * @property attachments [List<EmailAttachment>] List of attached files to be sent with the message.
 *  Default is an empty list.
 * @property inlineAttachment [List<EmailAttachment>] List of inline attachments to be sent with the message.
 *  Default is an empty list.
 * @property replyingMessageId [String] Identifier of the message being replied to. Defaults to null.
 * @property forwardingMessageId [String] Identifier of the message being forwarded. Defaults to null.
 */
data class SendMaskedEmailMessageInput(
    val senderEmailMaskId: String,
    val emailMessageHeader: InternetMessageFormatHeader,
    val body: String,
    val attachments: List<EmailAttachment> = emptyList(),
    val inlineAttachment: List<EmailAttachment> = emptyList(),
    var replyingMessageId: String? = null,
    val forwardingMessageId: String? = null,
)
