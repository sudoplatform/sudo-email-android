/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Core entity representation of an email message's RFC 822 data used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email message.
 * @property body [String] The email message body.
 * @property isHtml [Boolean] Flag indicating whether the body is formatted as HTML.
 * @property attachments [List<EmailAttachmentEntity>] A list of email message attachments.
 * @property inlineAttachments [List<EmailAttachmentEntity>] A list of email message inline attachments.
 */
@Parcelize
internal data class EmailMessageWithBodyEntity(
    val id: String,
    val body: String,
    val isHtml: Boolean,
    val attachments: List<EmailAttachmentEntity>,
    val inlineAttachments: List<EmailAttachmentEntity>,
) : Parcelable
