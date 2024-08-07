/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.regions.Regions
import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.util.Base64
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudoemail.appsync.enqueue
import com.sudoplatform.sudoemail.appsync.enqueueFirst
import com.sudoplatform.sudoemail.graphql.BlockEmailAddressesMutation
import com.sudoplatform.sudoemail.graphql.CheckEmailAddressAvailabilityQuery
import com.sudoplatform.sudoemail.graphql.DeleteEmailMessagesMutation
import com.sudoplatform.sudoemail.graphql.DeprovisionEmailAddressMutation
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
import com.sudoplatform.sudoemail.graphql.LookupEmailAddressesPublicInfoQuery
import com.sudoplatform.sudoemail.graphql.ProvisionEmailAddressMutation
import com.sudoplatform.sudoemail.graphql.SendEmailMessageMutation
import com.sudoplatform.sudoemail.graphql.SendEncryptedEmailMessageMutation
import com.sudoplatform.sudoemail.graphql.UnblockEmailAddressesMutation
import com.sudoplatform.sudoemail.graphql.UpdateEmailAddressMetadataMutation
import com.sudoplatform.sudoemail.graphql.UpdateEmailMessagesMutation
import com.sudoplatform.sudoemail.graphql.fragment.SealedEmailMessage
import com.sudoplatform.sudoemail.graphql.type.BlockEmailAddressesBulkUpdateStatus
import com.sudoplatform.sudoemail.graphql.type.BlockedAddressHashAlgorithm
import com.sudoplatform.sudoemail.graphql.type.BlockedEmailAddressInput
import com.sudoplatform.sudoemail.graphql.type.DeleteEmailMessagesInput
import com.sudoplatform.sudoemail.graphql.type.DeprovisionEmailAddressInput
import com.sudoplatform.sudoemail.graphql.type.EmailAddressMetadataUpdateValuesInput
import com.sudoplatform.sudoemail.graphql.type.EmailMessageEncryptionStatus
import com.sudoplatform.sudoemail.graphql.type.EmailMessageUpdateValuesInput
import com.sudoplatform.sudoemail.graphql.type.ProvisionEmailAddressPublicKeyInput
import com.sudoplatform.sudoemail.graphql.type.Rfc822HeaderInput
import com.sudoplatform.sudoemail.graphql.type.S3EmailObjectInput
import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.graphql.type.UnblockEmailAddressesInput
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.keys.KeyPair
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.secure.types.LEGACY_BODY_CONTENT_ID
import com.sudoplatform.sudoemail.secure.types.LEGACY_KEY_EXCHANGE_CONTENT_ID
import com.sudoplatform.sudoemail.secure.types.SecureEmailAttachmentType
import com.sudoplatform.sudoemail.secure.types.SecurePackage
import com.sudoplatform.sudoemail.subscription.EmailMessageSubscriber
import com.sudoplatform.sudoemail.subscription.SubscriptionService
import com.sudoplatform.sudoemail.types.BatchOperationResult
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.ConfigurationData
import com.sudoplatform.sudoemail.types.DeleteEmailMessagesResult
import com.sudoplatform.sudoemail.types.DraftEmailMessageMetadata
import com.sudoplatform.sudoemail.types.DraftEmailMessageWithContent
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailAddressPublicInfo
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.EmailFolder
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.EmailMessageOperationFailureResult
import com.sudoplatform.sudoemail.types.EmailMessageRfc822Data
import com.sudoplatform.sudoemail.types.EmailMessageWithBody
import com.sudoplatform.sudoemail.types.EncryptionStatus
import com.sudoplatform.sudoemail.types.InternetMessageFormatHeader
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.ListOutput
import com.sudoplatform.sudoemail.types.PartialEmailAddress
import com.sudoplatform.sudoemail.types.PartialEmailMessage
import com.sudoplatform.sudoemail.types.PartialResult
import com.sudoplatform.sudoemail.types.SendEmailMessageResult
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudoemail.types.UnsealedBlockedAddress
import com.sudoplatform.sudoemail.types.UnsealedBlockedAddressStatus
import com.sudoplatform.sudoemail.types.UpdatedEmailMessageResult.UpdatedEmailMessageSuccess
import com.sudoplatform.sudoemail.types.inputs.CheckEmailAddressAvailabilityInput
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.DeleteDraftEmailMessagesInput
import com.sudoplatform.sudoemail.types.inputs.GetDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailAddressInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageRfc822DataInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageWithBodyInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesForSudoIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailFoldersForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesInput
import com.sudoplatform.sudoemail.types.inputs.LookupEmailAddressesPublicInfoInput
import com.sudoplatform.sudoemail.types.inputs.ProvisionEmailAddressInput
import com.sudoplatform.sudoemail.types.inputs.SendEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.UpdateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailAddressMetadataInput
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailMessagesInput
import com.sudoplatform.sudoemail.types.toResponseFetcher
import com.sudoplatform.sudoemail.types.transformers.DraftEmailMessageTransformer
import com.sudoplatform.sudoemail.types.transformers.EmailAddressPublicInfoTransformer
import com.sudoplatform.sudoemail.types.transformers.EmailAddressTransformer
import com.sudoplatform.sudoemail.types.transformers.EmailAddressTransformer.toAliasInput
import com.sudoplatform.sudoemail.types.transformers.EmailConfigurationTransformer
import com.sudoplatform.sudoemail.types.transformers.EmailFolderTransformer
import com.sudoplatform.sudoemail.types.transformers.EmailMessageDateRangeTransformer.toEmailMessageDateRangeInput
import com.sudoplatform.sudoemail.types.transformers.EmailMessageTransformer
import com.sudoplatform.sudoemail.types.transformers.Unsealer
import com.sudoplatform.sudoemail.types.transformers.UpdateEmailMessagesResultTransformer
import com.sudoplatform.sudoemail.types.transformers.toDate
import com.sudoplatform.sudoemail.util.EmailAddressParser
import com.sudoplatform.sudoemail.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.util.StringHasher
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudonotification.types.NotificationMetaData
import com.sudoplatform.sudonotification.types.NotificationSchemaEntry
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.exceptions.AuthenticationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.Date
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.zip.GZIPInputStream
import com.sudoplatform.sudoemail.graphql.type.BlockEmailAddressesInput as BlockEmailAddressesRequest
import com.sudoplatform.sudoemail.graphql.type.CheckEmailAddressAvailabilityInput as CheckEmailAddressAvailabilityRequest
import com.sudoplatform.sudoemail.graphql.type.GetEmailAddressBlocklistInput as GetEmailAddressBlocklistRequest
import com.sudoplatform.sudoemail.graphql.type.ListEmailAddressesForSudoIdInput as ListEmailAddressesForSudoIdRequest
import com.sudoplatform.sudoemail.graphql.type.ListEmailAddressesInput as ListEmailAddressesRequest
import com.sudoplatform.sudoemail.graphql.type.ListEmailFoldersForEmailAddressIdInput as ListEmailFoldersForEmailAddressIdRequest
import com.sudoplatform.sudoemail.graphql.type.ListEmailMessagesForEmailAddressIdInput as ListEmailMessagesForEmailAddressIdRequest
import com.sudoplatform.sudoemail.graphql.type.ListEmailMessagesForEmailFolderIdInput as ListEmailMessagesForEmailFolderIdRequest
import com.sudoplatform.sudoemail.graphql.type.ListEmailMessagesInput as ListEmailMessagesRequest
import com.sudoplatform.sudoemail.graphql.type.LookupEmailAddressesPublicInfoInput as LookupEmailAddressesPublicInfoRequest
import com.sudoplatform.sudoemail.graphql.type.ProvisionEmailAddressInput as ProvisionEmailAddressRequest
import com.sudoplatform.sudoemail.graphql.type.SendEmailMessageInput as SendEmailMessageRequest
import com.sudoplatform.sudoemail.graphql.type.SendEncryptedEmailMessageInput as SendEncryptedEmailMessageRequest
import com.sudoplatform.sudoemail.graphql.type.UnblockEmailAddressesInput as UnblockEmailAddressesRequest
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailAddressMetadataInput as UpdateEmailAddressMetadataRequest
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailMessagesInput as UpdateEmailMessagesRequest

