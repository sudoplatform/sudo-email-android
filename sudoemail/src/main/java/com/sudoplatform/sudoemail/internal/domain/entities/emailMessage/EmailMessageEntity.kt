/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage

import android.os.Parcelable
import com.sudoplatform.sudoemail.internal.domain.entities.common.OwnerEntity
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * An enumeration depicting the direction of an email message
 * in the Sudo Platform Email SDK.
 */
enum class DirectionEntity {
    INBOUND,
    OUTBOUND,
    UNKNOWN,
}

/**
 * An enumeration depicting the state of an email message
 * in the Sudo Platform Email SDK.
 */
enum class StateEntity {
    /** Outbound message is queued to be sent. */
    QUEUED,

    /** Outbound message has been sent. */
    SENT,

    /** Outbound message has been acknowledged as delivered upstream. */
    DELIVERED,

    /** Outbound message has been acknowledged as undelivered upstream. */
    UNDELIVERED,

    /** Outbound message has been acknowledged as failed upstream. */
    FAILED,

    /** Inbound message has been received. */
    RECEIVED,

    /** Email Message has been deleted. */
    DELETED,

    /** API Evolution - if this occurs, it may mean you need to update the library. */
    UNKNOWN,
}

/**
 * An enumeration depicting the encryption status on an email message
 * in the Sudo Platform Email SDK.
 */
enum class EncryptionStatusEntity {
    ENCRYPTED,
    UNENCRYPTED,
    UNKNOWN,
}

/**
 * Core entity representation of an email message used in the Sudo Platform Email SDK.
 *
 * @property id [String] Unique identifier of the email message.
 * @property clientRefId [String] Unique client reference identifier.
 * @property owner [String] Identifier of the user that owns the email message.
 * @property owners [List<OwnerEntity>] List of identifiers of the user/sudo associated with this email message.
 * @property emailAddressId [String] Identifier of the email address that is associated with the
 *  email message - which address sent/received this message.
 * @property folderId [String] Unique identifier of the email folder which the message is assigned to.
 * @property previousFolderId [String] Unique identifier of the previous email folder which the message
 *  was assigned to, if any.
 * @property seen [Boolean] True if the user has previously seen the email message.
 * @property repliedTo [Boolean] True if the email message has been replied to.
 * @property forwarded [Boolean] True if the email message has been forwarded.
 * @property direction [DirectionEntity] Direction of the email message.
 * @property state [StateEntity] Current state of the email message.
 * @property version [Int] Current version of the email message.
 * @property sortDate [Date] When the email message was processed by the service.
 * @property createdAt [Date] When the email message was created.
 * @property updatedAt [Date] When the email message was last updated.
 * @property size [Double] The size of the encrypted RFC822 data stored in the backend in bytes.
 * @property from [List<EmailMessageAddressEntity>] List of recipients that the email message was sent from.
 * @property to [List<EmailMessageAddressEntity>] List of recipients that the email message is being sent to.
 * @property cc [List<EmailMessageAddressEntity>] List of carbon copy recipients of the email message.
 * @property bcc [List<EmailMessageAddressEntity>] List of blind carbon copy recipients of the email message.
 * @property replyTo [List<EmailMessageAddressEntity>] List of recipients that a reply to this email message
 *  will be sent to.
 * @property subject [String] Subject header of the email message.
 * @property sentAt [Date] When the email message was sent.
 * @property receivedAt [Date] When the email message was received.
 * @property hasAttachments [Boolean] Whether or not the message has attachments.
 * @property encryptionStatus [EncryptionStatusEntity] The encryption status of the email message.
 * @property date [Date] The date header of the email message.
 */
@Parcelize
internal data class EmailMessageEntity(
    val id: String,
    val clientRefId: String? = null,
    val owner: String,
    val owners: List<OwnerEntity>,
    val emailAddressId: String,
    val folderId: String,
    val previousFolderId: String? = null,
    val seen: Boolean = false,
    val repliedTo: Boolean = false,
    val forwarded: Boolean = false,
    val direction: DirectionEntity,
    val state: StateEntity,
    val version: Int,
    val sortDate: Date,
    val createdAt: Date,
    val updatedAt: Date,
    val size: Double,
    val from: List<EmailMessageAddressEntity>,
    val to: List<EmailMessageAddressEntity>,
    val cc: List<EmailMessageAddressEntity> = emptyList(),
    val bcc: List<EmailMessageAddressEntity> = emptyList(),
    val replyTo: List<EmailMessageAddressEntity> = emptyList(),
    val subject: String? = null,
    val sentAt: Date? = null,
    val receivedAt: Date? = null,
    val hasAttachments: Boolean,
    val encryptionStatus: EncryptionStatusEntity,
    val date: Date? = null,
    val keyId: String,
    val algorithm: String,
) : Parcelable
