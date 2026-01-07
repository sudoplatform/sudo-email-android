/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage

import com.amazonaws.regions.Regions
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressPublicInfoEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailAttachmentEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EncryptionStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.InternetMessageFormatHeaderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SendEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SendEmailMessageResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SendEncryptedEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SimplifiedEmailMessageEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.LookupEmailAddressesPublicInfoUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.LookupEmailAddressesPublicInfoUseCaseInput
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudologging.Logger
import java.util.UUID

/**
 * Input for the send email message use case.
 *
 * @property senderEmailAddressId [String] The email address ID from which to send the message.
 * @property emailMessageHeader [InternetMessageFormatHeaderEntity] The email message header.
 * @property body [String] The body content of the email message.
 * @property attachments [List] of [EmailAttachmentEntity] file attachments.
 * @property inlineAttachments [List] of [EmailAttachmentEntity] inline attachments.
 * @property replyingMessageId [String] Optional ID of the message being replied to.
 * @property forwardingMessageId [String] Optional ID of the message being forwarded.
 */
internal data class SendEmailMessageUseCaseInput(
    val senderEmailAddressId: String,
    val emailMessageHeader: InternetMessageFormatHeaderEntity,
    val body: String,
    val attachments: List<EmailAttachmentEntity> = emptyList(),
    val inlineAttachments: List<EmailAttachmentEntity> = emptyList(),
    var replyingMessageId: String? = null,
    val forwardingMessageId: String? = null,
)

/**
 * Use case for sending an email message.
 *
 * This use case handles sending an email message, including both out-of-network and
 * encrypted in-network messages.
 *
 * @property emailMessageService [EmailMessageService] Service for email message operations.
 * @property configurationDataService [ConfigurationDataService] Service for configuration data.
 * @property emailAddressService [EmailAddressService] Service for email address operations.
 * @property emailMessageDataProcessor [EmailMessageDataProcessor] Processor for email message data.
 * @property s3TransientClient [S3Client] Client for S3 transient bucket operations.
 * @property region [String] The AWS region for S3 operations.
 * @property transientBucket [String] The name of the S3 transient bucket.
 * @property logger [Logger] Logger for debugging.
 * @property lookupEmailAddressesPublicInfoUseCase [LookupEmailAddressesPublicInfoUseCase] Use case for looking up public info of email addresses.
 */
