/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage

import com.sudoplatform.sudoemail.internal.domain.entities.common.OwnerEntity
import java.util.Date

/**
 * Core entity representation of a partial email message used in the Sudo Platform Email SDK.
 *
 * This entity represents an email message that could not be fully unsealed/decrypted.
 *
 * @property id [String] Unique identifier of the email message.
 * @property clientRefId [String] Unique client reference identifier.
 * @property owner [String] Identifier of the user that owns the email message.
 * @property owners [List] of [OwnerEntity] identifiers of the user/sudo associated with this email message.
 * @property emailAddressId [String] Identifier of the email address associated with the email message.
 * @property folderId [String] Unique identifier of the email folder which the message is assigned to.
 * @property previousFolderId [String] Unique identifier of the previous email folder which the message was assigned to.
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
 * @property encryptionStatus [EncryptionStatusEntity] Encryption status of the email message.
 * @property date [Date] The date the email message was sent or received.
 */
internal data class PartialEmailMessageEntity(
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
    val encryptionStatus: EncryptionStatusEntity,
    val date: Date? = null,
)
