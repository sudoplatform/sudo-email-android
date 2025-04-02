/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.notifications

import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.EncryptionStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A messageReceived Email Service notification
 *
 * @property type [String] Type of notification. Always [MessageReceivedNotification.TYPE]
 * @property owner [String] Subject identifier of user to whom the notification is addressed.
 * @property emailAddressId [String] Identifier of email address to which this notification pertains.
 * @property sudoId [String] Identifier of Sudo owning the email address.
 * @property messageId [String] Identifier of received email message to which this notification corresponds.
 * @property folderId [String] Identifier of email folder in to which this message was stored on receipt.
 * @property encryptionStatus [EncryptionStatus] End-to-end encryption status of the message.
 * @property subject [String] Up to 140 characters of the received message's Subject if any.
 * @property from [EmailMessage.EmailAddress] Sender of the email message.
 * @property replyTo [EmailMessage.EmailAddress] First [replyTo] address, if any, of the received message.
 * @property hasAttachments [Boolean] Whether or not the received message has attachments.
 * @property sentAtEpochMs [Long] When the message was sent. Corresponds to the Date header of the message.
 * @property receivedAtEpochMs [Long] When the message was received.
 */
@Serializable
@SerialName(MessageReceivedNotification.TYPE) // Value of type property
class MessageReceivedNotification(
    override val type: String,
    override val owner: String,
    override val emailAddressId: String,
    val sudoId: String,
    val messageId: String,
    val folderId: String,
    val encryptionStatus: EncryptionStatus,
    val subject: String?,
    val from: EmailMessage.EmailAddress,
    val replyTo: EmailMessage.EmailAddress? = null,
    val hasAttachments: Boolean,
    val sentAtEpochMs: Long,
    val receivedAtEpochMs: Long,
) : EmailAddressNotification() {
    internal companion object {
        const val TYPE = "messageReceived"
    }
}
