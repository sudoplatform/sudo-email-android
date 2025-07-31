/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.data

import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.graphql.BlockEmailAddressesMutation
import com.sudoplatform.sudoemail.graphql.CancelScheduledDraftMessageMutation
import com.sudoplatform.sudoemail.graphql.CheckEmailAddressAvailabilityQuery
import com.sudoplatform.sudoemail.graphql.CreateCustomEmailFolderMutation
import com.sudoplatform.sudoemail.graphql.DeleteCustomEmailFolderMutation
import com.sudoplatform.sudoemail.graphql.DeleteEmailMessagesMutation
import com.sudoplatform.sudoemail.graphql.DeleteMessagesByFolderIdMutation
import com.sudoplatform.sudoemail.graphql.DeprovisionEmailAddressMutation
import com.sudoplatform.sudoemail.graphql.GetConfiguredEmailDomainsQuery
import com.sudoplatform.sudoemail.graphql.GetEmailAddressBlocklistQuery
import com.sudoplatform.sudoemail.graphql.GetEmailAddressQuery
import com.sudoplatform.sudoemail.graphql.GetEmailConfigQuery
import com.sudoplatform.sudoemail.graphql.GetEmailDomainsQuery
import com.sudoplatform.sudoemail.graphql.GetEmailMessageQuery
import com.sudoplatform.sudoemail.graphql.ListEmailAddressesForSudoIdQuery
import com.sudoplatform.sudoemail.graphql.ListEmailAddressesQuery
import com.sudoplatform.sudoemail.graphql.ListEmailFoldersForEmailAddressIdQuery
import com.sudoplatform.sudoemail.graphql.ListEmailMessagesForEmailAddressIdQuery
import com.sudoplatform.sudoemail.graphql.ListEmailMessagesForEmailFolderIdQuery
import com.sudoplatform.sudoemail.graphql.ListEmailMessagesQuery
import com.sudoplatform.sudoemail.graphql.ListScheduledDraftMessagesForEmailAddressIdQuery
import com.sudoplatform.sudoemail.graphql.LookupEmailAddressesPublicInfoQuery
import com.sudoplatform.sudoemail.graphql.ProvisionEmailAddressMutation
import com.sudoplatform.sudoemail.graphql.ScheduleSendDraftMessageMutation
import com.sudoplatform.sudoemail.graphql.SendEmailMessageMutation
import com.sudoplatform.sudoemail.graphql.SendEncryptedEmailMessageMutation
import com.sudoplatform.sudoemail.graphql.UnblockEmailAddressesMutation
import com.sudoplatform.sudoemail.graphql.UpdateCustomEmailFolderMutation
import com.sudoplatform.sudoemail.graphql.UpdateEmailAddressMetadataMutation
import com.sudoplatform.sudoemail.graphql.UpdateEmailMessagesMutation
import com.sudoplatform.sudoemail.graphql.fragment.BlockAddressesResult
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddress
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressPublicInfo
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressPublicKey
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.graphql.fragment.EmailConfigurationData
import com.sudoplatform.sudoemail.graphql.fragment.EmailFolder
import com.sudoplatform.sudoemail.graphql.fragment.GetEmailAddressBlocklistResponse
import com.sudoplatform.sudoemail.graphql.fragment.ScheduledDraftMessage
import com.sudoplatform.sudoemail.graphql.fragment.SealedAttribute
import com.sudoplatform.sudoemail.graphql.fragment.SealedEmailMessage
import com.sudoplatform.sudoemail.graphql.fragment.SendEmailMessageResult
import com.sudoplatform.sudoemail.graphql.fragment.UnblockAddressesResult
import com.sudoplatform.sudoemail.graphql.fragment.UpdateEmailMessagesResult
import com.sudoplatform.sudoemail.graphql.type.BlockEmailAddressesBulkUpdateStatus
import com.sudoplatform.sudoemail.graphql.type.BlockedAddressAction
import com.sudoplatform.sudoemail.graphql.type.EmailMessageDirection
import com.sudoplatform.sudoemail.graphql.type.EmailMessageEncryptionStatus
import com.sudoplatform.sudoemail.graphql.type.EmailMessageState
import com.sudoplatform.sudoemail.graphql.type.KeyFormat
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailMessagesStatus
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm

