/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Representation of the email headers formatted under the RFC-6854 (supersedes RFC 822).
 * (https://tools.ietf.org/html/rfc6854) standard. Some further rules (beyond RFC 6854) must also be
 * applied to the data:
 *  - At least one recipient must exist (to, cc, bcc).
 *  - For all email addresses:
 *    - Total length (including both local part and domain) must not exceed 256 characters.
 *    - Local part must not exceed more than 64 characters.
 *    - Input domain parts (domain separated by `.`) must not exceed 63 characters.
 *    - Address must match standard email address pattern:
 *       `^[a-zA-Z0-9](\.?[-_a-zA-Z0-9])*@[a-zA-Z0-9](-*\.?[a-zA-Z0-9])*\.[a-zA-Z](-?[a-zA-Z0-9])+$`.
 *
 * @property from [EmailMessage.EmailAddress] The email address belonging to the sender.
 * @property to [List<EmailMessage.EmailAddress>] The email addresses belonging to the primary recipients.
 * @property cc [List<EmailMessage.EmailAddress>] The email addresses belonging to the secondary recipients.
 * @property bcc [List<EmailMessage.EmailAddress>] The email addresses belonging to additional recipients.
 * @property replyTo [List<EmailMessage.EmailAddress>] The email addresses in which responses are to be sent.
 * @property subject [String] The subject line of the email message.
 */
@Parcelize
data class InternetMessageFormatHeader(
    val from: EmailMessage.EmailAddress,
    val to: List<EmailMessage.EmailAddress>,
    val cc: List<EmailMessage.EmailAddress>,
    val bcc: List<EmailMessage.EmailAddress>,
    val replyTo: List<EmailMessage.EmailAddress>,
    val subject: String,
) : Parcelable