internal class SendEmailMessageUseCase(
    private val emailMessageService: EmailMessageService,
    private val configurationDataService: ConfigurationDataService,
    private val emailAddressService: EmailAddressService,
    private val emailMessageDataProcessor: EmailMessageDataProcessor,
    private val s3TransientClient: S3Client,
    private val region: String = Regions.US_EAST_1.name,
    private val transientBucket: String,
    private val logger: Logger,
    private val lookupEmailAddressesPublicInfoUseCase: LookupEmailAddressesPublicInfoUseCase =
        LookupEmailAddressesPublicInfoUseCase(
            emailAddressService = emailAddressService,
            logger = logger,
        ),
) {
    /**
     * Executes the send email message use case.
     *
     * @param input [SendEmailMessageUseCaseInput] The input parameters.
     * @return [SendEmailMessageResultEntity] The result of the send operation.
     * @throws SudoEmailClient.EmailAddressException if email address validation fails.
     * @throws SudoEmailClient.EmailMessageException if message validation or sending fails.
     */
    suspend fun execute(input: SendEmailMessageUseCaseInput): SendEmailMessageResultEntity {
        logger.debug("SendEmailMessageUseCase execute input: $input")
        val (senderEmailAddressId, emailMessageHeader, body, attachments, inlineAttachments, replyingMessageId, forwardingMessageId) = input
        val configurationData = configurationDataService.getConfigurationData()
        val (
            _, _, _,
            emailMessageMaxOutboundMessageSize,
            emailMessageRecipientsLimit,
            encryptedEmailMessageRecipientsLimit,
        ) = configurationData

        try {
            configurationData.verifyAttachmentValidity(
                attachments,
                inlineAttachments,
            )
            val domains = configurationDataService.getConfiguredEmailDomains()

            val allRecipients =
                mutableListOf<EmailMessageAddressEntity>()
                    .apply {
                        addAll(emailMessageHeader.to)
                        addAll(emailMessageHeader.cc)
                        addAll(emailMessageHeader.bcc)
                    }.map { it.emailAddress }

            // Check if all recipient domains are ours
            val allRecipientsInternal =
                allRecipients.isNotEmpty() &&
                    allRecipients.all { recipient ->
                        domains.any { domain ->
                            recipient.lowercase().contains(domain)
                        }
                    }

            if (allRecipientsInternal) {
                if (allRecipients.size > encryptedEmailMessageRecipientsLimit) {
                    throw SudoEmailClient.EmailMessageException.LimitExceededException(
                        "${StringConstants.RECIPIENT_LIMIT_EXCEEDED_ERROR_MSG}$encryptedEmailMessageRecipientsLimit",
                    )
                }
                val allRecipientsAndSender = allRecipients.toMutableList()
                allRecipientsAndSender.add(emailMessageHeader.from.emailAddress)

                val emailAddressesPublicInfo =
                    lookupEmailAddressesPublicInfoUseCase.execute(
                        LookupEmailAddressesPublicInfoUseCaseInput(
                            addresses = allRecipientsAndSender,
                            throwIfNotAllInternal = true,
                        ),
                    )

                return sendInNetworkEmailMessage(
                    senderEmailAddressId,
                    emailMessageHeader,
                    body,
                    attachments,
                    inlineAttachments,
                    emailAddressesPublicInfo,
                    replyingMessageId,
                    forwardingMessageId,
                    emailMessageMaxOutboundMessageSize,
                )
            }
            if (allRecipients.size > emailMessageRecipientsLimit) {
                throw SudoEmailClient.EmailMessageException.LimitExceededException(
                    "${StringConstants.RECIPIENT_LIMIT_EXCEEDED_ERROR_MSG}$emailMessageRecipientsLimit",
                )
            }

            return sendOutOfNetworkEmailMessage(
                senderEmailAddressId = senderEmailAddressId,
                emailMessageHeader = emailMessageHeader,
                body = body,
                attachments = attachments,
                inlineAttachments = inlineAttachments,
                replyingMessageId = replyingMessageId,
                forwardingMessageId = forwardingMessageId,
                emailMessageMaxOutboundMessageSize = emailMessageMaxOutboundMessageSize,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretEmailMessageException(e)
        }
    }

    private suspend fun sendInNetworkEmailMessage(
        senderEmailAddressId: String,
        emailMessageHeader: InternetMessageFormatHeaderEntity,
        body: String,
        attachments: List<EmailAttachmentEntity>,
        inlineAttachments: List<EmailAttachmentEntity>,
        emailAddressesPublicInfo: List<EmailAddressPublicInfoEntity>,
        replyingMessageId: String? = null,
        forwardingMessageId: String? = null,
        emailMessageMaxOutboundMessageSize: Int,
    ): SendEmailMessageResultEntity {
        var s3ObjectKey = ""

        try {
            val messageData =
                SimplifiedEmailMessageEntity(
                    from = listOf(emailMessageHeader.from.toString()),
                    to = emailMessageHeader.to.map { it.toString() },
                    cc = emailMessageHeader.cc.map { it.toString() },
                    bcc = emailMessageHeader.bcc.map { it.toString() },
                    subject = emailMessageHeader.subject,
                    body = body,
                    isHtml = true,
                    inlineAttachments = inlineAttachments,
                    attachments = attachments,
                    replyingMessageId = replyingMessageId,
                    forwardingMessageId = forwardingMessageId,
                )
            s3ObjectKey =
                processAndUploadEmailMessage(
                    senderEmailAddressId,
                    messageData,
                    EncryptionStatusEntity.ENCRYPTED,
                    emailAddressesPublicInfo,
                    emailMessageMaxOutboundMessageSize,
                )

            val sendEncryptedEmailMessageRequest =
                SendEncryptedEmailMessageRequest(
                    emailAddressId = senderEmailAddressId,
                    s3ObjectKey = s3ObjectKey,
                    region = region,
                    transientBucket = transientBucket,
                    emailMessageHeader = emailMessageHeader,
                    attachments = attachments,
                    inlineAttachments = inlineAttachments,
                    replyingMessageId = replyingMessageId,
                    forwardingMessageId = forwardingMessageId,
                )
            return emailMessageService.sendEncrypted(sendEncryptedEmailMessageRequest)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretEmailMessageException(e)
        }
    }

    private suspend fun sendOutOfNetworkEmailMessage(
        senderEmailAddressId: String,
        emailMessageHeader: InternetMessageFormatHeaderEntity,
        body: String,
        attachments: List<EmailAttachmentEntity>,
        inlineAttachments: List<EmailAttachmentEntity>,
        replyingMessageId: String? = null,
        forwardingMessageId: String? = null,
        emailMessageMaxOutboundMessageSize: Int,
    ): SendEmailMessageResultEntity {
        var s3ObjectKey = ""

        try {
            val messageData =
                SimplifiedEmailMessageEntity(
                    from = listOf(emailMessageHeader.from.toString()),
                    to = emailMessageHeader.to.map { it.toString() },
                    cc = emailMessageHeader.cc.map { it.toString() },
                    bcc = emailMessageHeader.bcc.map { it.toString() },
                    subject = emailMessageHeader.subject,
                    body = body,
                    isHtml = true,
                    inlineAttachments = inlineAttachments,
                    attachments = attachments,
                    replyingMessageId = replyingMessageId,
                    forwardingMessageId = forwardingMessageId,
                )
            s3ObjectKey =
                processAndUploadEmailMessage(
                    senderEmailAddressId,
                    messageData,
                    EncryptionStatusEntity.UNENCRYPTED,
                    emailMessageMaxOutboundMessageSize = emailMessageMaxOutboundMessageSize,
                )

            val sendEmailMessageRequest =
                SendEmailMessageRequest(
                    emailAddressId = senderEmailAddressId,
                    s3ObjectKey = s3ObjectKey,
                    region = region,
                    transientBucket = transientBucket,
                )
            return emailMessageService.send(sendEmailMessageRequest)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretEmailMessageException(e)
        }
    }

    private suspend fun processAndUploadEmailMessage(
        senderEmailAddressId: String,
        messageData: SimplifiedEmailMessageEntity,
        encryptionStatus: EncryptionStatusEntity,
        emailAddressesPublicInfo: List<EmailAddressPublicInfoEntity> = emptyList(),
        emailMessageMaxOutboundMessageSize: Int,
    ): String {
        var rfc822Data: ByteArray
        val clientRefId = UUID.randomUUID().toString()
        val objectId = "${DefaultS3Client.constructS3PrefixForEmailAddress(senderEmailAddressId)}/$clientRefId"

        try {
            rfc822Data =
                emailMessageDataProcessor.processMessageData(
                    messageData,
                    encryptionStatus,
                    emailAddressesPublicInfo,
                )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretEmailMessageException(e)
        }

        if (rfc822Data.size > emailMessageMaxOutboundMessageSize) {
            logger.error(
                "Email message size exceeded. Limit: $emailMessageMaxOutboundMessageSize bytes. " +
                    "Message size: ${rfc822Data.size}",
            )
            throw SudoEmailClient.EmailMessageException.EmailMessageSizeLimitExceededException(
                "Email message size exceeded. Limit: $emailMessageMaxOutboundMessageSize bytes",
            )
        }
        return s3TransientClient.upload(rfc822Data, objectId)
    }
}
