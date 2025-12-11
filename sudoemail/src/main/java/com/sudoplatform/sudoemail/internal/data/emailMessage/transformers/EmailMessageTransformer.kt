/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMessage.transformers

import com.google.gson.Gson
import com.sudoplatform.sudoemail.graphql.fragment.SealedEmailMessage
import com.sudoplatform.sudoemail.graphql.type.EmailMessageDirection
import com.sudoplatform.sudoemail.graphql.type.EmailMessageEncryptionStatus
import com.sudoplatform.sudoemail.graphql.type.EmailMessageState
import com.sudoplatform.sudoemail.internal.data.common.mechanisms.Unsealer
import com.sudoplatform.sudoemail.internal.data.common.transformers.OwnerTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.KeyInfo
import com.sudoplatform.sudoemail.internal.domain.entities.common.KeyType
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.DirectionEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.PartialEmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SealedEmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.StateEntity
import com.sudoplatform.sudoemail.internal.util.toDate
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.types.Direction
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.EncryptionStatus
import com.sudoplatform.sudoemail.types.PartialEmailMessage
import com.sudoplatform.sudoemail.types.State
import java.util.Date

internal data class EmailHeaderDetails(
    val from: List<EmailMessageAddressEntity>,
    val to: List<EmailMessageAddressEntity>,
    val cc: List<EmailMessageAddressEntity>,
    val bcc: List<EmailMessageAddressEntity>,
    val replyTo: List<EmailMessageAddressEntity>,
    val hasAttachments: Boolean = false,
    val subject: String? = null,
    val date: Date? = null,
    val inReplyTo: String? = null,
    val references: List<String>? = null,
)

internal object EmailMessageTransformer {
    fun EmailMessage.EmailAddress.toEmailMessageAddressEntity(): EmailMessageAddressEntity =
        EmailMessageAddressEntity(
            emailAddress = this.emailAddress,
            displayName = this.displayName,
        )

    fun graphQLToSealedEntity(sealedEmailMessage: SealedEmailMessage): SealedEmailMessageEntity =
        SealedEmailMessageEntity(
            id = sealedEmailMessage.id,
            clientRefId = sealedEmailMessage.clientRefId,
            owner = sealedEmailMessage.owner,
            owners = sealedEmailMessage.owners.map { OwnerTransformer.graphQLToEntity(it) },
            emailAddressId = sealedEmailMessage.emailAddressId,
            folderId = sealedEmailMessage.folderId,
            previousFolderId = sealedEmailMessage.previousFolderId,
            seen = sealedEmailMessage.seen,
            repliedTo = sealedEmailMessage.repliedTo,
            forwarded = sealedEmailMessage.forwarded,
            direction = sealedEmailMessage.direction.toEmailMessageDirectionEntity(),
            state = sealedEmailMessage.state.toEmailMessageStateEntity(),
            version = sealedEmailMessage.version,
            size = sealedEmailMessage.size,
            encryptionStatus =
                sealedEmailMessage.encryptionStatus
                    ?.toEncryptionStatusEntity() ?: EncryptionStatusEntity.UNENCRYPTED,
            createdAtEpochMs = sealedEmailMessage.createdAtEpochMs,
            updatedAtEpochMs = sealedEmailMessage.updatedAtEpochMs,
            sortDateEpochMs = sealedEmailMessage.sortDateEpochMs,
            rfc822Header = sealedEmailMessage.rfc822Header.toSealedAttributeEntity(),
        )

