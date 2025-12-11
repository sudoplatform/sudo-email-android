/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.data

import com.sudoplatform.sudoemail.internal.domain.entities.common.OwnerEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageMetadataEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageWithContentEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduledDraftMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduledDraftMessageStateEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressPublicInfoEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressPublicKeyEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.PartialEmailAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.PublicKeyFormatEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.SealedEmailAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.UnsealedEmailAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.PartialEmailFolderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.SealedEmailFolderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.UnsealedEmailFolderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.DirectionEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailAttachmentEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageWithBodyEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.PartialEmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SealedEmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SimplifiedEmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.StateEntity
import java.time.Duration
import java.util.Date

internal object EntityDataFactory {
    fun getConfigurationDataEntity(
        deleteEmailMessagesLimit: Int = 10,
        updateEmailMessagesLimit: Int = 5,
        emailMessageMaxInboundMessageSize: Int = 200,
        emailMessageMaxOutboundMessageSize: Int = 100,
        emailMessageRecipientsLimit: Int = 5,
        encryptedEmailMessageRecipientsLimit: Int = 10,
        prohibitedFileExtensions: List<String> = listOf(".js", ".exe", ".lib"),
    ) = ConfigurationDataEntity(
        deleteEmailMessagesLimit = deleteEmailMessagesLimit,
        updateEmailMessagesLimit = updateEmailMessagesLimit,
        emailMessageMaxInboundMessageSize = emailMessageMaxInboundMessageSize,
        emailMessageMaxOutboundMessageSize = emailMessageMaxOutboundMessageSize,
        emailMessageRecipientsLimit = emailMessageRecipientsLimit,
        encryptedEmailMessageRecipientsLimit = encryptedEmailMessageRecipientsLimit,
        prohibitedFileExtensions = prohibitedFileExtensions,
    )

    fun getOwnerEntity(
        id: String = "mockOwner",
        issuer: String = "issuer",
    ) = OwnerEntity(
        id = id,
        issuer = issuer,
    )

    fun getSealedEmailFolderEntity(
        id: String = "mockFolderId",
        owner: String = "mockOwner",
        owners: List<OwnerEntity> =
            listOf(
                getOwnerEntity(),
            ),
        emailAddressId: String = "mockEmailAddressId",
        folderName: String = "INBOX",
        size: Double = 0.0,
        unseenCount: Int = 0,
        version: Int = 1,
        createdAt: Date = Date(1),
        updatedAt: Date = Date(1),
        sealedCustomFolderName: SealedAttributeEntity? = null,
    ) = SealedEmailFolderEntity(
        id = id,
        owner = owner,
        owners = owners,
        emailAddressId = emailAddressId,
        folderName = folderName,
        size = size,
        unseenCount = unseenCount,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sealedCustomFolderName = sealedCustomFolderName,
    )

    fun getUnsealedEmailFolderEntity(
        id: String = "mockFolderId",
        owner: String = "mockOwner",
        owners: List<OwnerEntity> =
            listOf(
                getOwnerEntity(),
            ),
        emailAddressId: String = "mockEmailAddressId",
        folderName: String = "folderName",
        size: Double = 0.0,
        unseenCount: Int = 0,
        version: Int = 1,
        createdAt: Date = Date(1),
        updatedAt: Date = Date(1),
        customFolderName: String? = null,
    ) = UnsealedEmailFolderEntity(
        id = id,
        owner = owner,
        owners = owners,
        emailAddressId = emailAddressId,
        folderName = folderName,
        size = size,
        unseenCount = unseenCount,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
        customFolderName = customFolderName,
    )

