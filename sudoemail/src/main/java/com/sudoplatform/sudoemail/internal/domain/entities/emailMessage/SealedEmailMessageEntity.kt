/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage

import com.sudoplatform.sudoemail.internal.domain.entities.common.OwnerEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity

/**
 * Core entity representation of a sealed (encrypted) email message used in the Sudo Platform Email SDK.
 *
 * This entity contains encrypted email message data that has not yet been unsealed/decrypted.
 *
 * @property id [String] Unique identifier of the email message.
 * @property owner [String] Identifier of the user that owns the email message.
 * @property owners [List] of [OwnerEntity] identifiers of the user/sudo associated with this email message.
 * @property emailAddressId [String] Identifier of the email address associated with the email message.
 * @property version [Int] Current version of the email message.
 * @property createdAtEpochMs [Double] When the email message was created, in milliseconds since epoch.
 * @property updatedAtEpochMs [Double] When the email message was last updated, in milliseconds since epoch.
 * @property sortDateEpochMs [Double] When the email message was processed by the service, in milliseconds since epoch.
 * @property folderId [String] Unique identifier of the email folder which the message is assigned to.
 * @property previousFolderId [String] Unique identifier of the previous email folder which the message was assigned to.
 * @property direction [DirectionEntity] Direction of the email message.
 * @property seen [Boolean] True if the user has previously seen the email message.
 * @property repliedTo [Boolean] True if the email message has been replied to.
 * @property forwarded [Boolean] True if the email message has been forwarded.
 * @property state [StateEntity] Current state of the email message.
 * @property clientRefId [String] Unique client reference identifier.
 * @property rfc822Header [SealedAttributeEntity] Sealed (encrypted) RFC822 header data.
 * @property size [Double] The size of the encrypted RFC822 data stored in the backend in bytes.
 * @property encryptionStatus [EncryptionStatusEntity] Encryption status of the email message.
 */
internal data class SealedEmailMessageEntity(
    val id: String,
    val owner: String,
    val owners: List<OwnerEntity>,
    val emailAddressId: String,
    val version: Int,
    val createdAtEpochMs: Double,
    val updatedAtEpochMs: Double,
    val sortDateEpochMs: Double,
    val folderId: String,
    val previousFolderId: String?,
    val direction: DirectionEntity,
    val seen: Boolean,
    val repliedTo: Boolean,
    val forwarded: Boolean,
    val state: StateEntity,
    val clientRefId: String?,
    val rfc822Header: SealedAttributeEntity,
    val size: Double,
    val encryptionStatus: EncryptionStatusEntity?,
)