    fun sealedEntityToUnsealedEntity(
        deviceKeyManager: DeviceKeyManager,
        sealedEmailMessage: SealedEmailMessageEntity,
    ): EmailMessageEntity {
        val unsealedRfc822Header =
            unsealEmailHeaderDetails(
                sealedEmailMessage.rfc822Header.base64EncodedSealedData,
                sealedEmailMessage.rfc822Header.keyId,
                sealedEmailMessage.rfc822Header.algorithm,
                deviceKeyManager,
            )

        return EmailMessageEntity(
            id = sealedEmailMessage.id,
            clientRefId = sealedEmailMessage.clientRefId,
            owner = sealedEmailMessage.owner,
            owners = sealedEmailMessage.owners,
            emailAddressId = sealedEmailMessage.emailAddressId,
            folderId = sealedEmailMessage.folderId,
            previousFolderId = sealedEmailMessage.previousFolderId,
            seen = sealedEmailMessage.seen,
            repliedTo = sealedEmailMessage.repliedTo,
            forwarded = sealedEmailMessage.forwarded,
            direction = sealedEmailMessage.direction,
            state = sealedEmailMessage.state,
            version = sealedEmailMessage.version,
            sortDate = sealedEmailMessage.sortDateEpochMs.toDate(),
            createdAt = sealedEmailMessage.createdAtEpochMs.toDate(),
            updatedAt = sealedEmailMessage.updatedAtEpochMs.toDate(),
            size = sealedEmailMessage.size,
            from = unsealedRfc822Header.from,
            to = unsealedRfc822Header.to,
            cc = unsealedRfc822Header.cc,
            bcc = unsealedRfc822Header.bcc,
            replyTo = unsealedRfc822Header.replyTo,
            subject = unsealedRfc822Header.subject,
            hasAttachments = unsealedRfc822Header.hasAttachments,
            encryptionStatus = sealedEmailMessage.encryptionStatus ?: EncryptionStatusEntity.UNENCRYPTED,
            date = unsealedRfc822Header.date,
            keyId = sealedEmailMessage.rfc822Header.keyId,
            algorithm = sealedEmailMessage.rfc822Header.algorithm,
        )
    }

    fun entityToApi(entity: EmailMessageEntity): EmailMessage =
        EmailMessage(
            id = entity.id,
            clientRefId = entity.clientRefId,
            owner = entity.owner,
            owners = entity.owners.map { OwnerTransformer.entityToApi(it) },
            emailAddressId = entity.emailAddressId,
            folderId = entity.folderId,
            previousFolderId = entity.previousFolderId,
            seen = entity.seen,
            repliedTo = entity.repliedTo,
            forwarded = entity.forwarded,
            direction = entity.direction.toDirection(),
            state = entity.state.toState(),
            version = entity.version,
            sortDate = entity.sortDate,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            size = entity.size,
            from = entity.from.map { it.toEmailAddress() },
            to = entity.to.map { it.toEmailAddress() },
            cc = entity.cc.map { it.toEmailAddress() },
            bcc = entity.bcc.map { it.toEmailAddress() },
            replyTo = entity.replyTo.map { it.toEmailAddress() },
            subject = entity.subject,
            hasAttachments = entity.hasAttachments,
            encryptionStatus = entity.encryptionStatus.toEncryptionStatus(),
            date = entity.date,
        )

    fun sealedEntityToPartialEntity(entity: SealedEmailMessageEntity): PartialEmailMessageEntity =
        PartialEmailMessageEntity(
            id = entity.id,
            clientRefId = entity.clientRefId,
            owner = entity.owner,
            owners = entity.owners,
            emailAddressId = entity.emailAddressId,
            folderId = entity.folderId,
            previousFolderId = entity.previousFolderId,
            seen = entity.seen,
            repliedTo = entity.repliedTo,
            forwarded = entity.forwarded,
            direction = entity.direction,
            state = entity.state,
            version = entity.version,
            size = entity.size,
            encryptionStatus = entity.encryptionStatus ?: EncryptionStatusEntity.UNENCRYPTED,
            sortDate = entity.sortDateEpochMs.toDate(),
            createdAt = entity.createdAtEpochMs.toDate(),
            updatedAt = entity.updatedAtEpochMs.toDate(),
        )

