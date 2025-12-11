/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Core entity representation of the email headers formatted under the RFC-6854 standard.
 *
 * @property from [EmailMessageAddressEntity] The email address belonging to the sender.
 * @property to [List<EmailMessageAddressEntity>] The email addresses belonging to the primary recipients.
 * @property cc [List<EmailMessageAddressEntity>] The email addresses belonging to the secondary recipients.
 * @property bcc [List<EmailMessageAddressEntity>] The email addresses belonging to additional recipients.
 * @property replyTo [List<EmailMessageAddressEntity>] The email addresses in which responses are to be sent.
 * @property subject [String] The subject line of the email message.
 */
@Parcelize
internal data class InternetMessageFormatHeaderEntity(
    val from: EmailMessageAddressEntity,
    val to: List<EmailMessageAddressEntity>,
    val cc: List<EmailMessageAddressEntity>,
    val bcc: List<EmailMessageAddressEntity>,
    val replyTo: List<EmailMessageAddressEntity>,
    val subject: String,
) : Parcelable