/**
 * Default implementation of the [SudoEmailClient] interface.
 *
 * @property context [Context] Application context.
 * @property appSyncClient [AWSAppSyncClient] GraphQL client used to make requests to AWS and call sudo email service API.
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
    private val appSyncClient: AWSAppSyncClient,
    private val sudoUserClient: SudoUserClient,
    private val logger: Logger = Logger(
        LogConstants.SUDOLOG_TAG,
        AndroidUtilsLogDriver(LogLevel.INFO),
    ),
    private val serviceKeyManager: ServiceKeyManager,
    private val emailMessageDataProcessor: EmailMessageDataProcessor,
    private val sealingService: SealingService,
    private val emailCryptoService: EmailCryptoService,
    private val region: String = Regions.US_EAST_1.name,
    private val emailBucket: String,
    private val transientBucket: String,
    private val notificationHandler: SudoEmailNotificationHandler? = null,
    private val s3TransientClient: S3Client = DefaultS3Client(
        context,
        sudoUserClient,
        region,
        transientBucket,
        logger,
    ),
    private val s3EmailClient: S3Client = DefaultS3Client(
        context,
        sudoUserClient,
        region,
        emailBucket,
        logger,
    ),
) : SudoEmailClient {

    companion object {
        /** Maximum limit of number of identifiers that can be deleted per request. */
        private const val ID_REQUEST_LIMIT = 100

        /** Content encoding values for email message data. */
        private const val CRYPTO_CONTENT_ENCODING = "sudoplatform-crypto"
        private const val BINARY_DATA_CONTENT_ENCODING = "sudoplatform-binary-data"
        private const val COMPRESSION_CONTENT_ENCODING = "sudoplatform-compression"

        /** Exception messages */
        private const val UNSEAL_EMAIL_ADDRESS_ERROR_MSG = "Unable to unseal email address data"
        private const val UNSEAL_EMAIL_MSG_ERROR_MSG = "Unable to unseal email message data"
        private const val KEY_GENERATION_ERROR_MSG = "Failed to generate a public key pair"
        private const val KEY_ARCHIVE_ERROR_MSG = "Unable to perform key archive operation"
        private const val NO_EMAIL_ERROR_MSG = "No email address returned"
        private const val INVALID_KEYRING_MSG = "Invalid key ring identifier"
        private const val INVALID_EMAIL_ADDRESS_MSG = "Invalid email address"
        private const val INSUFFICIENT_ENTITLEMENTS_MSG = "Entitlements have been exceeded"
        private const val NO_EMAIL_ID_ERROR_MSG = "No email message identifier returned"
        private const val IN_NETWORK_EMAIL_ADDRESSES_NOT_FOUND_ERROR_MSG =
            "At least one email address does not exist in network"
        private const val INVALID_MESSAGE_CONTENT_MSG = "Invalid email message contents"
        private const val EMAIL_ADDRESS_NOT_FOUND_MSG = "Email address not found"
        private const val EMAIL_ADDRESS_UNAVAILABLE_MSG = "Email address is not available"
        private const val EMAIL_ADDRESS_UNAUTHORIZED_MSG = "Unauthorized email address"
        private const val EMAIL_MESSAGE_NOT_FOUND_MSG = "Email message not found"
        private const val LIMIT_EXCEEDED_ERROR_MSG = "Input cannot exceed $ID_REQUEST_LIMIT"
        private const val INVALID_ARGUMENT_ERROR_MSG = "Invalid input"
        private const val SYMMETRIC_KEY_NOT_FOUND_ERROR_MSG = "Symmetric key not found"
        private const val PUBLIC_KEY_NOT_FOUND_ERROR_MSG = "Public Key not found"
        private const val S3_KEY_ID_ERROR_MSG = "No sealed keyId associated with s3 object"
        private const val S3_ALGORITHM_ERROR_MSG = "No sealed algorithm associated with s3 object"
        private const val S3_NOT_FOUND_ERROR_CODE = "404 Not Found"
        private const val ADDRESS_BLOCKLIST_EMPTY_MSG = "At least one email address must be passed"
        private const val ADDRESS_BLOCKLIST_DUPLICATE_MSG =
            "Duplicate email address found. Please include each address only once"
        private const val KEY_ATTACHMENTS_NOT_FOUND_ERROR_MSG = "Key attachments could not be found"
        private const val BODY_ATTACHMENT_NOT_FOUND_ERROR_MSG =
            "Body attachments could not be found"
        private const val EMAIL_CRYPTO_ERROR_MSG =
            "Unable to perform cryptographic operation on email data"
        private const val SERVICE_QUOTA_EXCEEDED_ERROR_MSG = "Daily message quota limit exceeded"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_NOT_FOUND_ERROR = "Key not found"
        const val DECODE_ERROR = "Could not decode"

        /** Errors returned from the service */
        private const val ERROR_TYPE = "errorType"
        private const val ERROR_INVALID_KEYRING = "InvalidKeyRingId"
        private const val ERROR_INVALID_ARGUMENT = "InvalidArgument"
        private const val ERROR_INVALID_EMAIL = "EmailValidation"
        private const val ERROR_POLICY_FAILED = "PolicyFailed"
        private const val ERROR_INVALID_EMAIL_CONTENTS = "InvalidEmailContents"
        private const val ERROR_UNAUTHORIZED_ADDRESS = "UnauthorizedAddress"
        private const val ERROR_ADDRESS_NOT_FOUND = "AddressNotFound"
        private const val ERROR_ADDRESS_UNAVAILABLE = "AddressUnavailable"
        private const val ERROR_INVALID_DOMAIN = "InvalidEmailDomain"
        private const val ERROR_MESSAGE_NOT_FOUND = "EmailMessageNotFound"
        private const val ERROR_INSUFFICIENT_ENTITLEMENTS = "InsufficientEntitlementsError"
        private const val ERROR_SERVICE_QUOTA_EXCEEDED = "ServiceQuotaExceededError"
    }

    /**
     * Checksum's for each file are generated and are used to create a checksum that is used when
     * publishing to maven central. In order to retry a failed publish without needing to change any
     * functionality, we need a way to generate a different checksum for the source code. We can
     * change the value of this property which will generate a different checksum for publishing
     * and allow us to retry. The value of `version` doesn't need to be kept up-to-date with the
     * version of the code.
     */
    private val version: String = "16.0.2"

    /** This manages the subscriptions to email message creates and deletes */
    private val subscriptions =
        SubscriptionService(appSyncClient, serviceKeyManager, sudoUserClient, logger)

    @Throws(SudoEmailClient.EmailConfigurationException::class)
    override suspend fun getConfigurationData(): ConfigurationData {
        try {
            val query = GetEmailConfigQuery.builder()
                .build()

            val queryResponse = appSyncClient.query(query)
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors()}")
                throw interpretEmailConfigurationError(queryResponse.errors().first())
            }

            val queryResult =
                queryResponse.data()?.emailConfig?.fragments()?.emailConfigurationData()
                    ?: throw SudoEmailClient.EmailConfigurationException.FailedException()
            return EmailConfigurationTransformer.toEntity(queryResult)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is ApolloException -> throw SudoEmailClient.EmailConfigurationException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailConfigurationException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun getSupportedEmailDomains(cachePolicy: CachePolicy): List<String> {
        try {
            val query = GetEmailDomainsQuery.builder().build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(cachePolicy.toResponseFetcher())
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors()}")
                throw interpretEmailDomainError(queryResponse.errors().first())
            }
            return queryResponse.data()?.emailDomains?.domains() ?: emptyList()
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )

                is ApolloException -> throw SudoEmailClient.EmailAddressException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailAddressException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun checkEmailAddressAvailability(input: CheckEmailAddressAvailabilityInput): List<String> {
        try {
            val queryInput = CheckEmailAddressAvailabilityRequest.builder()
                .localParts(input.localParts)
                .domains(input.domains)
                .build()
            val query = CheckEmailAddressAvailabilityQuery.builder()
                .input(queryInput)
                .build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(CachePolicy.REMOTE_ONLY.toResponseFetcher())
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors()}")
                throw interpretEmailAddressError(queryResponse.errors().first())
            }
            return queryResponse.data()?.checkEmailAddressAvailability()?.addresses()
                ?: return emptyList()
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )

                is ApolloException -> throw SudoEmailClient.EmailAddressException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailAddressException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun provisionEmailAddress(input: ProvisionEmailAddressInput): EmailAddress {
        try {
            // Ensure symmetric key has been generated
            val symmetricKeyId = this.serviceKeyManager.getCurrentSymmetricKeyId()
            if (symmetricKeyId == null) {
                this.serviceKeyManager.generateNewCurrentSymmetricKey()
            }

            val keyPair: KeyPair = if (input.keyId != null) {
                val id = input.keyId.toString()
                this.serviceKeyManager.getKeyPairWithId(id) ?: throw KeyNotFoundException(
                    PUBLIC_KEY_NOT_FOUND_ERROR_MSG,
                )
            } else {
                this.serviceKeyManager.generateKeyPair()
            }
            val keyInput = ProvisionEmailAddressPublicKeyInput.builder()
                .keyId(keyPair.keyId)
                .publicKey(Base64.encodeAsString(*keyPair.publicKey))
                .algorithm("RSAEncryptionOAEPAESCBC")
                .build()

            val mutationInput = ProvisionEmailAddressRequest.builder()
                .emailAddress(input.emailAddress)
                .ownershipProofTokens(listOf(input.ownershipProofToken))
                .key(keyInput)
                .alias(input.alias.toAliasInput(serviceKeyManager))
                .build()
            val mutation = ProvisionEmailAddressMutation.builder()
                .input(mutationInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors()}")
                throw interpretEmailAddressError(mutationResponse.errors().first())
            }

            val result =
                mutationResponse.data()?.provisionEmailAddress()?.fragments()?.emailAddress()
            result?.let {
                return EmailAddressTransformer.toEntity(serviceKeyManager, result)
            }
            throw SudoEmailClient.EmailAddressException.ProvisionFailedException(NO_EMAIL_ERROR_MSG)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )

                is ApolloException -> throw SudoEmailClient.EmailAddressException.ProvisionFailedException(
                    cause = e,
                )

                else -> throw interpretEmailAddressException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun deprovisionEmailAddress(id: String): EmailAddress {
        try {
            val mutationInput = DeprovisionEmailAddressInput.builder()
                .emailAddressId(id)
                .build()
            val mutation = DeprovisionEmailAddressMutation.builder()
                .input(mutationInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors()}")
                throw interpretEmailAddressError(mutationResponse.errors().first())
            }

            val result = mutationResponse.data()?.deprovisionEmailAddress()?.fragments()
                ?.emailAddressWithoutFolders()
            result?.let {
                return EmailAddressTransformer.toEntity(serviceKeyManager, result)
            }
            throw SudoEmailClient.EmailAddressException.DeprovisionFailedException(
                NO_EMAIL_ERROR_MSG,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )

                is ApolloException -> throw SudoEmailClient.EmailAddressException.DeprovisionFailedException(
                    cause = e,
                )

                else -> throw interpretEmailAddressException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun updateEmailAddressMetadata(input: UpdateEmailAddressMetadataInput): String {
        try {
            val updateValuesInput = EmailAddressMetadataUpdateValuesInput.builder()
                .alias(input.alias.toAliasInput(serviceKeyManager))
                .build()
            val mutationInput = UpdateEmailAddressMetadataRequest.builder()
                .id(input.id)
                .values(updateValuesInput)
                .build()
            val mutation = UpdateEmailAddressMetadataMutation.builder()
                .input(mutationInput)
                .build()
            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors()}")
                throw interpretEmailAddressError(mutationResponse.errors().first())
            }

            val result = mutationResponse.data()?.updateEmailAddressMetadata()
            return result ?: throw SudoEmailClient.EmailAddressException.UpdateFailedException(
                NO_EMAIL_ERROR_MSG,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )

                is ApolloException -> throw SudoEmailClient.EmailAddressException.UpdateFailedException(
                    cause = e,
                )

                else -> throw interpretEmailAddressException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun getEmailAddress(input: GetEmailAddressInput): EmailAddress? {
        try {
            return this.retrieveEmailAddress(input)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )

                is ApolloException -> throw SudoEmailClient.EmailAddressException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailAddressException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    private suspend fun retrieveEmailAddress(input: GetEmailAddressInput): EmailAddress? {
        val query = GetEmailAddressQuery.builder()
            .id(input.id)
            .build()

        val queryResponse = appSyncClient.query(query)
            .responseFetcher(input.cachePolicy.toResponseFetcher())
            .enqueueFirst()

        if (queryResponse.hasErrors()) {
            logger.error("errors = ${queryResponse.errors()}")
            throw interpretEmailAddressError(queryResponse.errors().first())
        }

        val result = queryResponse.data()?.emailAddress?.fragments()?.emailAddress() ?: return null
        return EmailAddressTransformer.toEntity(serviceKeyManager, result)
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun listEmailAddresses(input: ListEmailAddressesInput): ListAPIResult<EmailAddress, PartialEmailAddress> {
        try {
            val queryInput = ListEmailAddressesRequest.builder()
                .limit(input.limit)
                .nextToken(input.nextToken)
                .build()
            val query = ListEmailAddressesQuery.builder()
                .input(queryInput)
                .build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(input.cachePolicy.toResponseFetcher())
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors()}")
                throw interpretEmailAddressError(queryResponse.errors().first())
            }

            val queryResult = queryResponse.data()?.listEmailAddresses()
            val sealedEmailAddresses = queryResult?.items() ?: emptyList()
            val newNextToken = queryResult?.nextToken()

            val success: MutableList<EmailAddress> = mutableListOf()
            val partials: MutableList<PartialResult<PartialEmailAddress>> = mutableListOf()
            for (sealedEmailAddress in sealedEmailAddresses) {
                try {
                    val unsealedEmailAddress = EmailAddressTransformer
                        .toEntity(serviceKeyManager, sealedEmailAddress.fragments().emailAddress())
                    success.add(unsealedEmailAddress)
                } catch (e: Exception) {
                    val partialEmailAddress = EmailAddressTransformer
                        .toPartialEntity(sealedEmailAddress.fragments().emailAddress())
                    val partialResult = PartialResult(partialEmailAddress, e)
                    partials.add(partialResult)
                }
            }
            if (partials.isNotEmpty()) {
                val listPartialResult =
                    ListAPIResult.ListPartialResult(success, partials, newNextToken)
                return ListAPIResult.Partial(listPartialResult)
            }
            val listSuccessResult = ListAPIResult.ListSuccessResult(success, newNextToken)
            return ListAPIResult.Success(listSuccessResult)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )

                is ApolloException -> throw SudoEmailClient.EmailAddressException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailAddressException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun listEmailAddressesForSudoId(
        input: ListEmailAddressesForSudoIdInput,
    ): ListAPIResult<EmailAddress, PartialEmailAddress> {
        try {
            val queryInput = ListEmailAddressesForSudoIdRequest.builder()
                .sudoId(input.sudoId)
                .limit(input.limit)
                .nextToken(input.nextToken)
                .build()
            val query = ListEmailAddressesForSudoIdQuery.builder()
                .input(queryInput)
                .build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(input.cachePolicy.toResponseFetcher())
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors()}")
                throw interpretEmailAddressError(queryResponse.errors().first())
            }

            val queryResult = queryResponse.data()?.listEmailAddressesForSudoId()
            val sealedEmailAddresses = queryResult?.items() ?: emptyList()
            val newNextToken = queryResult?.nextToken()

            val success: MutableList<EmailAddress> = mutableListOf()
            val partials: MutableList<PartialResult<PartialEmailAddress>> = mutableListOf()
            for (sealedEmailAddress in sealedEmailAddresses) {
                try {
                    val unsealedEmailAddress = EmailAddressTransformer
                        .toEntity(serviceKeyManager, sealedEmailAddress.fragments().emailAddress())
                    success.add(unsealedEmailAddress)
                } catch (e: Exception) {
                    val partialEmailAddress = EmailAddressTransformer
                        .toPartialEntity(sealedEmailAddress.fragments().emailAddress())
                    val partialResult = PartialResult(partialEmailAddress, e)
                    partials.add(partialResult)
                }
            }
            if (partials.isNotEmpty()) {
                val listPartialResult =
                    ListAPIResult.ListPartialResult(success, partials, newNextToken)
                return ListAPIResult.Partial(listPartialResult)
            }
            val listSuccessResult = ListAPIResult.ListSuccessResult(success, newNextToken)
            return ListAPIResult.Success(listSuccessResult)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )

                is ApolloException -> throw SudoEmailClient.EmailAddressException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailAddressException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun lookupEmailAddressesPublicInfo(
        input: LookupEmailAddressesPublicInfoInput,
    ): List<EmailAddressPublicInfo> {
        try {
            val queryInput = LookupEmailAddressesPublicInfoRequest.builder()
                .emailAddresses(input.emailAddresses)
                .build()
            val query = LookupEmailAddressesPublicInfoQuery.builder()
                .input(queryInput)
                .build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(input.cachePolicy.toResponseFetcher())
                .enqueueFirst()
            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors()}")
                throw interpretEmailAddressError(queryResponse.errors().first())
            }

            val queryResult = queryResponse.data()?.lookupEmailAddressesPublicInfo()
            val emailAddressesPublicInfo = queryResult?.items() ?: emptyList()

            return emailAddressesPublicInfo.map { publicInfo ->
                EmailAddressPublicInfoTransformer.toEntity(
                    publicInfo.fragments().emailAddressPublicInfo(),
                )
            }
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(
                    cause = e,
                )

                is ApolloException -> throw SudoEmailClient.EmailAddressException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailAddressException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailFolderException::class)
    override suspend fun listEmailFoldersForEmailAddressId(input: ListEmailFoldersForEmailAddressIdInput): ListOutput<EmailFolder> {
        try {
            val queryInput = ListEmailFoldersForEmailAddressIdRequest.builder()
                .emailAddressId(input.emailAddressId)
                .limit(input.limit)
                .nextToken(input.nextToken)
                .build()
            val query = ListEmailFoldersForEmailAddressIdQuery.builder()
                .input(queryInput)
                .build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(input.cachePolicy.toResponseFetcher())
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors()}")
                throw interpretEmailFolderError(queryResponse.errors().first())
            }

            val result =
                queryResponse.data()?.listEmailFoldersForEmailAddressId() ?: return ListOutput(
                    emptyList(),
                    null,
                )
            val emailFolders = EmailFolderTransformer.toEntity(result.items())
            return ListOutput(emailFolders, result.nextToken())
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailFolderException.AuthenticationException(
                    cause = e,
                )

                is ApolloException -> throw SudoEmailClient.EmailFolderException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailFolderException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun sendEmailMessage(input: SendEmailMessageInput): SendEmailMessageResult {
        val (senderEmailAddressId, emailMessageHeader, body, attachments, inlineAttachments) = input
        try {
            val domains = getSupportedEmailDomains()

            val allRecipients = mutableListOf<EmailMessage.EmailAddress>().apply {
                addAll(emailMessageHeader.to)
                addAll(emailMessageHeader.cc)
                addAll(emailMessageHeader.bcc)
            }.map { it.emailAddress }

            // Identify whether recipients are internal or external based on their domains
            val (internalRecipients, externalRecipients) = allRecipients.partition { recipient ->
                domains.any { domain ->
                    recipient.contains(domain)
                }
            }

            if (internalRecipients.isNotEmpty()) {
                // Lookup public key information for each internal recipient and sender
                val recipientsAndSender = mutableListOf<String>().apply {
                    addAll(internalRecipients)
                    add(emailMessageHeader.from.emailAddress)
                }
                val lookupPublicInfoInput = LookupEmailAddressesPublicInfoInput(
                    emailAddresses = recipientsAndSender,
                )
                val emailAddressesPublicInfo = lookupEmailAddressesPublicInfo(lookupPublicInfoInput)

                // Check whether internal recipient addresses and associated public keys exist in the platform
                val isInNetworkAddresses = internalRecipients.all { recipient ->
                    emailAddressesPublicInfo.any { info -> info.emailAddress == recipient }
                }
                if (!isInNetworkAddresses) {
                    throw SudoEmailClient.EmailMessageException.InNetworkAddressNotFoundException(
                        IN_NETWORK_EMAIL_ADDRESSES_NOT_FOUND_ERROR_MSG,
                    )
                }

                return if (externalRecipients.isEmpty()) {
                    // Process encrypted email message
                    sendInNetworkEmailMessage(
                        senderEmailAddressId,
                        emailMessageHeader,
                        body,
                        attachments,
                        inlineAttachments,
                        emailAddressesPublicInfo,
                    )
                } else {
                    // Process non-encrypted email message
                    sendOutOfNetworkEmailMessage(
                        senderEmailAddressId,
                        emailMessageHeader,
                        body,
                        attachments,
                        inlineAttachments,
                    )
                }
            }
            // Process non-encrypted email message
            return sendOutOfNetworkEmailMessage(
                senderEmailAddressId,
                emailMessageHeader,
                body,
                attachments,
                inlineAttachments,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw handleSendEmailMessageException(e)
        }
    }

    private suspend fun sendInNetworkEmailMessage(
        senderEmailAddressId: String,
        emailMessageHeader: InternetMessageFormatHeader,
        body: String,
        attachments: List<EmailAttachment>,
        inlineAttachments: List<EmailAttachment>,
        emailAddressesPublicInfo: List<EmailAddressPublicInfo>,
    ): SendEmailMessageResult {
        var s3ObjectKey = ""

        try {
            s3ObjectKey = processAndUploadEmailMessage(
                senderEmailAddressId,
                emailMessageHeader,
                body,
                attachments,
                inlineAttachments,
                EncryptionStatus.ENCRYPTED,
                emailAddressesPublicInfo,
            )

            val s3EmailObjectInput = S3EmailObjectInput.builder()
                .key(s3ObjectKey)
                .region(region)
                .bucket(transientBucket)
                .build()
            val rfc822HeaderInput = Rfc822HeaderInput.builder()
                .from(emailMessageHeader.from.toString())
                .to(emailMessageHeader.to.map { it.toString() })
                .cc(emailMessageHeader.cc.map { it.toString() })
                .bcc(emailMessageHeader.bcc.map { it.toString() })
                .replyTo(emailMessageHeader.replyTo.map { it.toString() })
                .subject(emailMessageHeader.subject)
                .hasAttachments(attachments.isNotEmpty() || inlineAttachments.isNotEmpty())
                .build()

            val mutationInput = SendEncryptedEmailMessageRequest.builder()
                .emailAddressId(senderEmailAddressId)
                .message(s3EmailObjectInput)
                .rfc822Header(rfc822HeaderInput)
                .build()
            val mutation = SendEncryptedEmailMessageMutation.builder()
                .input(mutationInput)
                .build()
            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()
            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors()}")
                throw interpretEmailMessageError(mutationResponse.errors().first())
            }

            val result = mutationResponse.data()?.sendEncryptedEmailMessage()
            result?.let {
                return SendEmailMessageResult(
                    it.fragments().sendEmailMessageResult().id(),
                    it.fragments().sendEmailMessageResult().createdAtEpochMs().toDate(),
                )
            }
            throw SudoEmailClient.EmailMessageException.FailedException(NO_EMAIL_ID_ERROR_MSG)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw handleSendEmailMessageException(e)
        } finally {
            try {
                if (s3ObjectKey.isNotBlank()) {
                    s3TransientClient.delete(s3ObjectKey)
                }
            } catch (e: Throwable) {
                logger.error("$e")
            }
        }
    }

    private suspend fun sendOutOfNetworkEmailMessage(
        senderEmailAddressId: String,
        emailMessageHeader: InternetMessageFormatHeader,
        body: String,
        attachments: List<EmailAttachment>,
        inlineAttachments: List<EmailAttachment>,
    ): SendEmailMessageResult {
        var s3ObjectKey = ""

        try {
            s3ObjectKey = processAndUploadEmailMessage(
                senderEmailAddressId,
                emailMessageHeader,
                body,
                attachments,
                inlineAttachments,
                EncryptionStatus.UNENCRYPTED,
            )

            val s3EmailObject = S3EmailObjectInput.builder()
                .key(s3ObjectKey)
                .region(region)
                .bucket(transientBucket)
                .build()
            val mutationInput = SendEmailMessageRequest.builder()
                .emailAddressId(senderEmailAddressId)
                .message(s3EmailObject)
                .build()
            val mutation = SendEmailMessageMutation.builder()
                .input(mutationInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors()}")
                throw interpretEmailMessageError(mutationResponse.errors().first())
            }

            val result = mutationResponse.data()?.sendEmailMessageV2()
            result?.let {
                return SendEmailMessageResult(
                    it.fragments().sendEmailMessageResult().id(),
                    it.fragments().sendEmailMessageResult().createdAtEpochMs().toDate(),
                )
            }
            throw SudoEmailClient.EmailMessageException.FailedException(NO_EMAIL_ID_ERROR_MSG)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw handleSendEmailMessageException(e)
        } finally {
            try {
                if (s3ObjectKey.isNotBlank()) {
                    s3TransientClient.delete(s3ObjectKey)
                }
            } catch (e: Throwable) {
                logger.error("$e")
            }
        }
    }

    private suspend fun processAndUploadEmailMessage(
        senderEmailAddressId: String,
        emailMessageHeader: InternetMessageFormatHeader,
        body: String,
        attachments: List<EmailAttachment>,
        inlineAttachments: List<EmailAttachment>,
        encryptionStatus: EncryptionStatus,
        emailAddressesPublicInfo: List<EmailAddressPublicInfo> = emptyList(),
    ): String {
        val config = getConfigurationData()
        val emailMessageMaxOutboundMessageSize = config.emailMessageMaxOutboundMessageSize

        try {
            val clientRefId = UUID.randomUUID().toString()
            val objectId =
                "${this.constructS3PrefixForEmailAddress(senderEmailAddressId)}/$clientRefId"

            var rfc822Data = emailMessageDataProcessor.encodeToInternetMessageData(
                from = emailMessageHeader.from.toString(),
                to = emailMessageHeader.to.map { it.toString() },
                cc = emailMessageHeader.cc.map { it.toString() },
                bcc = emailMessageHeader.bcc.map { it.toString() },
                subject = emailMessageHeader.subject,
                body,
                attachments,
                inlineAttachments,
                isHtml = true,
                EncryptionStatus.UNENCRYPTED,
            )

            if (encryptionStatus == EncryptionStatus.ENCRYPTED) {
                val encryptedEmailMessage =
                    emailCryptoService.encrypt(rfc822Data, emailAddressesPublicInfo)
                val secureAttachments = encryptedEmailMessage.toList()

                // Encode the RFC 822 data with the secureAttachments
                rfc822Data = emailMessageDataProcessor.encodeToInternetMessageData(
                    from = emailMessageHeader.from.toString(),
                    to = emailMessageHeader.to.map { it.toString() },
                    cc = emailMessageHeader.cc.map { it.toString() },
                    bcc = emailMessageHeader.bcc.map { it.toString() },
                    subject = emailMessageHeader.subject,
                    body,
                    secureAttachments,
                    inlineAttachments,
                    isHtml = false,
                    encryptionStatus = encryptionStatus,
                )
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
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw handleSendEmailMessageException(e)
        }
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun updateEmailMessages(input: UpdateEmailMessagesInput):
        BatchOperationResult<UpdatedEmailMessageSuccess, EmailMessageOperationFailureResult> {
        val idSet = input.ids.toSet()
        try {
            if (idSet.size > ID_REQUEST_LIMIT) {
                throw SudoEmailClient.EmailMessageException.LimitExceededException(
                    LIMIT_EXCEEDED_ERROR_MSG,
                )
            }
            if (idSet.isEmpty()) {
                throw SudoEmailClient.EmailMessageException.InvalidArgumentException(
                    INVALID_ARGUMENT_ERROR_MSG,
                )
            }

            val updateValuesInput = EmailMessageUpdateValuesInput.builder()
                .folderId(input.values.folderId)
                .seen(input.values.seen)
                .build()
            val mutationInput = UpdateEmailMessagesRequest.builder()
                .messageIds(idSet.toList())
                .values(updateValuesInput)
                .build()
            val mutation = UpdateEmailMessagesMutation.builder()
                .input(mutationInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors()}")
                throw interpretEmailMessageError(mutationResponse.errors().first())
            }
            val result = mutationResponse.data()?.updateEmailMessagesV2()?.fragments()
                ?.updateEmailMessagesResult()
                ?: throw SudoEmailClient.EmailMessageException.FailedException(NO_EMAIL_ID_ERROR_MSG)

            return UpdateEmailMessagesResultTransformer.toEntity(result)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMessageException.AuthenticationException(
                    cause = e,
                )

                is ApolloException -> throw SudoEmailClient.EmailMessageException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailMessageException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun deleteEmailMessages(ids: List<String>): BatchOperationResult<String, String> {
        val idSet = ids.toSet()
        val result = executeDeleteEmailMessages(idSet)

        val status = if (result.successIds.size == idSet.size) {
            BatchOperationStatus.SUCCESS
        } else if (result.failureIds.size == idSet.size) {
            BatchOperationStatus.FAILURE
        } else {
            BatchOperationStatus.PARTIAL
        }
        return BatchOperationResult.createSame(status, result.successIds, result.failureIds)
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun deleteEmailMessage(id: String): String? {
        val idSet = setOf(id)
        val result = executeDeleteEmailMessages(idSet)
        if (result.successIds.size == idSet.size) {
            return result.successIds.first()
        }
        return null
    }

    private suspend fun executeDeleteEmailMessages(idSet: Set<String>): DeleteEmailMessagesResult {
        try {
            if (idSet.size > ID_REQUEST_LIMIT) {
                throw SudoEmailClient.EmailMessageException.LimitExceededException(
                    LIMIT_EXCEEDED_ERROR_MSG,
                )
            }
            if (idSet.isEmpty()) {
                throw SudoEmailClient.EmailMessageException.InvalidArgumentException(
                    INVALID_ARGUMENT_ERROR_MSG,
                )
            }

            val mutationInput = DeleteEmailMessagesInput.builder()
                .messageIds(idSet.toList())
                .build()
            val mutation = DeleteEmailMessagesMutation.builder()
                .input(mutationInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.error("errors = ${mutationResponse.errors()}")
                throw interpretEmailMessageError(mutationResponse.errors().first())
            }
            val failureIds = mutationResponse.data()?.deleteEmailMessages()
                ?: throw SudoEmailClient.EmailMessageException.FailedException(NO_EMAIL_ID_ERROR_MSG)
            val successIds = idSet.filter { !failureIds.contains(it) } as MutableList<String>
            return DeleteEmailMessagesResult(successIds, failureIds)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMessageException.AuthenticationException(
                    cause = e,
                )

                is ApolloException -> throw SudoEmailClient.EmailMessageException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailMessageException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun getEmailMessage(input: GetEmailMessageInput): EmailMessage? {
        try {
            val result = retrieveEmailMessage(input) ?: return null
            return EmailMessageTransformer.toEntity(serviceKeyManager, result)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMessageException.AuthenticationException(
                    cause = e,
                )

                is ApolloException -> throw SudoEmailClient.EmailMessageException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailMessageException(e)
            }
        }
    }

    @Deprecated(
        "Use getEmailMessageWithBody instead to retrieve email message data",
        ReplaceWith("getEmailMessageWithBody"),
    )
    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun getEmailMessageRfc822Data(input: GetEmailMessageRfc822DataInput): EmailMessageRfc822Data? {
        try {
            val getEmailMessageInput = GetEmailMessageInput(input.id)
            val emailMessage = retrieveEmailMessage(getEmailMessageInput) ?: return null

            val s3Key = constructS3KeyForEmailMessage(
                input.emailAddressId,
                input.id,
                emailMessage.rfc822Header().keyId(),
            )
            val sealedRfc822Data = s3EmailClient.download(s3Key)
            val rfc822Metadata = s3EmailClient.getObjectMetadata(s3Key)
            val contentEncodingValues = (
                if (rfc822Metadata.contentEncoding != null) {
                    rfc822Metadata.contentEncoding.split(',')
                } else {
                    listOf(CRYPTO_CONTENT_ENCODING, BINARY_DATA_CONTENT_ENCODING)
                }
                ).reversed()
            var decodedBytes = sealedRfc822Data
            for (value in contentEncodingValues) {
                when (value.trim().lowercase()) {
                    COMPRESSION_CONTENT_ENCODING -> {
                        decodedBytes = Base64.decode(decodedBytes)
                        val unzippedInputStream =
                            GZIPInputStream(ByteArrayInputStream(decodedBytes))
                        unzippedInputStream.use {
                            decodedBytes = withContext(Dispatchers.IO) {
                                unzippedInputStream.readBytes()
                            }
                        }
                    }

                    CRYPTO_CONTENT_ENCODING -> {
                        decodedBytes = EmailMessageTransformer.toUnsealedRfc822Data(
                            serviceKeyManager,
                            emailMessage.rfc822Header().keyId(),
                            emailMessage.rfc822Header().algorithm(),
                            decodedBytes,
                        )
                    }

                    BINARY_DATA_CONTENT_ENCODING -> {} // no-op
                    else -> throw SudoEmailClient.EmailMessageException.UnsealingException("Invalid Content-Encoding value $value")
                }
            }
            return EmailMessageRfc822Data(input.id, decodedBytes)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMessageException.AuthenticationException(
                    cause = e,
                )

                is ApolloException -> throw SudoEmailClient.EmailMessageException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailMessageException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun getEmailMessageWithBody(input: GetEmailMessageWithBodyInput): EmailMessageWithBody? {
        try {
            val getEmailMessageInput = GetEmailMessageInput(input.id)
            val emailMessage = retrieveEmailMessage(getEmailMessageInput) ?: return null

            val s3Key = constructS3KeyForEmailMessage(
                input.emailAddressId,
                input.id,
                emailMessage.rfc822Header().keyId(),
            )
            val sealedRfc822Data = s3EmailClient.download(s3Key)
            val rfc822Metadata = s3EmailClient.getObjectMetadata(s3Key)
            val contentEncodingValues = (
                if (rfc822Metadata.contentEncoding != null) {
                    rfc822Metadata.contentEncoding.split(',')
                } else {
                    listOf(CRYPTO_CONTENT_ENCODING, BINARY_DATA_CONTENT_ENCODING)
                }
                ).reversed()
            var decodedBytes = sealedRfc822Data
            for (value in contentEncodingValues) {
                when (value.trim().lowercase()) {
                    COMPRESSION_CONTENT_ENCODING -> {
                        decodedBytes = Base64.decode(decodedBytes)
                        val unzippedInputStream =
                            GZIPInputStream(ByteArrayInputStream(decodedBytes))
                        unzippedInputStream.use {
                            decodedBytes = withContext(Dispatchers.IO) {
                                unzippedInputStream.readBytes()
                            }
                        }
                    }

                    CRYPTO_CONTENT_ENCODING -> {
                        decodedBytes = EmailMessageTransformer.toUnsealedRfc822Data(
                            serviceKeyManager,
                            emailMessage.rfc822Header().keyId(),
                            emailMessage.rfc822Header().algorithm(),
                            decodedBytes,
                        )
                    }

                    BINARY_DATA_CONTENT_ENCODING -> {} // no-op
                    else -> throw SudoEmailClient.EmailMessageException.UnsealingException("Invalid Content-Encoding value $value")
                }
            }
            var parsedMessage = emailMessageDataProcessor.parseInternetMessageData(decodedBytes)
            if (emailMessage.encryptionStatus() == EmailMessageEncryptionStatus.ENCRYPTED) {
                val keyAttachments = parsedMessage.attachments.filter {
                    it.contentId.contains(SecureEmailAttachmentType.KEY_EXCHANGE.contentId) ||
                        it.contentId.contains(LEGACY_KEY_EXCHANGE_CONTENT_ID)
                }
                if (keyAttachments.isEmpty()) {
                    throw SudoEmailClient.EmailMessageException.FailedException(
                        KEY_ATTACHMENTS_NOT_FOUND_ERROR_MSG,
                    )
                }
                val bodyAttachment = parsedMessage.attachments.filter {
                    it.contentId.contains(SecureEmailAttachmentType.BODY.contentId) ||
                        it.contentId.contains(LEGACY_BODY_CONTENT_ID)
                }
                if (bodyAttachment.isEmpty()) {
                    throw SudoEmailClient.EmailMessageException.FailedException(
                        BODY_ATTACHMENT_NOT_FOUND_ERROR_MSG,
                    )
                }
                val securePackage = SecurePackage(keyAttachments.toSet(), bodyAttachment.first())
                val unencryptedMessage = emailCryptoService.decrypt(securePackage)
                parsedMessage =
                    emailMessageDataProcessor.parseInternetMessageData(unencryptedMessage)
            }
            return EmailMessageWithBody(
                input.id,
                parsedMessage.body ?: "",
                parsedMessage.isHtml,
                parsedMessage.attachments,
                parsedMessage.inlineAttachments,
            )
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMessageException.AuthenticationException(
                    cause = e,
                )

                is ApolloException -> throw SudoEmailClient.EmailMessageException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailMessageException(e)
            }
        }
    }

    private suspend fun retrieveEmailMessage(input: GetEmailMessageInput): SealedEmailMessage? {
        val query = GetEmailMessageQuery.builder()
            .id(input.id)
            .build()

        val queryResponse = appSyncClient.query(query)
            .responseFetcher(input.cachePolicy.toResponseFetcher())
            .enqueueFirst()

        if (queryResponse.hasErrors()) {
            logger.error("errors = ${queryResponse.errors()}")
            throw interpretEmailMessageError(queryResponse.errors().first())
        }
        return queryResponse.data()?.emailMessage?.fragments()?.sealedEmailMessage()
    }

    override suspend fun listEmailMessages(input: ListEmailMessagesInput): ListAPIResult<EmailMessage, PartialEmailMessage> {
        try {
            val queryInput = ListEmailMessagesRequest.builder()
                .limit(input.limit)
                .nextToken(input.nextToken)
                .specifiedDateRange(input.dateRange.toEmailMessageDateRangeInput())
                .sortOrder(input.sortOrder.toSortOrderInput(input.sortOrder))
                .includeDeletedMessages(input.includeDeletedMessages)
                .build()

            val query = ListEmailMessagesQuery.builder()
                .input(queryInput)
                .build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(input.cachePolicy.toResponseFetcher())
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors()}")
                throw interpretEmailMessageError(queryResponse.errors().first())
            }

            val queryResult = queryResponse.data()?.listEmailMessages()
            val sealedEmailMessages = queryResult?.items() ?: emptyList()
            val newNextToken = queryResult?.nextToken()

            val success: MutableList<EmailMessage> = mutableListOf()
            val partials: MutableList<PartialResult<PartialEmailMessage>> = mutableListOf()
            for (sealedEmailMessage in sealedEmailMessages) {
                try {
                    val unsealedEmailMessage =
                        EmailMessageTransformer.toEntity(
                            serviceKeyManager,
                            sealedEmailMessage.fragments().sealedEmailMessage(),
                        )
                    success.add(unsealedEmailMessage)
                } catch (e: Exception) {
                    val partialEmailMessage =
                        EmailMessageTransformer.toPartialEntity(
                            sealedEmailMessage.fragments().sealedEmailMessage(),
                        )
                    val partialResult = PartialResult(partialEmailMessage, e)
                    partials.add(partialResult)
                }
            }
            if (partials.isNotEmpty()) {
                val listPartialResult =
                    ListAPIResult.ListPartialResult(success, partials, newNextToken)
                return ListAPIResult.Partial(listPartialResult)
            }
            val listSuccessResult = ListAPIResult.ListSuccessResult(success, newNextToken)
            return ListAPIResult.Success(listSuccessResult)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is ApolloException -> throw SudoEmailClient.EmailMessageException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailMessageException(e)
            }
        }
    }

    override suspend fun listEmailMessagesForEmailAddressId(
        input: ListEmailMessagesForEmailAddressIdInput,
    ): ListAPIResult<EmailMessage, PartialEmailMessage> {
        try {
            val queryInput = ListEmailMessagesForEmailAddressIdRequest.builder()
                .emailAddressId(input.emailAddressId)
                .limit(input.limit)
                .nextToken(input.nextToken)
                .specifiedDateRange(input.dateRange.toEmailMessageDateRangeInput())
                .sortOrder(input.sortOrder.toSortOrderInput(input.sortOrder))
                .includeDeletedMessages(input.includeDeletedMessages)
                .build()

            val query = ListEmailMessagesForEmailAddressIdQuery.builder()
                .input(queryInput)
                .build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(input.cachePolicy.toResponseFetcher())
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors()}")
                throw interpretEmailMessageError(queryResponse.errors().first())
            }

            val queryResult = queryResponse.data()?.listEmailMessagesForEmailAddressId()
            val sealedEmailMessages = queryResult?.items() ?: emptyList()
            val newNextToken = queryResult?.nextToken()

            val success: MutableList<EmailMessage> = mutableListOf()
            val partials: MutableList<PartialResult<PartialEmailMessage>> = mutableListOf()
            for (sealedEmailMessage in sealedEmailMessages) {
                try {
                    val unsealedEmailMessage =
                        EmailMessageTransformer.toEntity(
                            serviceKeyManager,
                            sealedEmailMessage.fragments().sealedEmailMessage(),
                        )
                    success.add(unsealedEmailMessage)
                } catch (e: Exception) {
                    val partialEmailMessage =
                        EmailMessageTransformer.toPartialEntity(
                            sealedEmailMessage.fragments().sealedEmailMessage(),
                        )
                    val partialResult = PartialResult(partialEmailMessage, e)
                    partials.add(partialResult)
                }
            }
            if (partials.isNotEmpty()) {
                val listPartialResult =
                    ListAPIResult.ListPartialResult(success, partials, newNextToken)
                return ListAPIResult.Partial(listPartialResult)
            }
            val listSuccessResult = ListAPIResult.ListSuccessResult(success, newNextToken)
            return ListAPIResult.Success(listSuccessResult)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is ApolloException -> throw SudoEmailClient.EmailMessageException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailMessageException(e)
            }
        }
    }

    override suspend fun listEmailMessagesForEmailFolderId(
        input: ListEmailMessagesForEmailFolderIdInput,
    ): ListAPIResult<EmailMessage, PartialEmailMessage> {
        try {
            val queryInput = ListEmailMessagesForEmailFolderIdRequest.builder()
                .folderId(input.folderId)
                .limit(input.limit)
                .nextToken(input.nextToken)
                .specifiedDateRange(input.dateRange.toEmailMessageDateRangeInput())
                .sortOrder(input.sortOrder.toSortOrderInput(input.sortOrder))
                .includeDeletedMessages(input.includeDeletedMessages)
                .build()

            val query = ListEmailMessagesForEmailFolderIdQuery.builder()
                .input(queryInput)
                .build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(input.cachePolicy.toResponseFetcher())
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.error("errors = ${queryResponse.errors()}")
                throw interpretEmailMessageError(queryResponse.errors().first())
            }

            val queryResult = queryResponse.data()?.listEmailMessagesForEmailFolderId()
            val sealedEmailMessages = queryResult?.items() ?: emptyList()
            val newNextToken = queryResult?.nextToken()

            val success: MutableList<EmailMessage> = mutableListOf()
            val partials: MutableList<PartialResult<PartialEmailMessage>> = mutableListOf()
            for (sealedEmailMessage in sealedEmailMessages) {
                try {
                    val unsealedEmailMessage =
                        EmailMessageTransformer.toEntity(
                            serviceKeyManager,
                            sealedEmailMessage.fragments().sealedEmailMessage(),
                        )
                    success.add(unsealedEmailMessage)
                } catch (e: Exception) {
                    val partialEmailMessage =
                        EmailMessageTransformer.toPartialEntity(
                            sealedEmailMessage.fragments().sealedEmailMessage(),
                        )
                    val partialResult = PartialResult(partialEmailMessage, e)
                    partials.add(partialResult)
                }
            }
            if (partials.isNotEmpty()) {
                val listPartialResult =
                    ListAPIResult.ListPartialResult(success, partials, newNextToken)
                return ListAPIResult.Partial(listPartialResult)
            }
            val listSuccessResult = ListAPIResult.ListSuccessResult(success, newNextToken)
            return ListAPIResult.Success(listSuccessResult)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            when (e) {
                is ApolloException -> throw SudoEmailClient.EmailMessageException.FailedException(
                    cause = e,
                )

                else -> throw interpretEmailMessageException(e)
            }
        }
    }

    @Throws(
        SudoEmailClient.EmailMessageException::class,
        SudoEmailClient.EmailAddressException::class,
    )
    override suspend fun createDraftEmailMessage(input: CreateDraftEmailMessageInput): String {
        throwIfEmailAddressNotFound(input.senderEmailAddressId)

        try {
            return saveDraftEmailMessage(input.rfc822Data, input.senderEmailAddressId, null)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretEmailMessageException(e)
        }
    }

    @Throws(
        SudoEmailClient.EmailMessageException::class,
        SudoEmailClient.EmailAddressException::class,
    )
    override suspend fun updateDraftEmailMessage(input: UpdateDraftEmailMessageInput): String {
        this.getDraftEmailMessage(
            GetDraftEmailMessageInput(input.id, input.senderEmailAddressId),
        )
        try {
            return saveDraftEmailMessage(input.rfc822Data, input.senderEmailAddressId, input.id)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretEmailMessageException(e)
        }
    }

    private suspend fun saveDraftEmailMessage(
        rfc822Data: ByteArray,
        senderEmailAddressId: String,
        id: String? = null,
    ): String {
        val symmetricKeyId = this.serviceKeyManager.getCurrentSymmetricKeyId()
            ?: throw KeyNotFoundException(SYMMETRIC_KEY_NOT_FOUND_ERROR_MSG)

        val draftId = if (id !== null) id else UUID.randomUUID().toString()
        val s3Key = this.constructS3KeyForDraftEmailMessage(senderEmailAddressId, draftId)
        val metadataObject = mapOf(
            "keyId" to symmetricKeyId,
            "algorithm" to SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
        )

        val uploadData = DraftEmailMessageTransformer.toEncryptedAndEncodedRfc822Data(
            this.sealingService,
            rfc822Data,
            symmetricKeyId,
        )
        s3TransientClient.upload(uploadData, s3Key, metadataObject)
        return draftId
    }

    @Throws(
        SudoEmailClient.EmailAddressException::class,
        SudoEmailClient.EmailMessageException::class,
    )
    override suspend fun deleteDraftEmailMessages(
        input: DeleteDraftEmailMessagesInput,
    ): BatchOperationResult<String, EmailMessageOperationFailureResult> {
        val (ids, emailAddressId) = input

        throwIfEmailAddressNotFound(emailAddressId)

        val successIds: MutableList<String> = mutableListOf()
        val failureIds: MutableList<EmailMessageOperationFailureResult> = mutableListOf()
        for (id in ids) {
            try {
                val s3Key = this.constructS3KeyForDraftEmailMessage(emailAddressId, id)
                s3TransientClient.delete(s3Key)
                successIds.add(id)
            } catch (e: Throwable) {
                logger.error("unexpected error $e")
                failureIds.add(EmailMessageOperationFailureResult(id, e.message ?: "Unknown Error"))
            }
        }
        val status = if (ids.isEmpty() || ids.size == successIds.size) {
            BatchOperationStatus.SUCCESS
        } else if (ids.size == failureIds.size) {
            BatchOperationStatus.FAILURE
        } else {
            BatchOperationStatus.PARTIAL
        }
        return BatchOperationResult.createDifferent(status, successIds, failureIds)
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun getDraftEmailMessage(input: GetDraftEmailMessageInput): DraftEmailMessageWithContent {
        throwIfEmailAddressNotFound(input.emailAddressId)

        try {
            return this.retrieveDraftEmailMessage(input)
        } catch (e: Throwable) {
            logger.error(e.message)
            throw interpretEmailMessageException(e)
        }
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun listDraftEmailMessages(): List<DraftEmailMessageWithContent> {
        try {
            val emailAddressIds = mutableListOf<String>()
            var nextToken: String? = null
            do {
                val listInput = ListEmailAddressesInput(nextToken = nextToken)
                val emailAddresses = when (val listResult = listEmailAddresses(listInput)) {
                    is ListAPIResult.Success -> {
                        nextToken = listResult.result.nextToken
                        listResult.result.items
                    }

                    is ListAPIResult.Partial -> {
                        nextToken = listResult.result.nextToken
                        listResult.result.items
                    }
                }
                emailAddressIds.addAll(emailAddresses.map { it.id })
            } while (nextToken != null)

            return emailAddressIds.flatMap { listDraftEmailMessagesForEmailAddressId(it) }
        } catch (e: Throwable) {
            logger.error(e.message)
            throw interpretEmailMessageException(e)
        }
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun listDraftEmailMessagesForEmailAddressId(emailAddressId: String): List<DraftEmailMessageWithContent> {
        throwIfEmailAddressNotFound(emailAddressId)

        try {
            val s3Key = this.constructS3KeyForDraftEmailMessage(emailAddressId)
            val objects = s3TransientClient.list(this.transientBucket, s3Key)
            val draftMessageIds = objects.map {
                it.key.substringAfterLast("/")
            }

            val result: MutableList<DraftEmailMessageWithContent> = mutableListOf()
            draftMessageIds.map {
                val getDraftInput = GetDraftEmailMessageInput(it, emailAddressId)
                val draftEmailMessage = this.retrieveDraftEmailMessage(getDraftInput)
                result.add(draftEmailMessage)
            }
            return result
        } catch (e: Throwable) {
            logger.error(e.message)
            throw interpretEmailMessageException(e)
        }
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    private suspend fun retrieveDraftEmailMessage(input: GetDraftEmailMessageInput): DraftEmailMessageWithContent {
        val keyId: String?
        val updatedAt: Date
        try {
            val s3Key = constructS3KeyForDraftEmailMessage(input.emailAddressId, input.id)

            val metadata = s3TransientClient.getObjectMetadata(s3Key)
            keyId = metadata.userMetadata["keyId"]
                ?: throw SudoEmailClient.EmailMessageException.UnsealingException(
                    S3_KEY_ID_ERROR_MSG,
                )
            metadata.userMetadata["algorithm"]
                ?: throw SudoEmailClient.EmailMessageException.UnsealingException(
                    S3_ALGORITHM_ERROR_MSG,
                )
            updatedAt = metadata.lastModified

            val sealedRfc822Data = s3TransientClient.download(s3Key)
            val unsealedRfc822Data = DraftEmailMessageTransformer.toDecodedAndDecryptedRfc822Data(
                this.sealingService,
                sealedRfc822Data,
                keyId,
            )
            return DraftEmailMessageWithContent(
                id = input.id,
                emailAddressId = input.emailAddressId,
                updatedAt = updatedAt,
                rfc822Data = unsealedRfc822Data,
            )
        } catch (e: Throwable) {
            logger.error(e.message)
            throw interpretEmailMessageException(e)
        }
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun listDraftEmailMessageMetadata(): List<DraftEmailMessageMetadata> {
        try {
            val emailAddressIds = mutableListOf<String>()
            var nextToken: String? = null
            do {
                val listInput = ListEmailAddressesInput(nextToken = nextToken)
                val emailAddresses = when (val listResult = listEmailAddresses(listInput)) {
                    is ListAPIResult.Success -> {
                        nextToken = listResult.result.nextToken
                        listResult.result.items
                    }

                    is ListAPIResult.Partial -> {
                        nextToken = listResult.result.nextToken
                        listResult.result.items
                    }
                }
                emailAddressIds.addAll(emailAddresses.map { it.id })
            } while (nextToken != null)

            val result = emailAddressIds.flatMap { id ->
                val s3Key = this.constructS3KeyForDraftEmailMessage(id)
                val items = s3TransientClient.list(this.transientBucket, s3Key)
                items.map {
                    DraftEmailMessageMetadata(it.key.substringAfterLast("/"), id, it.lastModified)
                }
            }
            return result
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretEmailMessageException(e)
        }
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun listDraftEmailMessageMetadataForEmailAddressId(emailAddressId: String): List<DraftEmailMessageMetadata> {
        throwIfEmailAddressNotFound(emailAddressId)

        try {
            val s3Key = this.constructS3KeyForDraftEmailMessage(emailAddressId)
            val items = s3TransientClient.list(this.transientBucket, s3Key)
            return items.map {
                DraftEmailMessageMetadata(
                    it.key.substringAfterLast("/"),
                    emailAddressId,
                    it.lastModified,
                )
            }
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretEmailMessageException(e)
        }
    }

    override suspend fun blockEmailAddresses(addresses: List<String>): BatchOperationResult<String, String> {
        if (addresses.isEmpty()) {
            throw SudoEmailClient.EmailBlocklistException.InvalidInputException(
                ADDRESS_BLOCKLIST_EMPTY_MSG,
            )
        }

        val symmetricKeyId = this.serviceKeyManager.getCurrentSymmetricKeyId()
            ?: throw KeyNotFoundException(SYMMETRIC_KEY_NOT_FOUND_ERROR_MSG)

        val owner = this.sudoUserClient.getSubject()
            ?: throw AuthenticationException.NotSignedInException()

        val normalizedAddresses = HashSet<String>()
        val hashedBlockedValues = mutableListOf<String>()
        val sealedBlockedValues = mutableListOf<ByteArray>()
        addresses.forEach { address ->
            val normalized = EmailAddressParser.normalize(address)
            if (!normalizedAddresses.add(normalized)) {
                throw SudoEmailClient.EmailBlocklistException.InvalidInputException(
                    ADDRESS_BLOCKLIST_DUPLICATE_MSG,
                )
            }
            sealedBlockedValues.add(
                sealingService.sealString(
                    symmetricKeyId,
                    normalized.toByteArray(),
                ),
            )
            hashedBlockedValues.add(StringHasher.hashString("$owner|$normalized"))
        }

        val blockedAddresses = List(normalizedAddresses.size) { index ->
            BlockedEmailAddressInput
                .builder()
                .hashAlgorithm(BlockedAddressHashAlgorithm.SHA256)
                .hashedBlockedValue(hashedBlockedValues[index])
                .sealedValue(
                    SealedAttributeInput
                        .builder()
                        .keyId(symmetricKeyId)
                        .algorithm(SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString())
                        .plainTextType("string")
                        .base64EncodedSealedData(String(Base64.encode(sealedBlockedValues[index])))
                        .build(),
                )
                .build()
        }

        val blockEmailAddressesInput = BlockEmailAddressesRequest.builder()
            .owner(owner)
            .blockedAddresses(blockedAddresses)
            .build()

        val mutation = BlockEmailAddressesMutation.builder()
            .input(blockEmailAddressesInput)
            .build()

        val response = appSyncClient.mutate(mutation)
            .enqueue()

        if (response.hasErrors()) {
            logger.error("errors = ${response.errors()}")
            throw interpretEmailBlocklistError(response.errors().first())
        }

        val result = response.data()?.blockEmailAddresses()?.fragments()?.blockAddressesResult()

        val status = if (result?.status() == BlockEmailAddressesBulkUpdateStatus.SUCCESS) {
            BatchOperationStatus.SUCCESS
        } else if (result?.status() == BlockEmailAddressesBulkUpdateStatus.FAILED) {
            BatchOperationStatus.FAILURE
        } else {
            BatchOperationStatus.PARTIAL
        }
        val successResult = result?.successAddresses()?.map {
            val index = hashedBlockedValues.indexOf(it)
            addresses[index]
        } ?: emptyList()
        val failureResult = result?.failedAddresses()?.map {
            val index = hashedBlockedValues.indexOf(it)
            addresses[index]
        } ?: emptyList()
        return BatchOperationResult.createSame(status, successResult, failureResult)
    }

    override suspend fun unblockEmailAddresses(addresses: List<String>): BatchOperationResult<String, String> {
        if (addresses.isEmpty()) {
            throw SudoEmailClient.EmailBlocklistException.InvalidInputException(
                ADDRESS_BLOCKLIST_EMPTY_MSG,
            )
        }

        val owner = this.sudoUserClient.getSubject()
            ?: throw AuthenticationException.NotSignedInException()

        val normalizedAddresses = HashSet<String>()
        val hashedBlockedValues = mutableListOf<String>()
        for (address in addresses) {
            val normalized = EmailAddressParser.normalize(address)
            if (!normalizedAddresses.add(normalized)) {
                throw SudoEmailClient.EmailBlocklistException.InvalidInputException(
                    ADDRESS_BLOCKLIST_DUPLICATE_MSG,
                )
            }
            hashedBlockedValues.add(StringHasher.hashString("$owner|$normalized"))
        }

        val unblockEmailAddressesInput = UnblockEmailAddressesRequest.builder()
            .owner(owner)
            .unblockedAddresses(hashedBlockedValues)
            .build()

        val mutation = UnblockEmailAddressesMutation.builder()
            .input(unblockEmailAddressesInput)
            .build()

        val response = appSyncClient.mutate(mutation)
            .enqueue()

        if (response.hasErrors()) {
            logger.error("errors = ${response.errors()}")
            throw interpretEmailBlocklistError(response.errors().first())
        }

        val result = response.data()?.unblockEmailAddresses()?.fragments()?.unblockAddressesResult()

        val status = if (result?.status() == BlockEmailAddressesBulkUpdateStatus.SUCCESS) {
            BatchOperationStatus.SUCCESS
        } else if (result?.status() == BlockEmailAddressesBulkUpdateStatus.FAILED) {
            BatchOperationStatus.FAILURE
        } else {
            BatchOperationStatus.PARTIAL
        }
        val successResult = result?.successAddresses()?.map {
            val index = hashedBlockedValues.indexOf(it)
            addresses[index]
        } ?: emptyList()
        val failureResult = result?.failedAddresses()?.map {
            val index = hashedBlockedValues.indexOf(it)
            addresses[index]
        } ?: emptyList()
        return BatchOperationResult.createSame(status, successResult, failureResult)
    }

    override suspend fun unblockEmailAddressesByHashedValue(hashedValues: List<String>): BatchOperationResult<String, String> {
        if (hashedValues.isEmpty()) {
            throw SudoEmailClient.EmailBlocklistException.InvalidInputException(
                ADDRESS_BLOCKLIST_EMPTY_MSG,
            )
        }

        val owner = this.sudoUserClient.getSubject()
            ?: throw AuthenticationException.NotSignedInException()

        val unblockEmailAddressesInput = UnblockEmailAddressesInput.builder()
            .owner(owner)
            .unblockedAddresses(hashedValues)
            .build()

        val mutation = UnblockEmailAddressesMutation.builder()
            .input(unblockEmailAddressesInput)
            .build()

        val response = appSyncClient.mutate(mutation)
            .enqueue()

        if (response.hasErrors()) {
            logger.error("errors = ${response.errors()}")
            throw interpretEmailBlocklistError(response.errors().first())
        }

        val result = response.data()?.unblockEmailAddresses()?.fragments()?.unblockAddressesResult()

        val status = if (result?.status() == BlockEmailAddressesBulkUpdateStatus.SUCCESS) {
            BatchOperationStatus.SUCCESS
        } else if (result?.status() == BlockEmailAddressesBulkUpdateStatus.FAILED) {
            BatchOperationStatus.FAILURE
        } else {
            BatchOperationStatus.PARTIAL
        }
        val successResult = result?.successAddresses() ?: emptyList()
        val failureResult = result?.failedAddresses() ?: emptyList()
        return BatchOperationResult.createSame(status, successResult, failureResult)
    }

    override suspend fun getEmailAddressBlocklist(): List<UnsealedBlockedAddress> {
        val owner = this.sudoUserClient.getSubject()
            ?: throw AuthenticationException.NotSignedInException()

        val getBlocklistInput = GetEmailAddressBlocklistRequest.builder()
            .owner(owner)
            .build()

        val query = GetEmailAddressBlocklistQuery.builder()
            .input(getBlocklistInput)
            .build()

        val response = appSyncClient.query(query)
            .responseFetcher(CachePolicy.REMOTE_ONLY.toResponseFetcher())
            .enqueueFirst()

        if (response.hasErrors()) {
            logger.error("errors = ${response.errors()}")
            throw interpretEmailBlocklistError(response.errors().first())
        }

        val blockedAddresses =
            response.data()?.emailAddressBlocklist?.fragments()?.emailAddressBlocklistResponse?.blockedAddresses()

        if (blockedAddresses.isNullOrEmpty()) {
            return emptyList()
        }

        val unsealedBlockedAddresses = blockedAddresses.map {
            val hashedValue = it.hashedBlockedValue()
            var unsealedAddress = ""
            if (serviceKeyManager.symmetricKeyExists(
                    it.sealedValue().fragments().sealedAttribute().keyId(),
                )
            ) {
                try {
                    unsealedAddress = sealingService.unsealString(
                        it.sealedValue().fragments().sealedAttribute().keyId(),
                        Base64.decode(
                            it.sealedValue().fragments().sealedAttribute()
                                .base64EncodedSealedData(),
                        ),
                    ).decodeToString()
                } catch (e: Throwable) {
                    UnsealedBlockedAddress(
                        address = unsealedAddress,
                        hashedBlockedValue = hashedValue,
                        status = UnsealedBlockedAddressStatus.Failed(
                            SudoEmailClient.EmailBlocklistException.FailedException(DECODE_ERROR),
                        ),
                    )
                }
                UnsealedBlockedAddress(
                    address = unsealedAddress,
                    hashedBlockedValue = hashedValue,
                    status = UnsealedBlockedAddressStatus.Completed,
                )
            } else {
                UnsealedBlockedAddress(
                    address = unsealedAddress,
                    hashedBlockedValue = hashedValue,
                    status = UnsealedBlockedAddressStatus.Failed(
                        KeyNotFoundException(
                            KEY_NOT_FOUND_ERROR,
                        ),
                    ),
                )
            }
        }

        return unsealedBlockedAddresses.toList()
    }

    @Throws(SudoEmailClient.EmailCryptographicKeysException::class)
    override suspend fun importKeys(archiveData: ByteArray) {
        if (archiveData.isEmpty()) {
            throw SudoEmailClient.EmailCryptographicKeysException.SecureKeyArchiveException(
                INVALID_ARGUMENT_ERROR_MSG,
            )
        }
        try {
            serviceKeyManager.importKeys(archiveData)
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretEmailMessageException(e)
        }
    }

    @Throws(SudoEmailClient.EmailCryptographicKeysException::class)
    override suspend fun exportKeys(): ByteArray {
        try {
            return serviceKeyManager.exportKeys()
        } catch (e: Throwable) {
            logger.error("unexpected error $e")
            throw interpretEmailMessageException(e)
        }
    }

    override suspend fun subscribeToEmailMessages(id: String, subscriber: EmailMessageSubscriber) {
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

    /** Private Methods */

    private fun constructS3PrefixForEmailAddress(emailAddressId: String): String {
        return "email/$emailAddressId"
    }

    private fun constructS3KeyForEmailMessage(
        emailAddressId: String,
        emailMessageId: String,
        keyId: String,
    ): String {
        val keyPrefix = constructS3PrefixForEmailAddress(emailAddressId)
        return "$keyPrefix/$emailMessageId-$keyId"
    }

    private fun constructS3KeyForDraftEmailMessage(
        emailAddressId: String,
        draftEmailMessageId: String = "",
    ): String {
        val keyPrefix = constructS3PrefixForEmailAddress(emailAddressId)
        val keySuffix = if (draftEmailMessageId.isNotEmpty()) "/$draftEmailMessageId" else ""
        return "$keyPrefix/draft$keySuffix"
    }

    private suspend fun throwIfEmailAddressNotFound(emailAddressId: String) {
        if (this.retrieveEmailAddress(GetEmailAddressInput(emailAddressId)) == null) {
            throw SudoEmailClient.EmailAddressException.EmailAddressNotFoundException(
                EMAIL_ADDRESS_NOT_FOUND_MSG,
            )
        }
    }

    private fun handleSendEmailMessageException(e: Throwable): Throwable {
        return when (e) {
            is NotAuthorizedException -> SudoEmailClient.EmailMessageException.AuthenticationException(
                cause = e,
            )

            is ApolloException -> SudoEmailClient.EmailMessageException.SendFailedException(cause = e)
            else -> interpretEmailMessageException(e)
        }
    }

    private fun interpretEmailConfigurationError(e: Error): SudoEmailClient.EmailConfigurationException {
        return SudoEmailClient.EmailConfigurationException.FailedException(e.toString())
    }

    private fun interpretEmailConfigurationException(e: Throwable): Throwable {
        return when (e) {
            is CancellationException,
            is SudoEmailClient.EmailConfigurationException,
            -> e

            else -> SudoEmailClient.EmailConfigurationException.UnknownException(e)
        }
    }

    private fun interpretEmailDomainError(e: Error): SudoEmailClient.EmailAddressException {
        return SudoEmailClient.EmailAddressException.FailedException(e.toString())
    }

    private fun interpretEmailAddressError(e: Error): SudoEmailClient.EmailAddressException {
        val error = e.customAttributes()[ERROR_TYPE]?.toString() ?: ""
        if (error.contains(ERROR_INVALID_KEYRING)) {
            return SudoEmailClient.EmailAddressException.PublicKeyException(INVALID_KEYRING_MSG)
        } else if (error.contains(ERROR_INVALID_EMAIL) || error.contains(ERROR_INVALID_DOMAIN)) {
            return SudoEmailClient.EmailAddressException.InvalidEmailAddressException(
                INVALID_EMAIL_ADDRESS_MSG,
            )
        } else if (error.contains(ERROR_ADDRESS_NOT_FOUND)) {
            return SudoEmailClient.EmailAddressException.EmailAddressNotFoundException(
                EMAIL_ADDRESS_NOT_FOUND_MSG,
            )
        } else if (error.contains(ERROR_ADDRESS_UNAVAILABLE)) {
            return SudoEmailClient.EmailAddressException.UnavailableEmailAddressException(
                EMAIL_ADDRESS_UNAVAILABLE_MSG,
            )
        } else if (error.contains(ERROR_UNAUTHORIZED_ADDRESS)) {
            return SudoEmailClient.EmailAddressException.UnauthorizedEmailAddressException(
                EMAIL_ADDRESS_UNAUTHORIZED_MSG,
            )
        } else if (error.contains(ERROR_INSUFFICIENT_ENTITLEMENTS) || error.contains(
                ERROR_POLICY_FAILED,
            )
        ) {
            return SudoEmailClient.EmailAddressException.InsufficientEntitlementsException(
                INSUFFICIENT_ENTITLEMENTS_MSG,
            )
        }
        return SudoEmailClient.EmailAddressException.FailedException(e.toString())
    }

    private fun interpretEmailAddressException(e: Throwable): Throwable {
        return when (e) {
            is CancellationException,
            is SudoEmailClient.EmailAddressException,
            is KeyNotFoundException,
            -> e

            is Unsealer.UnsealerException ->
                SudoEmailClient.EmailAddressException.UnsealingException(
                    UNSEAL_EMAIL_ADDRESS_ERROR_MSG,
                    e,
                )

            is DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException ->
                SudoEmailClient.EmailAddressException.PublicKeyException(KEY_GENERATION_ERROR_MSG)

            else -> SudoEmailClient.EmailAddressException.UnknownException(e)
        }
    }

    private fun interpretEmailFolderError(e: Error): SudoEmailClient.EmailFolderException {
        return SudoEmailClient.EmailFolderException.FailedException(e.toString())
    }

    private fun interpretEmailFolderException(e: Throwable): Throwable {
        return when (e) {
            is CancellationException,
            is SudoEmailClient.EmailFolderException,
            -> e

            else -> SudoEmailClient.EmailFolderException.UnknownException(e)
        }
    }

    private fun interpretEmailMessageError(e: Error): SudoEmailClient.EmailMessageException {
        val error = e.customAttributes()[ERROR_TYPE]?.toString() ?: ""
        if (error.contains(ERROR_INVALID_EMAIL_CONTENTS)) {
            return SudoEmailClient.EmailMessageException.InvalidMessageContentException(
                INVALID_MESSAGE_CONTENT_MSG,
            )
        } else if (error.contains(ERROR_INVALID_ARGUMENT)) {
            return SudoEmailClient.EmailMessageException.InvalidArgumentException(
                INVALID_ARGUMENT_ERROR_MSG,
            )
        } else if (error.contains(ERROR_UNAUTHORIZED_ADDRESS)) {
            return SudoEmailClient.EmailMessageException.UnauthorizedAddressException(
                EMAIL_ADDRESS_UNAUTHORIZED_MSG,
            )
        } else if (error.contains(ERROR_MESSAGE_NOT_FOUND)) {
            return SudoEmailClient.EmailMessageException.EmailMessageNotFoundException(
                EMAIL_MESSAGE_NOT_FOUND_MSG,
            )
        } else if (error.contains(ERROR_SERVICE_QUOTA_EXCEEDED)) {
            return SudoEmailClient.EmailMessageException.LimitExceededException(
                SERVICE_QUOTA_EXCEEDED_ERROR_MSG,
            )
        }
        return SudoEmailClient.EmailMessageException.FailedException(e.toString())
    }

    private fun interpretEmailMessageException(e: Throwable): Throwable {
        return when (e) {
            is CancellationException,
            is SudoEmailClient.EmailMessageException,
            -> e

            is Unsealer.UnsealerException ->
                SudoEmailClient.EmailMessageException.UnsealingException(
                    UNSEAL_EMAIL_MSG_ERROR_MSG,
                    e,
                )

            is DeviceKeyManager.DeviceKeyManagerException ->
                SudoEmailClient.EmailCryptographicKeysException.SecureKeyArchiveException(
                    KEY_ARCHIVE_ERROR_MSG,
                    e,
                )

            is EmailCryptoService.EmailCryptoServiceException ->
                SudoEmailClient.EmailMessageException.FailedException(
                    EMAIL_CRYPTO_ERROR_MSG,
                    e,
                )

            is AmazonS3Exception -> {
                if (e.errorCode == S3_NOT_FOUND_ERROR_CODE) {
                    SudoEmailClient.EmailMessageException.EmailMessageNotFoundException()
                } else {
                    e
                }
            }

            else -> SudoEmailClient.EmailMessageException.UnknownException(e)
        }
    }

    private fun interpretEmailBlocklistError(e: Error): SudoEmailClient.EmailBlocklistException {
        return SudoEmailClient.EmailBlocklistException.FailedException(e.toString())
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
