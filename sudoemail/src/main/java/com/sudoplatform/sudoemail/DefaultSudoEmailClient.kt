/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amazonaws.regions.Regions
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.internal.data.blockedAddress.GraphQLBlockedAddressService
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.transformers.BatchOperationResultTransformer
import com.sudoplatform.sudoemail.internal.data.common.transformers.DateRangeTransformer
import com.sudoplatform.sudoemail.internal.data.common.transformers.ErrorTransformer
import com.sudoplatform.sudoemail.internal.data.common.transformers.ListApiResultTransformer
import com.sudoplatform.sudoemail.internal.data.common.transformers.SortOrderTransformer
import com.sudoplatform.sudoemail.internal.data.configuration.GraphQLConfigurationDataService
import com.sudoplatform.sudoemail.internal.data.configuration.transformers.ConfigurationDataTransformer
import com.sudoplatform.sudoemail.internal.data.draftMessage.GraphQLS3DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.data.draftMessage.transformers.DraftEmailMessageTransformer
import com.sudoplatform.sudoemail.internal.data.draftMessage.transformers.ScheduledDraftMessageTransformer
import com.sudoplatform.sudoemail.internal.data.emailAddress.GraphQLEmailAddressService
import com.sudoplatform.sudoemail.internal.data.emailAddress.transformers.EmailAddressPublicInfoTransformer
import com.sudoplatform.sudoemail.internal.data.emailAddress.transformers.EmailAddressTransformer
import com.sudoplatform.sudoemail.internal.data.emailFolder.GraphQLEmailFolderService
import com.sudoplatform.sudoemail.internal.data.emailFolder.transformers.EmailFolderTransformer
import com.sudoplatform.sudoemail.internal.data.emailMask.GraphQLEmailMaskService
import com.sudoplatform.sudoemail.internal.data.emailMask.transformers.EmailMaskTransformer
import com.sudoplatform.sudoemail.internal.data.emailMessage.GraphQLEmailMessageService
import com.sudoplatform.sudoemail.internal.data.emailMessage.transformers.EmailAttachmentTransformer
import com.sudoplatform.sudoemail.internal.data.emailMessage.transformers.EmailMessageTransformer
import com.sudoplatform.sudoemail.internal.data.emailMessage.transformers.InternetMessageFormatHeaderTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.CheckEmailAddressAvailabilityRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.DeprovisionEmailAddressRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.EmailFolderService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMask.EmailMaskService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.DeleteMessageForFolderIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.useCases.DefaultUseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.blockedAddress.BlockEmailAddressesUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.blockedAddress.UnblockEmailAddressesByHashedValueUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.blockedAddress.UnblockEmailAddressesUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.CancelScheduledDraftMessageUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.CreateDraftEmailMessageUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.DeleteDraftEmailMessagesUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.GetDraftEmailMessageUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.ListScheduledDraftMessagesForEmailAddressIdUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.ScheduleSendDraftMessageUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.UpdateDraftEmailMessageUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.GetEmailAddressUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.ListEmailAddressesForSudoIdUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.ListEmailAddressesUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.LookupEmailAddressesPublicInfoUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.ProvisionEmailAddressUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.UpdateEmailAddressMetadataUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder.CreateCustomEmailFolderUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder.DeleteCustomEmailFolderUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder.ListEmailFoldersForEmailAddressIdUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder.UpdateCustomEmailFolderUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMask.DeprovisionEmailMaskUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMask.DisableEmailMaskUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMask.EnableEmailMaskUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMask.ListEmailMasksUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMask.ProvisionEmailMaskUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMask.UpdateEmailMaskUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.GetEmailMessageRfc822DataUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.GetEmailMessageUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.GetEmailMessageWithBodyUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.ListEmailMessagesUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.SendEmailMessageUseCaseInput
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.UpdateEmailMessagesUseCaseInput
import com.sudoplatform.sudoemail.internal.util.DefaultEmailMessageDataProcessor
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultEmailCryptoService
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.subscription.EmailMessageSubscriber
import com.sudoplatform.sudoemail.subscription.SubscriptionService
import com.sudoplatform.sudoemail.types.BatchOperationResult
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.BlockedEmailAddressAction
import com.sudoplatform.sudoemail.types.BlockedEmailAddressLevel
import com.sudoplatform.sudoemail.types.ConfigurationData
import com.sudoplatform.sudoemail.types.DeleteEmailMessageSuccessResult
import com.sudoplatform.sudoemail.types.DraftEmailMessageMetadata
import com.sudoplatform.sudoemail.types.DraftEmailMessageWithContent
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailAddressPublicInfo
import com.sudoplatform.sudoemail.types.EmailFolder
import com.sudoplatform.sudoemail.types.EmailMask
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.EmailMessageOperationFailureResult
import com.sudoplatform.sudoemail.types.EmailMessageRfc822Data
import com.sudoplatform.sudoemail.types.EmailMessageWithBody
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.ListOutput
import com.sudoplatform.sudoemail.types.PartialEmailAddress
import com.sudoplatform.sudoemail.types.PartialEmailMask
import com.sudoplatform.sudoemail.types.PartialEmailMessage
import com.sudoplatform.sudoemail.types.ScheduledDraftMessage
import com.sudoplatform.sudoemail.types.SendEmailMessageResult
import com.sudoplatform.sudoemail.types.UnsealedBlockedAddress
import com.sudoplatform.sudoemail.types.UpdatedEmailMessageResult.UpdatedEmailMessageSuccess
import com.sudoplatform.sudoemail.types.inputs.CancelScheduledDraftMessageInput
import com.sudoplatform.sudoemail.types.inputs.CheckEmailAddressAvailabilityInput
import com.sudoplatform.sudoemail.types.inputs.CreateCustomEmailFolderInput
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.DeleteCustomEmailFolderInput
import com.sudoplatform.sudoemail.types.inputs.DeleteDraftEmailMessagesInput
import com.sudoplatform.sudoemail.types.inputs.DeleteMessagesForFolderIdInput
import com.sudoplatform.sudoemail.types.inputs.DeprovisionEmailMaskInput
import com.sudoplatform.sudoemail.types.inputs.DisableEmailMaskInput
import com.sudoplatform.sudoemail.types.inputs.EnableEmailMaskInput
import com.sudoplatform.sudoemail.types.inputs.GetDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailAddressInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageRfc822DataInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageWithBodyInput
import com.sudoplatform.sudoemail.types.inputs.ListDraftEmailMessageMetadataForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ListDraftEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesForSudoIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailFoldersForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMasksForOwnerInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesInput
import com.sudoplatform.sudoemail.types.inputs.ListScheduledDraftMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.LookupEmailAddressesPublicInfoInput
import com.sudoplatform.sudoemail.types.inputs.ProvisionEmailAddressInput
import com.sudoplatform.sudoemail.types.inputs.ProvisionEmailMaskInput
import com.sudoplatform.sudoemail.types.inputs.ScheduleSendDraftMessageInput
import com.sudoplatform.sudoemail.types.inputs.SendEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.UpdateCustomEmailFolderInput
import com.sudoplatform.sudoemail.types.inputs.UpdateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailAddressMetadataInput
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailMaskInput
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailMessagesInput
import com.sudoplatform.sudoemail.util.EmailClientInvoker
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudonotification.types.NotificationMetaData
import com.sudoplatform.sudonotification.types.NotificationSchemaEntry
import com.sudoplatform.sudouser.SudoUserClient

