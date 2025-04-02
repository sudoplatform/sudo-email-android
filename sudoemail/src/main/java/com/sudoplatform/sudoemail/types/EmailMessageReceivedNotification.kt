/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Representation of a messageReceived Email Service notification.
 * @property id [String] Identifier of received email message to which this notification corresponds.
 * @property owner [String] Subject identifier of user to whom the notification is addressed.
 * @property sudoId [String] Identifier of Sudo owning the email address.
 * @property emailAddressId [String] Identifier of email address to which this notification pertains.
 * @property folderId [String] Identifier of email folder in to which this message was stored on receipt.
 * @property from [EmailMessage.EmailAddress] Sender of the email message.
 * @property replyTo [EmailMessage.EmailAddress] First [replyTo] address, if any, of the received message.
 * @property subject [String] Up to 140 characters of the received message's Subject if any.
 * @property sentAt [Date] When the message was sent. Corresponds to the Date header of the message.
 * @property receivedAt [Date] When the message was received.
 * @property encryptionStatus [EncryptionStatus] End-to-end encryption status of the message.
 * @property hasAttachments [Boolean] Whether or not the received message has attachments.
 */
@Parcelize
data class EmailMessageReceivedNotification(
    val id: String,
    val owner: String,
    val sudoId: String,
    val emailAddressId: String,
    val folderId: String,
    val from: EmailMessage.EmailAddress,
    val replyTo: EmailMessage.EmailAddress?,
    val subject: String? = null,
    val sentAt: Date,
    val receivedAt: Date,
    val encryptionStatus: EncryptionStatus,
    val hasAttachments: Boolean,
) : Parcelable
