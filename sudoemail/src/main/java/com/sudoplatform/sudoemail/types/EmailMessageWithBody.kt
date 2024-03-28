/*
* Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
*
* SPDX-License-Identifier: Apache-2.0
*/

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Representation of an email message's RFC 822 data used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email message.
 * @property body [String] The email message body.
 * @property attachments [List<EmailAttachment>] A list of email message attachments.
 * @property inlineAttachments [List<EmailAttachment>] A list of email message inline attachments.
 */
@Parcelize
data class EmailMessageWithBody(
    val id: String,
    val body: String,
    val attachments: List<EmailAttachment>,
    val inlineAttachments: List<EmailAttachment>,
) : Parcelable
