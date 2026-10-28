/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases

import com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress.BlockedAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasService
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.EmailFolderService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.EmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.EmailMessageBodyCache
import com.sudoplatform.sudoemail.internal.domain.useCases.blockedAddress.BlockEmailAddressesUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.blockedAddress.GetEmailAddressBlocklistUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.blockedAddress.UnblockEmailAddressesByHashedValueUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.blockedAddress.UnblockEmailAddressesUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.CancelScheduledDraftMessageUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.CreateDraftEmailMessageUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.DeleteDraftEmailMessagesUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.GetDraftEmailMessageUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.ListDraftEmailMessageMetadataForEmailAddressIdUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.ListDraftEmailMessageMetadataUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.ListDraftEmailMessagesForEmailAddressIdUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.ListDraftEmailMessagesUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.ListScheduledDraftMessagesForEmailAddressIdUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.ScheduleSendDraftMessageUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.UpdateDraftEmailMessageUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.GetEmailAddressUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.ListEmailAddressesForSudoIdUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.ListEmailAddressesUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.LookupEmailAddressesPublicInfoUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.ProvisionEmailAddressUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress.UpdateEmailAddressMetadataUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias.DeprovisionEmailAliasUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias.DisableEmailAliasUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias.EnableEmailAliasUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias.ListEmailAliasesUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias.ProvisionEmailAliasUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias.UpdateEmailAliasUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias.VerifyExternalEmailAddressUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder.CreateCustomEmailFolderUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder.DeleteCustomEmailFolderUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder.ListEmailFoldersForEmailAddressIdUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder.UpdateCustomEmailFolderUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.DeleteEmailMessagesUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.GetEmailMessageUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.GetEmailMessageWithBodyUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.ListEmailMessagesUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.SendEmailMessageUseCase
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.UpdateEmailMessagesUseCase
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient

internal interface UseCaseFactory {
    fun createProvisionEmailAddressUseCase(): ProvisionEmailAddressUseCase

    fun createUpdateEmailAddressMetadataUseCase(): UpdateEmailAddressMetadataUseCase

    fun createGetEmailAddressUseCase(): GetEmailAddressUseCase

    fun createListEmailAddressesUseCase(): ListEmailAddressesUseCase

    fun createListEmailAddressesForSudoIdUseCase(): ListEmailAddressesForSudoIdUseCase

    fun createLookupEmailAddressesPublicInfoUseCase(): LookupEmailAddressesPublicInfoUseCase

    fun createListEmailFoldersForEmailAddressIdUseCase(): ListEmailFoldersForEmailAddressIdUseCase

    fun createCustomEmailFolderUseCase(): CreateCustomEmailFolderUseCase

    fun createDeleteCustomEmailFolderUseCase(): DeleteCustomEmailFolderUseCase

    fun createUpdateCustomEmailFolderUseCase(): UpdateCustomEmailFolderUseCase

    fun createSendEmailMessageUseCase(): SendEmailMessageUseCase

    fun createUpdateEmailMessagesUseCase(): UpdateEmailMessagesUseCase

    fun createDeleteEmailMessagesUseCase(): DeleteEmailMessagesUseCase

    fun createGetEmailMessageUseCase(): GetEmailMessageUseCase

    fun createGetEmailMessageWithBodyUseCase(): GetEmailMessageWithBodyUseCase

    fun createListEmailMessagesUseCase(): ListEmailMessagesUseCase

    fun createCreateDraftEmailMessageUseCase(): CreateDraftEmailMessageUseCase

    fun createGetDraftEmailMessageUseCase(): GetDraftEmailMessageUseCase

    fun createUpdateDraftEmailMessageUseCase(): UpdateDraftEmailMessageUseCase

    fun createDeleteDraftEmailMessagesUseCase(): DeleteDraftEmailMessagesUseCase

    fun createListDraftEmailMessageMetadataForEmailAddressIdUseCase(): ListDraftEmailMessageMetadataForEmailAddressIdUseCase

    fun createListDraftEmailMessageMetadataUseCase(): ListDraftEmailMessageMetadataUseCase

    fun createListDraftEmailMessagesForEmailAddressIdUseCase(): ListDraftEmailMessagesForEmailAddressIdUseCase

    fun createListDraftEmailMessagesUseCase(): ListDraftEmailMessagesUseCase

    fun createScheduleSendDraftMessageUseCase(): ScheduleSendDraftMessageUseCase

    fun createCancelScheduledDraftMessageUseCase(): CancelScheduledDraftMessageUseCase

    fun createListScheduledDraftMessagesForEmailAddressIdUseCase(): ListScheduledDraftMessagesForEmailAddressIdUseCase

