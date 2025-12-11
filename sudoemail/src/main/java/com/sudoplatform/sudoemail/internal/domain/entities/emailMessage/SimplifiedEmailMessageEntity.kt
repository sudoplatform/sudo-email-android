/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Core entity representation of a simplified email message used in the Sudo Platform Email SDK.
 *
 * This entity contains the basic structure of an email message with simplified recipient lists.
 *
 * @property from [List] of [String] email addresses the message is from.
 * @property to [List] of [String] email addresses the message is being sent to.
 * @property cc [List] of [String] email addresses that are being carbon copied.
 * @property bcc [List] of [String] email addresses that are being blind carbon copied.
 * @property subject [String] The subject of the email message.
 * @property body [String] The body content of the email message.
 * @property isHtml [Boolean] True if the body is HTML formatted, false if plain text.
 * @property attachments [List] of [EmailAttachmentEntity] file attachments.
 * @property inlineAttachments [List] of [EmailAttachmentEntity] inline attachments.
 * @property replyingMessageId [String] Optional ID of the message being replied to.
 * @property forwardingMessageId [String] Optional ID of the message being forwarded.
 */
@Parcelize
internal data class SimplifiedEmailMessageEntity(
    val from: List<String>,
    val to: List<String>,
    val cc: List<String>,
    val bcc: List<String>,
    val subject: String?,
    val body: String?,
    val isHtml: Boolean,
    val attachments: List<EmailAttachmentEntity> = emptyList(),
    val inlineAttachments: List<EmailAttachmentEntity> = emptyList(),
    val replyingMessageId: String? = null,
    val forwardingMessageId: String? = null,
) : Parcelable