    fun getPartialEmailFolderEntity(
        id: String = "mockFolderId",
        owner: String = "mockOwner",
        owners: List<OwnerEntity> =
            listOf(
                getOwnerEntity(),
            ),
        emailAddressId: String = "mockEmailAddressId",
        folderName: String = "folderName",
        size: Double = 0.0,
        unseenCount: Int = 0,
        version: Int = 1,
        createdAt: Date = Date(1),
        updatedAt: Date = Date(1),
    ) = PartialEmailFolderEntity(
        id = id,
        owner = owner,
        owners = owners,
        emailAddressId = emailAddressId,
        folderName = folderName,
        size = size,
        unseenCount = unseenCount,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun getSealedEmailAddressEntity(
        id: String = "mockEmailAddressId",
        owner: String = "mockOwner",
        owners: List<OwnerEntity> =
            listOf(
                getOwnerEntity(),
            ),
        version: Int = 1,
        createdAt: Date = Date(1),
        updatedAt: Date = Date(1),
        lastReceivedAt: Date = Date(1),
        emailAddress: String = "example@sudoplatform.com",
        size: Double = 0.0,
        numberOfEmailMessages: Int = 0,
        alias: SealedAttributeEntity? = null,
        folders: List<SealedEmailFolderEntity> = listOf(getSealedEmailFolderEntity()),
    ) = SealedEmailAddressEntity(
        id = id,
        owner = owner,
        owners = owners,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastReceivedAt = lastReceivedAt,
        emailAddress = emailAddress,
        size = size,
        numberOfEmailMessages = numberOfEmailMessages,
        sealedAlias = alias,
        folders = folders,
    )

    fun getPartialEmailAddressEntity(
        id: String = "mockEmailAddressId",
        owner: String = "mockOwner",
        owners: List<OwnerEntity> =
            listOf(
                getOwnerEntity(),
            ),
        version: Int = 1,
        createdAt: Date = Date(1),
        updatedAt: Date = Date(1),
        lastReceivedAt: Date = Date(1),
        emailAddress: String = "example@sudoplatform.com",
        size: Double = 0.0,
        numberOfEmailMessages: Int = 0,
        folders: List<PartialEmailFolderEntity> = listOf(getPartialEmailFolderEntity()),
    ) = PartialEmailAddressEntity(
        id = id,
        owner = owner,
        owners = owners,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastReceivedAt = lastReceivedAt,
        emailAddress = emailAddress,
        size = size,
        numberOfEmailMessages = numberOfEmailMessages,
        folders = folders,
    )

    fun getUnsealedEmailAddressEntity(
        id: String = "mockEmailAddressId",
        owner: String = "mockOwner",
        owners: List<OwnerEntity> =
            listOf(
                getOwnerEntity(),
            ),
        version: Int = 1,
        createdAt: Date = Date(1),
        updatedAt: Date = Date(1),
        lastReceivedAt: Date = Date(1),
        emailAddress: String = "example@sudoplatform.com",
        size: Double = 0.0,
        numberOfEmailMessages: Int = 0,
        alias: String? = null,
        folders: List<UnsealedEmailFolderEntity> = listOf(getUnsealedEmailFolderEntity()),
    ) = UnsealedEmailAddressEntity(
        id = id,
        owner = owner,
        owners = owners,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastReceivedAt = lastReceivedAt,
        emailAddress = emailAddress,
        size = size,
        numberOfEmailMessages = numberOfEmailMessages,
        alias = alias,
        folders = folders,
    )

    fun getEmailAddressPublicInfoEntity(
        emailAddress: String = "example@sudoplatform.com",
        keyId: String = "keyId",
        publicKeyDetails: EmailAddressPublicKeyEntity =
            EmailAddressPublicKeyEntity(
                keyFormat = PublicKeyFormatEntity.RSA_PUBLIC_KEY,
                publicKey = "publicKey",
                algorithm = "algorithm",
            ),
    ) = EmailAddressPublicInfoEntity(
        emailAddress = emailAddress,
        keyId = keyId,
        publicKeyDetails = publicKeyDetails,
    )

    fun getSealedEmailMessageEntity(
        id: String = "mockEmailMessageId",
        clientRefId: String = "clientRefId",
        owner: String = "mockOwner",
        owners: List<OwnerEntity> =
            listOf(
                getOwnerEntity(),
            ),
        emailAddressId: String = "mockEmailAddressId",
        folderId: String = "folderId",
        previousFolderId: String? = null,
        seen: Boolean = false,
        repliedTo: Boolean = false,
        forwarded: Boolean = false,
        direction: DirectionEntity = DirectionEntity.INBOUND,
        state: StateEntity = StateEntity.RECEIVED,
        version: Int = 1,
        sortDateEpochMs: Double = 1.0,
        createdAtEpochMs: Double = 1.0,
        updatedAtEpochMs: Double = 1.0,
        size: Double = 0.0,
        rfc822Header: SealedAttributeEntity =
            SealedAttributeEntity(
                algorithm = "algorithm",
                keyId = "keyId",
                plainTextType = "string",
                base64EncodedSealedData = "mockSealedData",
            ),
        encryptionStatus: EncryptionStatusEntity = EncryptionStatusEntity.UNENCRYPTED,
    ) = SealedEmailMessageEntity(
        id = id,
        clientRefId = clientRefId,
        owner = owner,
        owners = owners,
        emailAddressId = emailAddressId,
        folderId = folderId,
        previousFolderId = previousFolderId,
        seen = seen,
        repliedTo = repliedTo,
        forwarded = forwarded,
        direction = direction,
        state = state,
        version = version,
        sortDateEpochMs = sortDateEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        size = size,
        rfc822Header = rfc822Header,
        encryptionStatus = encryptionStatus,
    )

    fun getEmailMessageEntity(
        id: String = "mockEmailMessageId",
        clientRefId: String = "clientRefId",
        owner: String = "mockOwner",
        owners: List<OwnerEntity> =
            listOf(
                getOwnerEntity(),
            ),
        emailAddressId: String = "mockEmailAddressId",
        folderId: String = "folderId",
        previousFolderId: String? = null,
        seen: Boolean = false,
        repliedTo: Boolean = false,
        forwarded: Boolean = false,
        direction: DirectionEntity = DirectionEntity.INBOUND,
        state: StateEntity = StateEntity.RECEIVED,
        version: Int = 1,
        sortDate: Date = Date(1),
        createdAt: Date = Date(1),
        updatedAt: Date = Date(1),
        size: Double = 0.0,
        from: List<EmailMessageAddressEntity> =
            listOf(
                EmailMessageAddressEntity(
                    emailAddress = "from@example.com",
                ),
            ),
        to: List<EmailMessageAddressEntity> =
            listOf(
                EmailMessageAddressEntity(
                    emailAddress = "to@example.com",
                ),
            ),
        cc: List<EmailMessageAddressEntity> = emptyList(),
        bcc: List<EmailMessageAddressEntity> = emptyList(),
        replyTo: List<EmailMessageAddressEntity> = emptyList(),
        subject: String? = "Subject",
        hasAttachments: Boolean = false,
        encryptionStatus: EncryptionStatusEntity = EncryptionStatusEntity.UNENCRYPTED,
        date: Date? = Date(1L),
        keyId: String = "keyId",
        algorithm: String = "algorithm",
    ) = EmailMessageEntity(
        id = id,
        clientRefId = clientRefId,
        owner = owner,
        owners = owners,
        emailAddressId = emailAddressId,
        folderId = folderId,
        previousFolderId = previousFolderId,
        seen = seen,
        repliedTo = repliedTo,
        forwarded = forwarded,
        direction = direction,
        state = state,
        version = version,
        sortDate = sortDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        size = size,
        from = from,
        to = to,
        cc = cc,
        bcc = bcc,
        replyTo = replyTo,
        subject = subject,
        hasAttachments = hasAttachments,
        encryptionStatus = encryptionStatus,
        date = date,
        keyId = keyId,
        algorithm = algorithm,
    )

    fun getPartialEmailMessageEntity(
        id: String = "mockEmailMessageId",
        clientRefId: String = "clientRefId",
        owner: String = "mockOwner",
        owners: List<OwnerEntity> =
            listOf(
                getOwnerEntity(),
            ),
        emailAddressId: String = "mockEmailAddressId",
        folderId: String = "folderId",
        previousFolderId: String? = null,
        seen: Boolean = false,
        repliedTo: Boolean = false,
        forwarded: Boolean = false,
        direction: DirectionEntity = DirectionEntity.INBOUND,
        state: StateEntity = StateEntity.RECEIVED,
        version: Int = 1,
        sortDate: Date = Date(1),
        createdAt: Date = Date(1),
        updatedAt: Date = Date(1),
        size: Double = 0.0,
        encryptionStatus: EncryptionStatusEntity = EncryptionStatusEntity.UNENCRYPTED,
        date: Date? = Date(1L),
    ) = PartialEmailMessageEntity(
        id = id,
        clientRefId = clientRefId,
        owner = owner,
        owners = owners,
        emailAddressId = emailAddressId,
        folderId = folderId,
        previousFolderId = previousFolderId,
        seen = seen,
        repliedTo = repliedTo,
        forwarded = forwarded,
        direction = direction,
        state = state,
        version = version,
        sortDate = sortDate,
        createdAt = createdAt,
        updatedAt = updatedAt,
        size = size,
        encryptionStatus = encryptionStatus,
        date = date,
    )

    fun getEmailMessageWithBodyEntity(
        id: String = "mockEmailMessageId",
        body: String = "This is the body of the email message.",
        isHtml: Boolean = false,
        attachments: List<EmailAttachmentEntity> = emptyList(),
        inlineAttachments: List<EmailAttachmentEntity> = emptyList(),
    ) = EmailMessageWithBodyEntity(
        id = id,
        body = body,
        isHtml = isHtml,
        attachments = attachments,
        inlineAttachments = inlineAttachments,
    )

    fun getSimplifiedEmailMessageEntity(
        from: List<String> =
            listOf(
                "from@example.com",
            ),
        to: List<String> =
            listOf(
                "to@example.com",
            ),
        cc: List<String> = emptyList(),
        bcc: List<String> = emptyList(),
        subject: String? = "Subject",
        body: String? = "This is the body of the email message.",
        isHtml: Boolean = false,
        attachments: List<EmailAttachmentEntity> = emptyList(),
        inlineAttachments: List<EmailAttachmentEntity> = emptyList(),
        replyingMessageId: String? = null,
        forwardingMessageId: String? = null,
    ) = SimplifiedEmailMessageEntity(
        from = from,
        to = to,
        cc = cc,
        bcc = bcc,
        subject = subject,
        body = body,
        isHtml = isHtml,
        attachments = attachments,
        inlineAttachments = inlineAttachments,
        replyingMessageId = replyingMessageId,
        forwardingMessageId = forwardingMessageId,
    )

    fun getDraftEmailMessageWithContentEntity(
        id: String = "draftId",
        emailAddressId: String = "mockEmailAddressId",
        updatedAt: Date = Date(1L),
        rfc822Data: ByteArray = "RFC822 data".toByteArray(),
    ) = DraftEmailMessageWithContentEntity(
        id = id,
        emailAddressId = emailAddressId,
        updatedAt = updatedAt,
        rfc822Data = rfc822Data,
    )

    fun getDraftEmailMessageMetadataEntity(
        id: String = "draftId",
        emailAddressId: String = "mockEmailAddressId",
        updatedAt: Date = Date(1L),
    ) = DraftEmailMessageMetadataEntity(
        id = id,
        emailAddressId = emailAddressId,
        updatedAt = updatedAt,
    )

    fun getScheduledDraftMessageEntity(
        id: String = "scheduledDraftId",
        emailAddressId: String = "mockEmailAddressId",
        owner: String = "mockOwner",
        owners: List<OwnerEntity> =
            listOf(
                getOwnerEntity(),
            ),
        sendAt: Date = Date(Date().time + Duration.ofDays(1).toMillis()),
        state: ScheduledDraftMessageStateEntity = ScheduledDraftMessageStateEntity.SCHEDULED,
        createdAt: Date = Date(1),
        updatedAt: Date = Date(1),
    ) = ScheduledDraftMessageEntity(
        id = id,
        emailAddressId = emailAddressId,
        sendAt = sendAt,
        state = state,
        owner = owner,
        owners = owners,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}