    fun entityToPartialApi(entity: PartialEmailMessageEntity): PartialEmailMessage =
        PartialEmailMessage(
            id = entity.id,
            clientRefId = entity.clientRefId,
            owner = entity.owner,
            owners = entity.owners.map { OwnerTransformer.entityToApi(it) },
            emailAddressId = entity.emailAddressId,
            folderId = entity.folderId,
            previousFolderId = entity.previousFolderId,
            seen = entity.seen,
            repliedTo = entity.repliedTo,
            forwarded = entity.forwarded,
            direction = entity.direction.toDirection(),
            state = entity.state.toState(),
            version = entity.version,
            size = entity.size,
            encryptionStatus = entity.encryptionStatus.toEncryptionStatus(),
            sortDate = entity.sortDate,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun graphQLToApi(
        deviceKeyManager: DeviceKeyManager,
        sealedEmailMessage: SealedEmailMessage,
    ): EmailMessage {
        val sealedEntity = graphQLToSealedEntity(sealedEmailMessage)
        val unsealedEntity = sealedEntityToUnsealedEntity(deviceKeyManager, sealedEntity)
        return entityToApi(unsealedEntity)
    }

    private fun unsealEmailHeaderDetails(
        sealedRFC822HeaderString: String,
        keyId: String,
        algorithm: String,
        deviceKeyManager: DeviceKeyManager,
    ): EmailHeaderDetails {
        val keyInfo =
            KeyInfo(
                keyId,
                KeyType.PRIVATE_KEY,
                algorithm,
            )
        val unsealer = Unsealer(deviceKeyManager, keyInfo)

        val unsealedRfc822HeaderString =
            unsealer.unseal(sealedRFC822HeaderString)

        return Gson().fromJson(unsealedRfc822HeaderString, EmailHeaderDetails::class.java)
    }

    private fun EmailMessageDirection.toEmailMessageDirectionEntity(): DirectionEntity =
        when (this) {
            EmailMessageDirection.INBOUND -> DirectionEntity.INBOUND
            EmailMessageDirection.OUTBOUND -> DirectionEntity.OUTBOUND
            EmailMessageDirection.UNKNOWN__ -> DirectionEntity.UNKNOWN
        }

    private fun EmailMessageState.toEmailMessageStateEntity(): StateEntity =
        when (this) {
            EmailMessageState.DELETED -> StateEntity.DELETED
            EmailMessageState.DELIVERED -> StateEntity.DELIVERED
            EmailMessageState.FAILED -> StateEntity.FAILED
            EmailMessageState.RECEIVED -> StateEntity.RECEIVED
            EmailMessageState.SENT -> StateEntity.SENT
            EmailMessageState.QUEUED -> StateEntity.QUEUED
            EmailMessageState.UNDELIVERED -> StateEntity.UNDELIVERED
            EmailMessageState.UNKNOWN__ -> StateEntity.UNKNOWN
        }

    private fun EmailMessageEncryptionStatus.toEncryptionStatusEntity(): EncryptionStatusEntity =
        when (this) {
            EmailMessageEncryptionStatus.ENCRYPTED -> EncryptionStatusEntity.ENCRYPTED
            EmailMessageEncryptionStatus.UNENCRYPTED -> EncryptionStatusEntity.UNENCRYPTED
            EmailMessageEncryptionStatus.UNKNOWN__ -> EncryptionStatusEntity.UNKNOWN
        }

    private fun DirectionEntity.toDirection(): Direction =
        when (this) {
            DirectionEntity.INBOUND -> Direction.INBOUND
            DirectionEntity.OUTBOUND -> Direction.OUTBOUND
            DirectionEntity.UNKNOWN -> Direction.UNKNOWN
        }

    private fun StateEntity.toState(): State =
        when (this) {
            StateEntity.DELETED -> State.DELETED
            StateEntity.DELIVERED -> State.DELIVERED
            StateEntity.FAILED -> State.FAILED
            StateEntity.RECEIVED -> State.RECEIVED
            StateEntity.SENT -> State.SENT
            StateEntity.QUEUED -> State.QUEUED
            StateEntity.UNDELIVERED -> State.UNDELIVERED
            StateEntity.UNKNOWN -> State.UNKNOWN
        }

    private fun EmailMessageAddressEntity.toEmailAddress(): EmailMessage.EmailAddress =
        EmailMessage.EmailAddress(
            emailAddress = this.emailAddress,
            displayName = this.displayName,
        )

    private fun EncryptionStatusEntity.toEncryptionStatus(): EncryptionStatus =
        when (this) {
            EncryptionStatusEntity.ENCRYPTED -> EncryptionStatus.ENCRYPTED
            EncryptionStatusEntity.UNENCRYPTED -> EncryptionStatus.UNENCRYPTED
            EncryptionStatusEntity.UNKNOWN -> EncryptionStatus.UNKNOWN
        }

    private fun SealedEmailMessage.Rfc822Header.toSealedAttributeEntity(): SealedAttributeEntity =
        SealedAttributeEntity(
            base64EncodedSealedData = this.base64EncodedSealedData,
            keyId = this.keyId,
            algorithm = this.algorithm,
            plainTextType = this.plainTextType,
        )
}