/**
 * Default implementation of the [SudoEmailClient] interface.
 *
 * @property context [Context] Application context.
 * @property apiClient [ApiClient] ApiClient used to make requests to AWS and call sudo email service API.
 * @property sudoUserClient [SudoUserClient] Used to determine if a user is signed in and gain access to the user owner ID.
 * @property logger [Logger] Errors and warnings will be logged here.
 * @property serviceKeyManager [ServiceKeyManager] On device management of key storage.
 * @property sealingService [SealingService] Service that handles sealing of emails
 * @property region [String] The AWS region.
 * @property emailBucket [String] The S3 Bucket for email messages sent from this identity.
 * @property transientBucket [String] The S3 Bucket for temporary draft email messages.
 */
internal class DefaultSudoEmailClient(
    private val context: Context,
    private val apiClient: ApiClient,
    private val sudoUserClient: SudoUserClient,
    private val logger: Logger =
        Logger(
            LogConstants.SUDOLOG_TAG,
            AndroidUtilsLogDriver(LogLevel.INFO),
        ),
    private val serviceKeyManager: ServiceKeyManager,
    private var sealingService: SealingService =
        DefaultSealingService(
            serviceKeyManager,
            logger,
        ),
    private val emailCryptoService: EmailCryptoService =
        DefaultEmailCryptoService(
            serviceKeyManager,
            logger,
        ),
    private val emailMaskService: EmailMaskService =
        GraphQLEmailMaskService(
            apiClient,
            logger,
        ),
    private val region: String = Regions.US_EAST_1.name,
    private val emailBucket: String,
    private val transientBucket: String,
    private val notificationHandler: SudoEmailNotificationHandler? = null,
    private val s3TransientClient: S3Client =
        DefaultS3Client(
            context,
            sudoUserClient,
            region,
            transientBucket,
            logger,
        ),
    private val s3EmailClient: S3Client =
        DefaultS3Client(
            context,
            sudoUserClient,
            region,
            emailBucket,
            logger,
        ),
    private val emailAddressService: EmailAddressService =
        GraphQLEmailAddressService(
            apiClient,
            logger,
        ),
    private val configurationDataService: ConfigurationDataService =
        GraphQLConfigurationDataService(
            apiClient,
            logger,
        ),
    private val emailFolderService: EmailFolderService =
        GraphQLEmailFolderService(
            apiClient,
            logger,
        ),
    private val emailMessageService: EmailMessageService =
        GraphQLEmailMessageService(
            apiClient,
            logger,
        ),
    private val draftEmailMessageService: DraftEmailMessageService =
        GraphQLS3DraftEmailMessageService(
            s3EmailClient,
            apiClient,
            logger,
        ),
    private val blockedAddressService: BlockedAddressService =
        GraphQLBlockedAddressService(
            apiClient,
            logger,
        ),
    private val useCaseFactory: UseCaseFactory =
        DefaultUseCaseFactory(
            emailAddressService = emailAddressService,
            emailFolderService = emailFolderService,
            emailMessageService = emailMessageService,
            configurationDataService = configurationDataService,
            draftEmailMessageService = draftEmailMessageService,
            blockedAddressService = blockedAddressService,
            emailMessageDataProcessor =
                DefaultEmailMessageDataProcessor(
                    context,
                    emailCryptoService = emailCryptoService,
                ),
            s3TransientClient = s3TransientClient,
            s3EmailClient = s3EmailClient,
            transientBucket = transientBucket,
            serviceKeyManager = serviceKeyManager,
            sealingService = sealingService,
            sudoUserClient = sudoUserClient,
            emailCryptoService = emailCryptoService,
            emailMaskService = emailMaskService,
            logger = logger,
        ),
    private val subscriptions: SubscriptionService =
        SubscriptionService(apiClient, serviceKeyManager, sudoUserClient, logger),
) : SudoEmailClient {
    /**
     * Checksum's for each file are generated and are used to create a checksum that is used when
     * publishing to maven central. In order to retry a failed publish without needing to change any
     * functionality, we need a way to generate a different checksum for the source code. We can
     * change the value of this property which will generate a different checksum for publishing
     * and allow us to retry. The value of `version` doesn't need to be kept up-to-date with the
     * version of the code.
     */
    private val version: String = "18.0.0"

    /** Long-lived invoker for async (set and forget) email client operations. */
    val invoker = EmailClientInvoker(this, logger)

    @Throws(SudoEmailClient.EmailConfigurationException::class)
    override suspend fun getConfigurationData(): ConfigurationData {
        val config = configurationDataService.getConfigurationData()

        return ConfigurationDataTransformer.entityToApi(config)
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun getEmailMaskDomains(): List<String> = configurationDataService.getEmailMaskDomains()

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun getSupportedEmailDomains(): List<String> = configurationDataService.getSupportedEmailDomains()

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun getConfiguredEmailDomains(): List<String> = configurationDataService.getConfiguredEmailDomains()

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun checkEmailAddressAvailability(input: CheckEmailAddressAvailabilityInput): List<String> {
        logger.debug("checkEmailAddressAvailability input: $input")

        return emailAddressService.checkAvailability(
            CheckEmailAddressAvailabilityRequest(
                input.localParts,
                input.domains,
            ),
        )
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun provisionEmailAddress(input: ProvisionEmailAddressInput): EmailAddress {
        logger.debug("provisionEmailAddress input: $input")

        val useCase = useCaseFactory.createProvisionEmailAddressUseCase()

        val result =
            useCase.execute(
                ProvisionEmailAddressUseCaseInput(
                    input.emailAddress,
                    input.ownershipProofToken,
                    input.alias,
                    input.keyId,
                ),
            )
        return EmailAddressTransformer.unsealedEntityToApi(result)
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun deprovisionEmailAddress(id: String): EmailAddress {
        logger.debug("deprovisionEmailAddress id: $id")

        val result =
            emailAddressService.deprovision(
                DeprovisionEmailAddressRequest(
                    emailAddressId = id,
                ),
            )
        return EmailAddressTransformer.sealedEntityToApi(result)
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun updateEmailAddressMetadata(input: UpdateEmailAddressMetadataInput): String {
        logger.debug("updateEmailAddressMetadata input: $input")

        val useCase = useCaseFactory.createUpdateEmailAddressMetadataUseCase()

        return useCase.execute(
            UpdateEmailAddressMetadataUseCaseInput(
                input.id,
                input.alias,
            ),
        )
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun getEmailAddress(input: GetEmailAddressInput): EmailAddress? {
        logger.debug("getEmailAddress input: $input")

        val useCase =
            useCaseFactory.createGetEmailAddressUseCase()

        val result =
            useCase.execute(
                GetEmailAddressUseCaseInput(
                    input.id,
                ),
            )
        return result?.let { EmailAddressTransformer.unsealedEntityToApi(it) }
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun listEmailAddresses(input: ListEmailAddressesInput): ListAPIResult<EmailAddress, PartialEmailAddress> {
        logger.debug("listEmailAddresses input: $input")

        val useCase =
            useCaseFactory.createListEmailAddressesUseCase()

        val result =
            useCase.execute(
                ListEmailAddressesUseCaseInput(
                    input.limit,
                    input.nextToken,
                ),
            )

        return ListApiResultTransformer.transformEmailAddressListApiResultEntity(result)
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun listEmailAddressesForSudoId(
        input: ListEmailAddressesForSudoIdInput,
    ): ListAPIResult<EmailAddress, PartialEmailAddress> {
        logger.debug("listEmailAddressesForSudoId input: $input")

        val useCase =
            useCaseFactory.createListEmailAddressesForSudoIdUseCase()

        val result =
            useCase.execute(
                ListEmailAddressesForSudoIdUseCaseInput(
                    input.sudoId,
                    input.limit,
                    input.nextToken,
                ),
            )

        return ListApiResultTransformer.transformEmailAddressListApiResultEntity(result)
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun lookupEmailAddressesPublicInfo(input: LookupEmailAddressesPublicInfoInput): List<EmailAddressPublicInfo> {
        logger.debug("lookupEmailAddressesPublicInfo input: $input")

        val useCase = useCaseFactory.createLookupEmailAddressesPublicInfoUseCase()

        val result = useCase.execute(LookupEmailAddressesPublicInfoUseCaseInput(input.emailAddresses))

        return result.map { EmailAddressPublicInfoTransformer.entityToApi(it) }
    }

    @Throws(SudoEmailClient.EmailFolderException::class)
    override suspend fun listEmailFoldersForEmailAddressId(input: ListEmailFoldersForEmailAddressIdInput): ListOutput<EmailFolder> {
        logger.debug("listEmailFoldersForEmailAddressId input: $input")

        val useCase = useCaseFactory.createListEmailFoldersForEmailAddressIdUseCase()

        val result =
            useCase.execute(
                ListEmailFoldersForEmailAddressIdUseCaseInput(
                    input.emailAddressId,
                    input.limit,
                    input.nextToken,
                ),
            )

        return ListOutput(
            items = result.items.map { EmailFolderTransformer.unsealedEntityToApi(it) },
            nextToken = result.nextToken,
        )
    }

    @Throws(SudoEmailClient.EmailFolderException::class)
    override suspend fun createCustomEmailFolder(input: CreateCustomEmailFolderInput): EmailFolder {
        logger.debug("createCustomEmailFolder input: $input")

        val useCase = useCaseFactory.createCustomEmailFolderUseCase()

        val result =
            useCase.execute(
                CreateCustomEmailFolderUseCaseInput(
                    emailAddressId = input.emailAddressId,
                    customFolderName = input.customFolderName,
                ),
            )

        return EmailFolderTransformer.unsealedEntityToApi(result)
    }

    @Throws(SudoEmailClient.EmailFolderException::class)
    override suspend fun deleteCustomEmailFolder(input: DeleteCustomEmailFolderInput): EmailFolder? {
        logger.debug("deleteCustomEmailFolder input: $input")

        val useCase = useCaseFactory.createDeleteCustomEmailFolderUseCase()

        val result =
            useCase.execute(
                DeleteCustomEmailFolderUseCaseInput(
                    emailFolderId = input.emailFolderId,
                    emailAddressId = input.emailAddressId,
                ),
            )
        return result?.let { EmailFolderTransformer.unsealedEntityToApi(result) }
    }

    @Throws(SudoEmailClient.EmailFolderException::class)
    override suspend fun updateCustomEmailFolder(input: UpdateCustomEmailFolderInput): EmailFolder {
        logger.debug("updateCustomEmailFolder input: $input")

        val useCase = useCaseFactory.createUpdateCustomEmailFolderUseCase()

        val result =
            useCase.execute(
                UpdateCustomEmailFolderUseCaseInput(
                    emailFolderId = input.emailFolderId,
                    emailAddressId = input.emailAddressId,
                    customFolderName = input.customFolderName,
                ),
            )

        return EmailFolderTransformer.unsealedEntityToApi(result)
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun sendEmailMessage(input: SendEmailMessageInput): SendEmailMessageResult {
        logger.debug("sendEmailMessage input: $input")

        val useCase = useCaseFactory.createSendEmailMessageUseCase()

        val (senderEmailAddressId, emailMessageHeader, body, attachments, inlineAttachments, replyingMessageId, forwardingMessageId) = input

        val result =
            useCase.execute(
                SendEmailMessageUseCaseInput(
                    senderEmailAddressId,
                    InternetMessageFormatHeaderTransformer.apiToEntity(emailMessageHeader),
                    body,
                    attachments.map { EmailAttachmentTransformer.apiToEntity(it) },
                    inlineAttachments.map { EmailAttachmentTransformer.apiToEntity(it) },
                    replyingMessageId,
                    forwardingMessageId,
                ),
            )
        return SendEmailMessageResult(
            result.id,
            result.createdAt,
        )
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun updateEmailMessages(
        input: UpdateEmailMessagesInput,
    ): BatchOperationResult<UpdatedEmailMessageSuccess, EmailMessageOperationFailureResult> {
        logger.debug("updateEmailMessages input: $input")

        val useCase = useCaseFactory.createUpdateEmailMessagesUseCase()

        val result =
            useCase.execute(
                UpdateEmailMessagesUseCaseInput(
                    ids = input.ids,
                    values =
                        UpdateEmailMessagesUseCaseInput.UpdatableValues(
                            folderId = input.values.folderId,
                            seen = input.values.seen,
                        ),
                ),
            )

        return BatchOperationResultTransformer.batchUpdateEmailMessagesEntityToApi(result)
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun deleteEmailMessages(
        ids: List<String>,
    ): BatchOperationResult<DeleteEmailMessageSuccessResult, EmailMessageOperationFailureResult> {
        logger.debug("deleteEmailMessages ids: $ids")

        val useCase = useCaseFactory.createDeleteEmailMessagesUseCase()

        val idSet = ids.toSet()
        return useCase.execute(idSet)
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun deleteEmailMessage(id: String): DeleteEmailMessageSuccessResult? {
        logger.debug("deleteEmailMessage id: $id")

        val useCase = useCaseFactory.createDeleteEmailMessagesUseCase()

        return useCase.execute(id)
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun getEmailMessage(input: GetEmailMessageInput): EmailMessage? {
        logger.debug("getEmailMessage input: $input")

        val useCase = useCaseFactory.createGetEmailMessageUseCase()

        val result =
            useCase.execute(
                GetEmailMessageUseCaseInput(
                    id = input.id,
                ),
            )

        result?.let {
            return EmailMessageTransformer.entityToApi(it)
        }
        return null
    }

    @Deprecated(
        "Use getEmailMessageWithBody instead to retrieve email message data",
        ReplaceWith("getEmailMessageWithBody"),
    )
    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun getEmailMessageRfc822Data(input: GetEmailMessageRfc822DataInput): EmailMessageRfc822Data? {
        logger.debug("getEmailMessageRfc822Data input: $input")

        val useCase = useCaseFactory.createGetEmailMessageRfc822DataUseCase()

        val result =
            useCase.execute(
                GetEmailMessageRfc822DataUseCaseInput(
                    id = input.id,
                    emailAddressId = input.emailAddressId,
                ),
            ) ?: return null

        return EmailMessageRfc822Data(result.id, result.rfc822Data)
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun getEmailMessageWithBody(input: GetEmailMessageWithBodyInput): EmailMessageWithBody? {
        logger.debug("getEmailMessageWithBody input: $input")

        val useCase = useCaseFactory.createGetEmailMessageWithBodyUseCase()

        val result =
            useCase.execute(
                GetEmailMessageWithBodyUseCaseInput(
                    id = input.id,
                    emailAddressId = input.emailAddressId,
                ),
            ) ?: return null

        return EmailMessageWithBody(
            input.id,
            result.body,
            result.isHtml,
            result.attachments.map { EmailAttachmentTransformer.entityToApi(it) },
            result.inlineAttachments.map { EmailAttachmentTransformer.entityToApi(it) },
        )
    }

    override suspend fun listEmailMessages(input: ListEmailMessagesInput): ListAPIResult<EmailMessage, PartialEmailMessage> {
        logger.debug("listEmailMessages input: $input")

        val useCase = useCaseFactory.createListEmailMessagesUseCase()

        val result =
            useCase.execute(
                ListEmailMessagesUseCaseInput(
                    limit = input.limit,
                    nextToken = input.nextToken,
                    dateRange = input.dateRange?.let { DateRangeTransformer.apiToEntity(it) },
                    sortOrder = SortOrderTransformer.apiToEntity(input.sortOrder),
                    includeDeletedMessages = input.includeDeletedMessages,
                ),
            )
        return ListApiResultTransformer.transformEmailMessageListApiResultEntity(result)
    }

    override suspend fun listEmailMessagesForEmailAddressId(
        input: ListEmailMessagesForEmailAddressIdInput,
    ): ListAPIResult<EmailMessage, PartialEmailMessage> {
        logger.debug("listEmailMessagesForEmailAddressId input: $input")

        val useCase = useCaseFactory.createListEmailMessagesUseCase()

        val result =
            useCase.execute(
                ListEmailMessagesUseCaseInput(
                    emailAddressId = input.emailAddressId,
                    limit = input.limit,
                    nextToken = input.nextToken,
                    dateRange = input.dateRange?.let { DateRangeTransformer.apiToEntity(it) },
                    sortOrder = SortOrderTransformer.apiToEntity(input.sortOrder),
                    includeDeletedMessages = input.includeDeletedMessages,
                ),
            )
        return ListApiResultTransformer.transformEmailMessageListApiResultEntity(result)
    }

    override suspend fun listEmailMessagesForEmailFolderId(
        input: ListEmailMessagesForEmailFolderIdInput,
    ): ListAPIResult<EmailMessage, PartialEmailMessage> {
        logger.debug("listEmailMessagesForEmailFolderId input: $input")

        val useCase = useCaseFactory.createListEmailMessagesUseCase()

        val result =
            useCase.execute(
                ListEmailMessagesUseCaseInput(
                    emailFolderId = input.folderId,
                    limit = input.limit,
                    nextToken = input.nextToken,
                    dateRange = input.dateRange?.let { DateRangeTransformer.apiToEntity(it) },
                    sortOrder = SortOrderTransformer.apiToEntity(input.sortOrder),
                    includeDeletedMessages = input.includeDeletedMessages,
                ),
            )
        return ListApiResultTransformer.transformEmailMessageListApiResultEntity(result)
    }

    @Throws(
        SudoEmailClient.EmailMessageException::class,
        SudoEmailClient.EmailAddressException::class,
    )
    override suspend fun createDraftEmailMessage(input: CreateDraftEmailMessageInput): String {
        logger.debug("createDraftEmailMessage input: ${input.senderEmailAddressId}")

        val useCase = useCaseFactory.createCreateDraftEmailMessageUseCase()

        return useCase.execute(
            CreateDraftEmailMessageUseCaseInput(
                rfc822Data = input.rfc822Data,
                emailAddressId = input.senderEmailAddressId,
            ),
        )
    }

    @Throws(
        SudoEmailClient.EmailMessageException::class,
        SudoEmailClient.EmailAddressException::class,
    )
    override suspend fun updateDraftEmailMessage(input: UpdateDraftEmailMessageInput): String {
        logger.debug("updateDraftEmailMessage input: $input")

        val useCase = useCaseFactory.createUpdateDraftEmailMessageUseCase()

        return useCase.execute(
            UpdateDraftEmailMessageUseCaseInput(
                rfc822Data = input.rfc822Data,
                emailAddressId = input.senderEmailAddressId,
                draftId = input.id,
            ),
        )
    }

    @Throws(
        SudoEmailClient.EmailAddressException::class,
        SudoEmailClient.EmailMessageException::class,
    )
    override suspend fun deleteDraftEmailMessages(
        input: DeleteDraftEmailMessagesInput,
    ): BatchOperationResult<DeleteEmailMessageSuccessResult, EmailMessageOperationFailureResult> {
        logger.debug("deleteDraftEmailMessages input: $input")

        val useCase = useCaseFactory.createDeleteDraftEmailMessagesUseCase()

        val result =
            useCase.execute(
                DeleteDraftEmailMessagesUseCaseInput(
                    ids = input.ids,
                    emailAddressId = input.emailAddressId,
                ),
            )

        result.successValues?.forEach {
            invoker.cancelScheduledDraftEmailMessage(it.id, input.emailAddressId)
        }

        return BatchOperationResultTransformer.batchDeleteDraftMessagesEntityToApi(result)
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun getDraftEmailMessage(input: GetDraftEmailMessageInput): DraftEmailMessageWithContent {
        logger.debug("getDraftEmailMessage input: $input")

        val useCase = useCaseFactory.createGetDraftEmailMessageUseCase()

        val result =
            useCase.execute(
                GetDraftEmailMessageUseCaseInput(
                    draftId = input.id,
                    emailAddressId = input.emailAddressId,
                ),
            )

        return DraftEmailMessageTransformer.entityWithContentToApi(result)
    }

    @Deprecated(
        "Use listDraftEmailMessagesForEmailAddressId instead to retrieve draft email messages",
        ReplaceWith("listDraftEmailMessagesForEmailAddressId(input)"),
    )
    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun listDraftEmailMessages(): List<DraftEmailMessageWithContent> {
        logger.debug("listDraftEmailMessages")

        val useCase = useCaseFactory.createListDraftEmailMessagesUseCase()

        val result = useCase.execute()

        return result.map { DraftEmailMessageTransformer.entityWithContentToApi(it) }
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun listDraftEmailMessagesForEmailAddressId(
        input: ListDraftEmailMessagesForEmailAddressIdInput,
    ): ListOutput<DraftEmailMessageWithContent> {
        logger.debug("listDraftEmailMessagesForEmailAddressId input: $input")

        val useCase = useCaseFactory.createListDraftEmailMessagesForEmailAddressIdUseCase()

        val result =
            useCase.execute(
                emailAddressId = input.emailAddressId,
                limit = input.limit,
                nextToken = input.nextToken,
            )

        return ListOutput(
            items = result.items.map { DraftEmailMessageTransformer.entityWithContentToApi(it) },
            nextToken = result.nextToken,
        )
    }

    @Deprecated(
        "Use listDraftEmailMessagesMetadataFormEmailAddressId instead to retrieve draft email messages",
        ReplaceWith("listDraftEmailMessageMetadataForEmailAddressId(input)"),
    )
    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun listDraftEmailMessageMetadata(): List<DraftEmailMessageMetadata> {
        logger.debug("listDraftEmailMessageMetadata")

        val useCase = useCaseFactory.createListDraftEmailMessageMetadataUseCase()

        val result = useCase.execute()

        return result.map { DraftEmailMessageTransformer.draftMetadataEntityToApi(it) }
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun listDraftEmailMessageMetadataForEmailAddressId(
        input: ListDraftEmailMessageMetadataForEmailAddressIdInput,
    ): ListOutput<DraftEmailMessageMetadata> {
        logger.debug("listDraftEmailMessageMetadataForEmailAddressId input: $input")

        val useCase = useCaseFactory.createListDraftEmailMessageMetadataForEmailAddressIdUseCase()

        val result =
            useCase.execute(
                emailAddressId = input.emailAddressId,
                limit = input.limit,
                nextToken = input.nextToken,
            )

        return ListOutput(
            items = result.items.map { DraftEmailMessageTransformer.draftMetadataEntityToApi(it) },
            nextToken = result.nextToken,
        )
    }

    override suspend fun scheduleSendDraftMessage(input: ScheduleSendDraftMessageInput): ScheduledDraftMessage {
        logger.debug("scheduleSendDraftMessage input: $input")

        val useCase = useCaseFactory.createScheduleSendDraftMessageUseCase()

        val result =
            useCase.execute(
                ScheduleSendDraftMessageUseCaseInput(
                    id = input.id,
                    emailAddressId = input.emailAddressId,
                    sendAt = input.sendAt,
                ),
            )

        return ScheduledDraftMessageTransformer.entityToApi(result)
    }

    override suspend fun cancelScheduledDraftMessage(input: CancelScheduledDraftMessageInput): String {
        logger.debug("cancelScheduledDraftMessage input: $input")

        val useCase = useCaseFactory.createCancelScheduledDraftMessageUseCase()

        return useCase.execute(
            CancelScheduledDraftMessageUseCaseInput(
                draftId = input.id,
                emailAddressId = input.emailAddressId,
            ),
        )
    }

    override suspend fun listScheduledDraftMessagesForEmailAddressId(
        input: ListScheduledDraftMessagesForEmailAddressIdInput,
    ): ListOutput<ScheduledDraftMessage> {
        logger.debug("listScheduledDraftMessagesForEmailAddressId input: $input")

        val useCase = useCaseFactory.createListScheduledDraftMessagesForEmailAddressIdUseCase()

        return useCase.execute(
            ListScheduledDraftMessagesForEmailAddressIdUseCaseInput(
                emailAddressId = input.emailAddressId,
                limit = input.limit,
                nextToken = input.nextToken,
                filter = input.filter,
            ),
        )
    }

    override suspend fun blockEmailAddresses(
        addresses: List<String>,
        action: BlockedEmailAddressAction?,
        emailAddressId: String?,
        level: BlockedEmailAddressLevel?,
    ): BatchOperationResult<String, String> {
        logger.debug("blockEmailAddresses addresses: $addresses, action: $action, emailAddressId: $emailAddressId, level: $level")

        val useCase = useCaseFactory.createBlockEmailAddressesUseCase()

        val result =
            useCase.execute(
                BlockEmailAddressesUseCaseInput(
                    addresses = addresses,
                    action = action ?: BlockedEmailAddressAction.DROP,
                    emailAddressId = emailAddressId,
                    level = level ?: BlockedEmailAddressLevel.ADDRESS,
                ),
            )

        return BatchOperationResult(
            status =
                when (result.status) {
                    BatchOperationStatusEntity.SUCCESS -> BatchOperationStatus.SUCCESS
                    BatchOperationStatusEntity.FAILURE -> BatchOperationStatus.FAILURE
                    else -> BatchOperationStatus.PARTIAL
                },
            successValues = result.successValues,
            failureValues = result.failureValues,
        )
    }

    @Deprecated(
        "Use unblockEmailAddressesByHashedValue instead",
        ReplaceWith("unblockEmailAddressesByHashedValue"),
    )
    override suspend fun unblockEmailAddresses(addresses: List<String>): BatchOperationResult<String, String> {
        logger.debug("unblockEmailAddresses addresses: $addresses")

        val useCase = useCaseFactory.createUnblockEmailAddressesUseCase()

        val result =
            useCase.execute(
                UnblockEmailAddressesUseCaseInput(
                    addresses = addresses,
                ),
            )

        return BatchOperationResult(
            status =
                when (result.status) {
                    BatchOperationStatusEntity.SUCCESS -> BatchOperationStatus.SUCCESS
                    BatchOperationStatusEntity.FAILURE -> BatchOperationStatus.FAILURE
                    else -> BatchOperationStatus.PARTIAL
                },
            successValues = result.successValues,
            failureValues = result.failureValues,
        )
    }

    override suspend fun unblockEmailAddressesByHashedValue(hashedValues: List<String>): BatchOperationResult<String, String> {
        logger.debug("unblockEmailAddressesByHashedValue hashedValues: $hashedValues")

        val useCase = useCaseFactory.createUnblockEmailAddressesByHashedValueUseCase()

        val result =
            useCase.execute(
                UnblockEmailAddressesByHashedValueUseCaseInput(
                    hashedValues = hashedValues,
                ),
            )

        return BatchOperationResult(
            status =
                when (result.status) {
                    BatchOperationStatusEntity.SUCCESS -> BatchOperationStatus.SUCCESS
                    BatchOperationStatusEntity.FAILURE -> BatchOperationStatus.FAILURE
                    else -> BatchOperationStatus.PARTIAL
                },
            successValues = result.successValues,
            failureValues = result.failureValues,
        )
    }

    override suspend fun getEmailAddressBlocklist(): List<UnsealedBlockedAddress> {
        logger.debug("getEmailAddressBlocklist")

        val useCase = useCaseFactory.createGetEmailAddressBlocklistUseCase()

        return useCase.execute()
    }

    override suspend fun deleteMessagesForFolderId(input: DeleteMessagesForFolderIdInput): String {
        logger.debug("deleteMessagesForFolderId input: $input")

        return emailMessageService.deleteForFolderId(
            DeleteMessageForFolderIdRequest(
                emailAddressId = input.emailAddressId,
                emailFolderId = input.emailFolderId,
                hardDelete = input.hardDelete,
            ),
        )
    }

    @Throws(SudoEmailClient.EmailCryptographicKeysException::class)
    override suspend fun importKeys(archiveData: ByteArray) {
        if (archiveData.isEmpty()) {
            throw SudoEmailClient.EmailCryptographicKeysException.SecureKeyArchiveException(
                StringConstants.INVALID_ARGUMENT_ERROR_MSG,
            )
        }
        try {
            serviceKeyManager.importKeys(archiveData)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretEmailMessageException(e)
        }
    }

    @Throws(SudoEmailClient.EmailCryptographicKeysException::class)
    override suspend fun exportKeys(): ByteArray {
        try {
            return serviceKeyManager.exportKeys()
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw ErrorTransformer.interpretEmailMessageException(e)
        }
    }

    override suspend fun provisionEmailMask(input: ProvisionEmailMaskInput): EmailMask {
        logger.debug("provisionEmailMask input: $input")

        val useCase = useCaseFactory.createProvisionEmailMaskUseCase()

        val result =
            useCase.execute(
                ProvisionEmailMaskUseCaseInput(
                    input.maskAddress,
                    input.realAddress,
                    input.ownershipProofToken,
                    input.metadata,
                    input.expiresAt,
                    input.keyId,
                ),
            )
        return EmailMaskTransformer.unsealedEntityToApi(result)
    }

    override suspend fun deprovisionEmailMask(input: DeprovisionEmailMaskInput): PartialEmailMask {
        logger.debug("deprovisionEmailMask input: $input")

        val useCase = useCaseFactory.createDeprovisionEmailMaskUseCase()

        val result =
            useCase.execute(
                DeprovisionEmailMaskUseCaseInput(
                    input.emailMaskId,
                ),
            )
        return EmailMaskTransformer.partialEntityToApi(result)
    }

    override suspend fun updateEmailMask(input: UpdateEmailMaskInput): EmailMask {
        logger.debug("updateEmailMask input: $input")

        val useCase = useCaseFactory.createUpdateEmailMaskUseCase()

        val result =
            useCase.execute(
                UpdateEmailMaskUseCaseInput(
                    input.emailMaskId,
                    input.metadata,
                    input.expiresAt,
                ),
            )
        return EmailMaskTransformer.unsealedEntityToApi(result)
    }

    override suspend fun enableEmailMask(input: EnableEmailMaskInput): EmailMask {
        logger.debug("enableEmailMask input: $input")

        val useCase = useCaseFactory.createEnableEmailMaskUseCase()

        val result =
            useCase.execute(
                EnableEmailMaskUseCaseInput(
                    input.emailMaskId,
                ),
            )
        return EmailMaskTransformer.unsealedEntityToApi(result)
    }

    override suspend fun disableEmailMask(input: DisableEmailMaskInput): EmailMask {
        logger.debug("disableEmailMask input: $input")

        val useCase = useCaseFactory.createDisableEmailMaskUseCase()

        val result =
            useCase.execute(
                DisableEmailMaskUseCaseInput(
                    input.emailMaskId,
                ),
            )
        return EmailMaskTransformer.unsealedEntityToApi(result)
    }

    override suspend fun listEmailMasksForOwner(input: ListEmailMasksForOwnerInput): ListAPIResult<EmailMask, PartialEmailMask> {
        logger.debug("listEmailMasksForOwner input: $input")

        val useCase =
            useCaseFactory.createListEmailMasksUseCase()

        val result =
            useCase.execute(
                ListEmailMasksUseCaseInput(
                    input.limit,
                    input.nextToken,
                    input.filter,
                ),
            )

        return ListApiResultTransformer.transformEmailMaskListApiResultEntity(result)
    }

    override suspend fun subscribeToEmailMessages(
        id: String,
        subscriber: EmailMessageSubscriber,
    ) {
        subscriptions.subscribeEmailMessages(id, subscriber)
    }

    override suspend fun unsubscribeFromEmailMessages(id: String) {
        subscriptions.unsubscribeEmailMessages(id)
    }

    override suspend fun unsubscribeAllFromEmailMessages() {
        subscriptions.unsubscribeAllEmailMessages()
    }

    override fun close() {
        subscriptions.close()
    }

    override fun reset() {
        close()
        this.serviceKeyManager.removeAllKeys()
    }
}

data class SudoEmailNotificationSchemaEntry(
    override val description: String,
    override val fieldName: String,
    override val type: String,
) : NotificationSchemaEntry

data class SudoEmailNotificationMetaData(
    override val serviceName: String,
    override val schema: List<SudoEmailNotificationSchemaEntry>,
) : NotificationMetaData