    fun createBlockEmailAddressesUseCase(): BlockEmailAddressesUseCase

    fun createUnblockEmailAddressesUseCase(): UnblockEmailAddressesUseCase

    fun createUnblockEmailAddressesByHashedValueUseCase(): UnblockEmailAddressesByHashedValueUseCase

    fun createGetEmailAddressBlocklistUseCase(): GetEmailAddressBlocklistUseCase

    fun createProvisionEmailAliasUseCase(): ProvisionEmailAliasUseCase

    fun createDeprovisionEmailAliasUseCase(): DeprovisionEmailAliasUseCase

    fun createUpdateEmailAliasUseCase(): UpdateEmailAliasUseCase

    fun createEnableEmailAliasUseCase(): EnableEmailAliasUseCase

    fun createDisableEmailAliasUseCase(): DisableEmailAliasUseCase

    fun createListEmailAliasesUseCase(): ListEmailAliasesUseCase

    fun createVerifyExternalEmailAddressUseCase(): VerifyExternalEmailAddressUseCase
}

internal class DefaultUseCaseFactory(
    private val emailAddressService: EmailAddressService,
    private val emailFolderService: EmailFolderService,
    private val emailMessageService: EmailMessageService,
    private val emailAliasService: EmailAliasService,
    private val configurationDataService: ConfigurationDataService,
    private val draftEmailMessageService: DraftEmailMessageService,
    private val blockedAddressService: BlockedAddressService,
    private val emailMessageDataProcessor: EmailMessageDataProcessor,
    private val s3TransientClient: S3Client,
    private val s3EmailClient: S3Client,
    private val transientBucket: String,
    private val serviceKeyManager: ServiceKeyManager,
    private val sealingService: SealingService,
    private val sudoUserClient: SudoUserClient,
    private val emailCryptoService: EmailCryptoService,
    private val emailMessageBodyCache: EmailMessageBodyCache,
    private val logger: Logger,
) : UseCaseFactory {
    override fun createProvisionEmailAddressUseCase(): ProvisionEmailAddressUseCase =
        ProvisionEmailAddressUseCase(
            emailAddressService = emailAddressService,
            serviceKeyManager = serviceKeyManager,
            sealingService = sealingService,
            logger = logger,
        )

    override fun createUpdateEmailAddressMetadataUseCase(): UpdateEmailAddressMetadataUseCase =
        UpdateEmailAddressMetadataUseCase(
            emailAddressService = emailAddressService,
            serviceKeyManager = serviceKeyManager,
            sealingService = sealingService,
            logger = logger,
        )

    override fun createGetEmailAddressUseCase(): GetEmailAddressUseCase =
        GetEmailAddressUseCase(
            emailAddressService = emailAddressService,
            serviceKeyManager = serviceKeyManager,
            logger = logger,
        )

    override fun createListEmailAddressesUseCase(): ListEmailAddressesUseCase =
        ListEmailAddressesUseCase(
            emailAddressService = emailAddressService,
            serviceKeyManager = serviceKeyManager,
            logger = logger,
        )

    override fun createListEmailAddressesForSudoIdUseCase(): ListEmailAddressesForSudoIdUseCase =
        ListEmailAddressesForSudoIdUseCase(
            emailAddressService = emailAddressService,
            serviceKeyManager = serviceKeyManager,
            logger = logger,
        )

    override fun createLookupEmailAddressesPublicInfoUseCase(): LookupEmailAddressesPublicInfoUseCase =
        LookupEmailAddressesPublicInfoUseCase(
            emailAddressService = emailAddressService,
            logger = logger,
        )

    override fun createListEmailFoldersForEmailAddressIdUseCase(): ListEmailFoldersForEmailAddressIdUseCase =
        ListEmailFoldersForEmailAddressIdUseCase(
            emailFolderService = emailFolderService,
            serviceKeyManager = serviceKeyManager,
            logger = logger,
        )

    override fun createCustomEmailFolderUseCase(): CreateCustomEmailFolderUseCase =
        CreateCustomEmailFolderUseCase(
            emailFolderService = emailFolderService,
            serviceKeyManager = serviceKeyManager,
            sealingService = sealingService,
            logger = logger,
        )

    override fun createDeleteCustomEmailFolderUseCase(): DeleteCustomEmailFolderUseCase =
        DeleteCustomEmailFolderUseCase(
            emailFolderService = emailFolderService,
            serviceKeyManager = serviceKeyManager,
            logger = logger,
        )

    override fun createUpdateCustomEmailFolderUseCase(): UpdateCustomEmailFolderUseCase =
        UpdateCustomEmailFolderUseCase(
            emailFolderService = emailFolderService,
            serviceKeyManager = serviceKeyManager,
            sealingService = sealingService,
            logger = logger,
        )

    override fun createSendEmailMessageUseCase(): SendEmailMessageUseCase =
        SendEmailMessageUseCase(
            emailMessageService = emailMessageService,
            emailAddressService = emailAddressService,
            configurationDataService = configurationDataService,
            emailMessageDataProcessor = emailMessageDataProcessor,
            s3TransientClient = s3TransientClient,
            transientBucket = transientBucket,
            logger = logger,
        )

    override fun createUpdateEmailMessagesUseCase(): UpdateEmailMessagesUseCase =
        UpdateEmailMessagesUseCase(
            emailMessageService = emailMessageService,
            configurationDataService = configurationDataService,
            logger = logger,
        )

    override fun createDeleteEmailMessagesUseCase(): DeleteEmailMessagesUseCase =
        DeleteEmailMessagesUseCase(
            emailMessageService = emailMessageService,
            configurationDataService = configurationDataService,
            logger = logger,
            emailMessageBodyCache = emailMessageBodyCache,
        )

    override fun createGetEmailMessageUseCase(): GetEmailMessageUseCase =
        GetEmailMessageUseCase(
            emailMessageService = emailMessageService,
            serviceKeyManager = serviceKeyManager,
            logger = logger,
        )

    override fun createGetEmailMessageWithBodyUseCase(): GetEmailMessageWithBodyUseCase =
        GetEmailMessageWithBodyUseCase(
            emailMessageService = emailMessageService,
            s3EmailClient = s3EmailClient,
            serviceKeyManager = serviceKeyManager,
            emailMessageDataProcessor = emailMessageDataProcessor,
            emailCryptoService = emailCryptoService,
            logger = logger,
            emailMessageBodyCache = emailMessageBodyCache,
        )

    override fun createListEmailMessagesUseCase(): ListEmailMessagesUseCase =
        ListEmailMessagesUseCase(
            emailMessageService = emailMessageService,
            serviceKeyManager = serviceKeyManager,
            logger = logger,
        )

    override fun createCreateDraftEmailMessageUseCase(): CreateDraftEmailMessageUseCase =
        CreateDraftEmailMessageUseCase(
            draftEmailMessageService = draftEmailMessageService,
            emailAddressService = emailAddressService,
            configurationDataService = configurationDataService,
            emailMessageDataProcessor = emailMessageDataProcessor,
            serviceKeyManager = serviceKeyManager,
            sealingService = sealingService,
            logger = logger,
        )

    override fun createGetDraftEmailMessageUseCase(): GetDraftEmailMessageUseCase =
        GetDraftEmailMessageUseCase(
            draftEmailMessageService = draftEmailMessageService,
            emailAddressService = emailAddressService,
            emailMessageDataProcessor = emailMessageDataProcessor,
            emailCryptoService = emailCryptoService,
            sealingService = sealingService,
            logger = logger,
        )

    override fun createUpdateDraftEmailMessageUseCase(): UpdateDraftEmailMessageUseCase =
        UpdateDraftEmailMessageUseCase(
            draftEmailMessageService = draftEmailMessageService,
            emailAddressService = emailAddressService,
            configurationDataService = configurationDataService,
            emailMessageDataProcessor = emailMessageDataProcessor,
            serviceKeyManager = serviceKeyManager,
            sealingService = sealingService,
            logger = logger,
        )

    override fun createDeleteDraftEmailMessagesUseCase(): DeleteDraftEmailMessagesUseCase =
        DeleteDraftEmailMessagesUseCase(
            draftEmailMessageService = draftEmailMessageService,
            emailAddressService = emailAddressService,
            logger = logger,
        )

    override fun createListDraftEmailMessageMetadataForEmailAddressIdUseCase(): ListDraftEmailMessageMetadataForEmailAddressIdUseCase =
        ListDraftEmailMessageMetadataForEmailAddressIdUseCase(
            draftEmailMessageService = draftEmailMessageService,
            emailAddressService = emailAddressService,
            logger = logger,
        )

    override fun createListDraftEmailMessageMetadataUseCase(): ListDraftEmailMessageMetadataUseCase =
        ListDraftEmailMessageMetadataUseCase(
            draftEmailMessageService = draftEmailMessageService,
            emailAddressService = emailAddressService,
            logger = logger,
        )

    override fun createListDraftEmailMessagesForEmailAddressIdUseCase(): ListDraftEmailMessagesForEmailAddressIdUseCase =
        ListDraftEmailMessagesForEmailAddressIdUseCase(
            draftEmailMessageService = draftEmailMessageService,
            emailAddressService = emailAddressService,
            logger = logger,
            sealingService = sealingService,
            emailMessageDataProcessor = emailMessageDataProcessor,
            emailCryptoService = emailCryptoService,
        )

    override fun createListDraftEmailMessagesUseCase(): ListDraftEmailMessagesUseCase =
        ListDraftEmailMessagesUseCase(
            draftEmailMessageService = draftEmailMessageService,
            emailAddressService = emailAddressService,
            logger = logger,
            sealingService = sealingService,
            emailMessageDataProcessor = emailMessageDataProcessor,
            emailCryptoService = emailCryptoService,
        )

    override fun createScheduleSendDraftMessageUseCase(): ScheduleSendDraftMessageUseCase =
        ScheduleSendDraftMessageUseCase(
            draftEmailMessageService = draftEmailMessageService,
            emailAddressService = emailAddressService,
            serviceKeyManager = serviceKeyManager,
            sudoUserClient = sudoUserClient,
            logger = logger,
        )

    override fun createCancelScheduledDraftMessageUseCase(): CancelScheduledDraftMessageUseCase =
        CancelScheduledDraftMessageUseCase(
            draftEmailMessageService = draftEmailMessageService,
            emailAddressService = emailAddressService,
            sudoUserClient = sudoUserClient,
            logger = logger,
        )

    override fun createListScheduledDraftMessagesForEmailAddressIdUseCase(): ListScheduledDraftMessagesForEmailAddressIdUseCase =
        ListScheduledDraftMessagesForEmailAddressIdUseCase(
            draftEmailMessageService = draftEmailMessageService,
            emailAddressService = emailAddressService,
            logger = logger,
        )

    override fun createBlockEmailAddressesUseCase(): BlockEmailAddressesUseCase =
        BlockEmailAddressesUseCase(
            blockedAddressService = blockedAddressService,
            serviceKeyManager = serviceKeyManager,
            sudoUserClient = sudoUserClient,
            sealingService = sealingService,
            logger = logger,
        )

    override fun createUnblockEmailAddressesUseCase(): UnblockEmailAddressesUseCase =
        UnblockEmailAddressesUseCase(
            blockedAddressService = blockedAddressService,
            sudoUserClient = sudoUserClient,
            logger = logger,
        )

    override fun createUnblockEmailAddressesByHashedValueUseCase(): UnblockEmailAddressesByHashedValueUseCase =
        UnblockEmailAddressesByHashedValueUseCase(
            blockedAddressService = blockedAddressService,
            sudoUserClient = sudoUserClient,
            logger = logger,
        )

    override fun createGetEmailAddressBlocklistUseCase(): GetEmailAddressBlocklistUseCase =
        GetEmailAddressBlocklistUseCase(
            blockedAddressService = blockedAddressService,
            serviceKeyManager = serviceKeyManager,
            sudoUserClient = sudoUserClient,
            sealingService = sealingService,
            logger = logger,
        )

    override fun createProvisionEmailAliasUseCase(): ProvisionEmailAliasUseCase =
        ProvisionEmailAliasUseCase(
            emailAliasService = emailAliasService,
            serviceKeyManager = serviceKeyManager,
            sealingService = sealingService,
            logger = logger,
        )

    override fun createDeprovisionEmailAliasUseCase(): DeprovisionEmailAliasUseCase =
        DeprovisionEmailAliasUseCase(
            emailAliasService = emailAliasService,
            logger = logger,
        )

    override fun createUpdateEmailAliasUseCase(): UpdateEmailAliasUseCase =
        UpdateEmailAliasUseCase(
            emailAliasService = emailAliasService,
            serviceKeyManager = serviceKeyManager,
            sealingService = sealingService,
            logger = logger,
        )

    override fun createEnableEmailAliasUseCase(): EnableEmailAliasUseCase =
        EnableEmailAliasUseCase(
            emailAliasService = emailAliasService,
            serviceKeyManager = serviceKeyManager,
            logger = logger,
        )

    override fun createDisableEmailAliasUseCase(): DisableEmailAliasUseCase =
        DisableEmailAliasUseCase(
            emailAliasService = emailAliasService,
            serviceKeyManager = serviceKeyManager,
            logger = logger,
        )

    override fun createListEmailAliasesUseCase(): ListEmailAliasesUseCase =
        ListEmailAliasesUseCase(
            emailAliasService = emailAliasService,
            serviceKeyManager = serviceKeyManager,
            logger = logger,
        )

    override fun createVerifyExternalEmailAddressUseCase(): VerifyExternalEmailAddressUseCase =
        VerifyExternalEmailAddressUseCase(
            emailAliasService = emailAliasService,
        )
}