object DataFactory {

    fun getEmailAddressWithoutFolder(
        id: String = "emailAddressId",
        owner: String = "owner",
        owners: List<EmailAddressWithoutFolders.Owner> = listOf(
            EmailAddressWithoutFolders.Owner(
                id = "ownerId",
                issuer = "issuer",
            ),
        ),
        identityId: String = "identityId",
        keyRingId: String = "keyRingId",
        keyIds: List<String> = emptyList(),
        version: Int = 1,
        createdAtEpochMs: Double = 1.0,
        updatedAtEpochMs: Double = 1.0,
        lastReceivedAtEpochMs: Double = 1.0,
        emailAddress: String = "example@sudoplatform.com",
        size: Double = 0.0,
        numberOfEmailMessages: Int = 0,
        alias: EmailAddressWithoutFolders.Alias? = null,
    ) = EmailAddressWithoutFolders(
        id,
        owner,
        owners,
        identityId,
        keyRingId,
        keyIds,
        version,
        createdAtEpochMs,
        updatedAtEpochMs,
        lastReceivedAtEpochMs,
        emailAddress,
        size,
        numberOfEmailMessages,
        alias,
    )

    fun getEmailFolder(
        id: String = "folderId",
        owner: String = "owner",
        owners: List<EmailFolder.Owner> = listOf(
            EmailFolder.Owner(
                id = "ownerId",
                issuer = "issuer",
            ),
        ),
        version: Int = 1,
        createdAtEpochMs: Double = 1.0,
        updatedAtEpochMs: Double = 1.0,
        emailAddressId: String = "emailAddressId",
        folderName: String = "folderName",
        size: Double = 0.0,
        unseenCount: Double = 0.0,
        ttl: Double = 1.0,
        customFolderName: EmailFolder.CustomFolderName? = null,
    ) = EmailFolder(
        id,
        owner,
        owners,
        version,
        createdAtEpochMs,
        updatedAtEpochMs,
        emailAddressId,
        folderName,
        size,
        unseenCount,
        ttl,
        customFolderName,

    )

    fun getSealedEmailMessage(
        id: String = "id",
        owner: String = "owner",
        owners: List<SealedEmailMessage.Owner> = listOf(
            SealedEmailMessage.Owner(
                "owner",
                "issuer",
            ),
        ),
        emailAddressId: String = "emailAddressId",
        version: Int = 1,
        createdAtEpochMs: Double = 1.0,
        updatedAtEpochMs: Double = 1.0,
        sortDateEpochMs: Double = 1.0,
        folderId: String = "folderId",
        previousFolderId: String = "previousFolderId",
        direction: EmailMessageDirection = EmailMessageDirection.INBOUND,
        seen: Boolean = false,
        repliedTo: Boolean = false,
        forwarded: Boolean = false,
        state: EmailMessageState = EmailMessageState.DELIVERED,
        clientRefId: String = "clientRefId",
        sealedData: String = "sealedData",
        encryptionStatus: EmailMessageEncryptionStatus = EmailMessageEncryptionStatus.UNENCRYPTED,
        size: Double = 1.0,
    ) = SealedEmailMessage(
        id = id,
        owner = owner,
        owners = owners,
        emailAddressId = emailAddressId,
        version = version,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        sortDateEpochMs = sortDateEpochMs,
        folderId = folderId,
        previousFolderId = previousFolderId,
        direction = direction,
        seen = seen,
        repliedTo = repliedTo,
        forwarded = forwarded,
        state = state,
        clientRefId = clientRefId,
        rfc822Header = SealedEmailMessage.Rfc822Header(
            algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
            keyId = "keyId",
            plainTextType = "plainText",
            base64EncodedSealedData = sealedData,
        ),
        encryptionStatus = encryptionStatus,
        size = size,
    )

    fun getScheduledDraftMessage(
        draftMessageKey: String,
        emailAddressId: String = "emailAddressId",
        owner: String = "ownerId",
        owners: List<ScheduledDraftMessage.Owner> = listOf(
            ScheduledDraftMessage.Owner(
                "ownerId",
                "issuer",
            ),
        ),
        sendAtEpochMs: Double,
        state: ScheduledDraftMessageState = ScheduledDraftMessageState.SCHEDULED,
        createdAtEpochMs: Double = 1.0,
        updatedAtEpochMs: Double = 1.0,
    ) = ScheduledDraftMessage(
        draftMessageKey = draftMessageKey,
        emailAddressId = emailAddressId,
        owner = owner,
        owners = owners,
        sendAtEpochMs = sendAtEpochMs,
        state = state,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    fun getEmailAddressPublicInfo(
        emailAddress: String = "emailAddress",
        keyId: String = "keyId",
        publicKey: String = "publicKey",
        publicKeyDetails: EmailAddressPublicInfo.PublicKeyDetails = EmailAddressPublicInfo.PublicKeyDetails(
            __typename = "EmailAddressPublicKey",
            EmailAddressPublicKey(
                publicKey = "publicKey",
                keyFormat = KeyFormat.RSA_PUBLIC_KEY,
                algorithm = "algorithm",
            ),
        ),
    ) = EmailAddressPublicInfo(
        emailAddress,
        keyId,
        publicKey,
        publicKeyDetails,
    )

    val unsealedHeaderDetailsWithDateString =
        "{\"bcc\":[],\"to\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"from\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"cc\":" +
            "[{\"emailAddress\":\"foobar@unittest.org\"}],\"replyTo\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"subject\":" +
            "\"testSubject\",\"date\":\"1970-01-01T00:00:00.002Z\",\"hasAttachments\":false}"
    val unsealedHeaderDetailsString =
        "{\"bcc\":[],\"to\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"from\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"cc\":" +
            "[{\"emailAddress\":\"foobar@unittest.org\"}],\"replyTo\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"subject\":" +
            "\"testSubject\",\"hasAttachments\":false}"
    val unsealedHeaderDetailsHasAttachmentsTrueString =
        "{\"bcc\":[],\"to\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"from\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"cc\":" +
            "[],\"replyTo\":[],\"subject\":\"testSubject\",\"date\":\"1970-01-01T00:00:00.002Z\",\"hasAttachments\":true}"
    val unsealedHeaderDetailsHasAttachmentsUnsetString =
        "{\"bcc\":[],\"to\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"from\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"cc\":" +
            "[],\"replyTo\":[],\"subject\":\"testSubject\",\"date\":\"1970-01-01T00:00:00.002Z\"}"

    fun blockEmailAddressMutationResponse(
        status: BlockEmailAddressesBulkUpdateStatus = BlockEmailAddressesBulkUpdateStatus.SUCCESS,
        failedAddresses: List<String> = emptyList(),
        successAddresses: List<String> = emptyList(),
    ): GraphQLResponse<BlockEmailAddressesMutation.Data> {
        return GraphQLResponse<BlockEmailAddressesMutation.Data>(
            BlockEmailAddressesMutation.Data(
                BlockEmailAddressesMutation.BlockEmailAddresses(
                    "BlockAddressesResult",
                    BlockAddressesResult(
                        status,
                        failedAddresses,
                        successAddresses,
                    ),
                ),
            ),
            null,
        )
    }

    fun cancelScheduledDraftMessageResponse(
        id: String = "scheduledDraftId",
    ): GraphQLResponse<CancelScheduledDraftMessageMutation.Data> {
        return GraphQLResponse<CancelScheduledDraftMessageMutation.Data>(
            CancelScheduledDraftMessageMutation.Data(
                id,
            ),
            null,
        )
    }

    fun getEmailAddressQueryResponse(
        emailAddress: EmailAddress = EmailAddress(
            "EmailAddress",
            folders = listOf(
                EmailAddress.Folder(
                    "__typename",
                    getEmailFolder(),
                ),
            ),
            getEmailAddressWithoutFolder(),
        ),
    ): GraphQLResponse<GetEmailAddressQuery.Data> {
        return GraphQLResponse<GetEmailAddressQuery.Data>(
            GetEmailAddressQuery.Data(
                GetEmailAddressQuery.GetEmailAddress(
                    "GetEmailAddress",
                    emailAddress,
                ),
            ),
            null,
        )
    }

    fun checkEmailAddressAvailabilityQueryResponse(
        addresses: List<String> = emptyList(),
    ): GraphQLResponse<CheckEmailAddressAvailabilityQuery.Data> {
        return GraphQLResponse<CheckEmailAddressAvailabilityQuery.Data>(
            CheckEmailAddressAvailabilityQuery.Data(
                CheckEmailAddressAvailabilityQuery.CheckEmailAddressAvailability(addresses),

            ),
            null,
        )
    }

    fun createCustomEmailFolderMutationResponse(
        sealedFolderName: String,
    ): GraphQLResponse<CreateCustomEmailFolderMutation.Data> {
        return GraphQLResponse<CreateCustomEmailFolderMutation.Data>(
            CreateCustomEmailFolderMutation.Data(
                CreateCustomEmailFolderMutation.CreateCustomEmailFolder(
                    "CreateCustomEmailFolder",
                    EmailFolder(
                        id = "folderId",
                        owner = "owner",
                        owners = listOf(
                            EmailFolder.Owner(
                                id = "ownerId",
                                issuer = "issue",
                            ),
                        ),
                        version = 1,
                        createdAtEpochMs = 1.0,
                        updatedAtEpochMs = 1.0,
                        emailAddressId = "emailAddressId",
                        folderName = "folderName",
                        size = 0.0,
                        unseenCount = 0.0,
                        ttl = 1.0,
                        customFolderName = EmailFolder.CustomFolderName(
                            "SealedAttribute",
                            SealedAttribute(
                                algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                                keyId = "keyId",
                                plainTextType = "plainText",
                                base64EncodedSealedData = sealedFolderName,
                            ),
                        ),
                    ),
                ),
            ),
            null,
        )
    }

    fun deleteCustomEmailFolderMutationResponse(
        sealedFolderName: String,
    ): GraphQLResponse<DeleteCustomEmailFolderMutation.Data> {
        return GraphQLResponse<DeleteCustomEmailFolderMutation.Data>(
            DeleteCustomEmailFolderMutation.Data(
                DeleteCustomEmailFolderMutation.DeleteCustomEmailFolder(
                    "DeleteCustomEmailFolder",
                    EmailFolder(
                        id = "folderId",
                        owner = "owner",
                        owners = listOf(
                            EmailFolder.Owner(
                                id = "ownerId",
                                issuer = "issue",
                            ),
                        ),
                        version = 1,
                        createdAtEpochMs = 1.0,
                        updatedAtEpochMs = 1.0,
                        emailAddressId = "emailAddressId",
                        folderName = "folderName",
                        size = 0.0,
                        unseenCount = 0.0,
                        ttl = 1.0,
                        customFolderName = EmailFolder.CustomFolderName(
                            "SealedAttribute",
                            SealedAttribute(
                                algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                                keyId = "keyId",
                                plainTextType = "plainText",
                                base64EncodedSealedData = sealedFolderName,
                            ),
                        ),
                    ),
                ),
            ),
            null,
        )
    }

    fun deleteEmailMessagesMutationResponse(
        ids: List<String> = emptyList(),
    ): GraphQLResponse<DeleteEmailMessagesMutation.Data> {
        return GraphQLResponse<DeleteEmailMessagesMutation.Data>(
            DeleteEmailMessagesMutation.Data(
                ids,
            ),
            null,
        )
    }

    fun getEmailConfigQueryResponse(
        deleteEmailMessagesLimit: Int = 10,
        updateEmailMessagesLimit: Int = 5,
        emailMessageMaxInboundMessageSize: Int = 200,
        emailMessageMaxOutboundMessageSize: Int = 100,
        emailMessageRecipientsLimit: Int = 5,
        encryptedEmailMessageRecipientsLimit: Int = 10,
        prohibitedFileExtensions: List<String> = listOf(".js", ".exe", ".lib"),
    ): GraphQLResponse<GetEmailConfigQuery.Data> {
        return GraphQLResponse<GetEmailConfigQuery.Data>(
            GetEmailConfigQuery.Data(
                GetEmailConfigQuery.GetEmailConfig(
                    "EmailConfigurationData",
                    EmailConfigurationData(
                        deleteEmailMessagesLimit,
                        updateEmailMessagesLimit,
                        emailMessageMaxInboundMessageSize,
                        emailMessageMaxOutboundMessageSize,
                        emailMessageRecipientsLimit,
                        encryptedEmailMessageRecipientsLimit,
                        prohibitedFileExtensions,
                    ),
                ),
            ),
            null,
        )
    }

    fun deleteEmailMessagesForFolderIdMutationResponse(
        folderId: String,
    ): GraphQLResponse<DeleteMessagesByFolderIdMutation.Data> {
        return GraphQLResponse<DeleteMessagesByFolderIdMutation.Data>(
            DeleteMessagesByFolderIdMutation.Data(
                folderId,
            ),
            null,
        )
    }

    fun deprovisionEmailAddressMutationResponse(): GraphQLResponse<DeprovisionEmailAddressMutation.Data> {
        return GraphQLResponse<DeprovisionEmailAddressMutation.Data>(
            DeprovisionEmailAddressMutation.Data(
                DeprovisionEmailAddressMutation.DeprovisionEmailAddress(
                    "DeprovisionEmailAddress",
                    getEmailAddressWithoutFolder(),
                ),
            ),
            null,
        )
    }

    fun getConfiguredEmailDomainsQueryResponse(domains: List<String> = emptyList()): GraphQLResponse<GetConfiguredEmailDomainsQuery.Data> {
        return GraphQLResponse<GetConfiguredEmailDomainsQuery.Data>(
            GetConfiguredEmailDomainsQuery.Data(
                GetConfiguredEmailDomainsQuery.GetConfiguredEmailDomains(
                    domains,
                ),
            ),
            null,
        )
    }

    data class GetEmailAddressBlocklistQueryDataValues(
        val sealedData: String,
        val unsealedData: ByteArray,
        val hashedValue: String,
        val action: BlockedAddressAction,
        val keyId: String = "keyId",
        val emailAddressId: String? = null,
    )
    fun getEmailAddressBlocklistQueryResponse(
        blockedAddressesData: List<GetEmailAddressBlocklistQueryDataValues>,
    ): GraphQLResponse<GetEmailAddressBlocklistQuery.Data> {
        return GraphQLResponse<GetEmailAddressBlocklistQuery.Data>(
            GetEmailAddressBlocklistQuery.Data(
                GetEmailAddressBlocklistQuery.GetEmailAddressBlocklist(
                    "GetEmailAddressBlocklist",
                    GetEmailAddressBlocklistResponse(
                        blockedAddressesData.map {
                            GetEmailAddressBlocklistResponse.BlockedAddress(
                                sealedValue = GetEmailAddressBlocklistResponse.SealedValue(
                                    "SealedAttribute",
                                    sealedAttribute = SealedAttribute(
                                        algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                                        keyId = "keyId",
                                        plainTextType = "plainText",
                                        base64EncodedSealedData = it.sealedData,
                                    ),
                                ),
                                hashedBlockedValue = it.hashedValue,
                                action = it.action,
                                emailAddressId = it.emailAddressId,
                            )
                        },
                    ),
                ),
            ),
            null,
        )
    }

    fun getEmailMessageQueryResponse(sealedData: String): GraphQLResponse<GetEmailMessageQuery.Data> {
        return GraphQLResponse<GetEmailMessageQuery.Data>(
            GetEmailMessageQuery.Data(
                GetEmailMessageQuery.GetEmailMessage(
                    "GetEmailMessage",
                    SealedEmailMessage(
                        id = "id",
                        owner = "owner",
                        owners = listOf(
                            SealedEmailMessage.Owner(
                                "owner",
                                "issuer",
                            ),
                        ),
                        emailAddressId = "emailAddressId",
                        version = 1,
                        createdAtEpochMs = 1.0,
                        updatedAtEpochMs = 1.0,
                        sortDateEpochMs = 1.0,
                        folderId = "folderId",
                        previousFolderId = "previousFolderId",
                        direction = EmailMessageDirection.INBOUND,
                        seen = false,
                        repliedTo = false,
                        forwarded = false,
                        state = EmailMessageState.DELIVERED,
                        clientRefId = "clientRefId",
                        encryptionStatus = EmailMessageEncryptionStatus.UNENCRYPTED,
                        size = 1.0,
                        rfc822Header = SealedEmailMessage.Rfc822Header(
                            algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                            keyId = "keyId",
                            plainTextType = "plainText",
                            base64EncodedSealedData = sealedData,
                        ),
                    ),
                ),
            ),
            null,
        )
    }

    fun getEmailDomainsQueryResponse(domains: List<String>): GraphQLResponse<GetEmailDomainsQuery.Data> {
        return GraphQLResponse<GetEmailDomainsQuery.Data>(
            GetEmailDomainsQuery.Data(
                GetEmailDomainsQuery.GetEmailDomains(
                    domains,
                ),
            ),
            null,
        )
    }

    data class EmailAddressQueryResponseData(
        val emailAddressWithoutFolders: EmailAddressWithoutFolders = getEmailAddressWithoutFolder(),
        val folders: List<EmailAddress.Folder> = listOf(
            EmailAddress.Folder("__typename", getEmailFolder()),
        ),
    )
    fun listEmailAddressesQueryResponse(
        items: List<EmailAddressQueryResponseData>,
        nextToken: String? = null,
    ): GraphQLResponse<ListEmailAddressesQuery.Data> {
        return GraphQLResponse<ListEmailAddressesQuery.Data>(
            ListEmailAddressesQuery.Data(
                ListEmailAddressesQuery.ListEmailAddresses(
                    items.map {
                        ListEmailAddressesQuery.Item(
                            "EmailAddress",
                            EmailAddress(
                                "EmailAddress",
                                it.folders,
                                it.emailAddressWithoutFolders,
                            ),
                        )
                    },
                    nextToken,
                ),
            ),
            null,
        )
    }

    fun listEmailAddressesForSudoIdQueryResponse(
        items: List<EmailAddressQueryResponseData>,
        nextToken: String? = null,
    ): GraphQLResponse<ListEmailAddressesForSudoIdQuery.Data> {
        return GraphQLResponse<ListEmailAddressesForSudoIdQuery.Data>(
            ListEmailAddressesForSudoIdQuery.Data(
                ListEmailAddressesForSudoIdQuery.ListEmailAddressesForSudoId(
                    items.map {
                        ListEmailAddressesForSudoIdQuery.Item(
                            "EmailAddress",
                            EmailAddress(
                                "EmailAddress",
                                it.folders,
                                it.emailAddressWithoutFolders,
                            ),
                        )
                    },
                    nextToken,
                ),
            ),
            null,
        )
    }

    fun listEmailFoldersForEmailAddressIdQueryResponse(
        items: List<EmailAddress.Folder>,
        nextToken: String? = null,
    ): GraphQLResponse<ListEmailFoldersForEmailAddressIdQuery.Data> {
        return GraphQLResponse<ListEmailFoldersForEmailAddressIdQuery.Data>(
            ListEmailFoldersForEmailAddressIdQuery.Data(
                ListEmailFoldersForEmailAddressIdQuery.ListEmailFoldersForEmailAddressId(
                    items.map {
                        ListEmailFoldersForEmailAddressIdQuery.Item(
                            "EmailFolder",
                            it.emailFolder,
                        )
                    },
                    nextToken,
                ),
            ),
            null,
        )
    }

    fun listEmailMessagesForEmailAddressIdQueryResponse(
        items: List<SealedEmailMessage>,
        nextToken: String? = null,
    ): GraphQLResponse<ListEmailMessagesForEmailAddressIdQuery.Data> {
        return GraphQLResponse<ListEmailMessagesForEmailAddressIdQuery.Data>(
            ListEmailMessagesForEmailAddressIdQuery.Data(
                ListEmailMessagesForEmailAddressIdQuery.ListEmailMessagesForEmailAddressId(
                    items.map {
                        ListEmailMessagesForEmailAddressIdQuery.Item(
                            "__typename",
                            it,
                        )
                    },
                    nextToken,
                ),
            ),
            null,
        )
    }

    fun listEmailMessagesForEmailFolderIdQueryResponse(
        items: List<SealedEmailMessage>,
        nextToken: String? = null,
    ): GraphQLResponse<ListEmailMessagesForEmailFolderIdQuery.Data> {
        return GraphQLResponse<ListEmailMessagesForEmailFolderIdQuery.Data>(
            ListEmailMessagesForEmailFolderIdQuery.Data(
                ListEmailMessagesForEmailFolderIdQuery.ListEmailMessagesForEmailFolderId(
                    items.map {
                        ListEmailMessagesForEmailFolderIdQuery.Item(
                            "__typename",
                            it,
                        )
                    },
                    nextToken,
                ),
            ),
            null,
        )
    }

    fun listEmailMessagesQueryResponse(
        items: List<SealedEmailMessage>,
        nextToken: String? = null,
    ): GraphQLResponse<ListEmailMessagesQuery.Data> {
        return GraphQLResponse<ListEmailMessagesQuery.Data>(
            ListEmailMessagesQuery.Data(
                ListEmailMessagesQuery.ListEmailMessages(
                    items.map {
                        ListEmailMessagesQuery.Item(
                            "__typename",
                            it,
                        )
                    },
                    nextToken,
                ),
            ),
            null,
        )
    }

    fun listScheduledDraftMessagesForEmailAddressIdQueryResponse(
        items: List<ScheduledDraftMessage>,
        nextToken: String? = null,
    ): GraphQLResponse<ListScheduledDraftMessagesForEmailAddressIdQuery.Data> {
        return GraphQLResponse<ListScheduledDraftMessagesForEmailAddressIdQuery.Data>(
            ListScheduledDraftMessagesForEmailAddressIdQuery.Data(
                ListScheduledDraftMessagesForEmailAddressIdQuery.ListScheduledDraftMessagesForEmailAddressId(
                    items.map {
                        ListScheduledDraftMessagesForEmailAddressIdQuery.Item(
                            "__typename",
                            it,
                        )
                    },
                    nextToken,
                ),
            ),
            null,
        )
    }

    fun lookupEmailAddressPublicInfoQueryResponse(
        items: List<EmailAddressPublicInfo> = listOf(
            getEmailAddressPublicInfo(),
        ),
    ): GraphQLResponse<LookupEmailAddressesPublicInfoQuery.Data> {
        return GraphQLResponse<LookupEmailAddressesPublicInfoQuery.Data>(
            LookupEmailAddressesPublicInfoQuery.Data(
                LookupEmailAddressesPublicInfoQuery.LookupEmailAddressesPublicInfo(
                    items.map {
                        LookupEmailAddressesPublicInfoQuery.Item(
                            "__typename",
                            it,
                        )
                    },
                ),
            ),
            null,
        )
    }

    fun provisionEmailAddressMutationResponse(
        emailAddress: EmailAddress = EmailAddress(
            "EmailAddress",
            folders = listOf(
                EmailAddress.Folder(
                    "__typename",
                    getEmailFolder(),
                ),
            ),
            getEmailAddressWithoutFolder(),
        ),
    ): GraphQLResponse<ProvisionEmailAddressMutation.Data> {
        return GraphQLResponse<ProvisionEmailAddressMutation.Data>(
            ProvisionEmailAddressMutation.Data(
                ProvisionEmailAddressMutation.ProvisionEmailAddress(
                    "ProvisionEmailAddress",
                    emailAddress,
                ),
            ),
            null,
        )
    }

    fun scheduleSendDraftMessageMutationResponse(
        scheduledDraftMessage: ScheduledDraftMessage,
    ): GraphQLResponse<ScheduleSendDraftMessageMutation.Data> {
        return GraphQLResponse<ScheduleSendDraftMessageMutation.Data>(
            ScheduleSendDraftMessageMutation.Data(
                ScheduleSendDraftMessageMutation.ScheduleSendDraftMessage(
                    "ScheduledDraftMessage",
                    scheduledDraftMessage,
                ),
            ),
            null,
        )
    }

    fun sendEmailMessageMutationResponse(
        id: String,
        createdAtEpochMs: Double,
    ): GraphQLResponse<SendEmailMessageMutation.Data> {
        return GraphQLResponse<SendEmailMessageMutation.Data>(
            SendEmailMessageMutation.Data(
                SendEmailMessageMutation.SendEmailMessageV2(
                    "SendEmailMessageV2",
                    SendEmailMessageResult(
                        id,
                        createdAtEpochMs,
                    ),
                ),
            ),
            null,
        )
    }

    fun sendEncryptedEmailMessageMutationResponse(
        id: String,
        createdAtEpochMs: Double,
    ): GraphQLResponse<SendEncryptedEmailMessageMutation.Data> {
        return GraphQLResponse<SendEncryptedEmailMessageMutation.Data>(
            SendEncryptedEmailMessageMutation.Data(
                SendEncryptedEmailMessageMutation.SendEncryptedEmailMessage(
                    "sendEncryptedEmailMessage",
                    SendEmailMessageResult(
                        id,
                        createdAtEpochMs,
                    ),
                ),
            ),
            null,
        )
    }

    fun unblockEmailAddressesMutationResponse(
        status: BlockEmailAddressesBulkUpdateStatus = BlockEmailAddressesBulkUpdateStatus.SUCCESS,
        failedAddresses: List<String> = emptyList(),
        successAddresses: List<String> = emptyList(),
    ): GraphQLResponse<UnblockEmailAddressesMutation.Data> {
        return GraphQLResponse<UnblockEmailAddressesMutation.Data>(
            UnblockEmailAddressesMutation.Data(
                UnblockEmailAddressesMutation.UnblockEmailAddresses(
                    "UnblockAddressesResult",
                    UnblockAddressesResult(
                        status,
                        failedAddresses,
                        successAddresses,
                    ),
                ),
            ),
            null,
        )
    }

    fun updateCustomEmailFolderMutationResponse(
        emailFolder: EmailFolder = getEmailFolder(),
    ): GraphQLResponse<UpdateCustomEmailFolderMutation.Data> {
        return GraphQLResponse<UpdateCustomEmailFolderMutation.Data>(
            UpdateCustomEmailFolderMutation.Data(
                UpdateCustomEmailFolderMutation.UpdateCustomEmailFolder(
                    "UpdateCustomEmailFolder",
                    emailFolder,
                ),
            ),
            null,
        )
    }

    fun updateEmailAddressMetadataMutationResponse(
        emailAddressId: String,
    ): GraphQLResponse<UpdateEmailAddressMetadataMutation.Data> {
        return GraphQLResponse<UpdateEmailAddressMetadataMutation.Data>(
            UpdateEmailAddressMetadataMutation.Data(
                emailAddressId,
            ),
            null,
        )
    }

    fun updateEmailMessagesMutationResponse(
        status: UpdateEmailMessagesStatus = UpdateEmailMessagesStatus.SUCCESS,
        failedMessages: List<UpdateEmailMessagesResult.FailedMessage> = emptyList(),
        successMessages: List<UpdateEmailMessagesResult.SuccessMessage> = emptyList(),
    ): GraphQLResponse<UpdateEmailMessagesMutation.Data> {
        return GraphQLResponse<UpdateEmailMessagesMutation.Data>(
            UpdateEmailMessagesMutation.Data(
                UpdateEmailMessagesMutation.UpdateEmailMessagesV2(
                    "UpdateEmailMessagesV2",
                    UpdateEmailMessagesResult(
                        status,
                        failedMessages,
                        successMessages,
                    ),
                ),
            ),
            null,
        )
    }
}
