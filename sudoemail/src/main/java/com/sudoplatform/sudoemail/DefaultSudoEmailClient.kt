/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.regions.Regions
import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudoemail.appsync.enqueue
import com.sudoplatform.sudoemail.appsync.enqueueFirst
import com.sudoplatform.sudoemail.graphql.CheckEmailAddressAvailabilityQuery
import com.sudoplatform.sudoemail.graphql.DeleteEmailMessageMutation
import com.sudoplatform.sudoemail.graphql.DeprovisionEmailAddressMutation
import com.sudoplatform.sudoemail.graphql.GetEmailAddressQuery
import com.sudoplatform.sudoemail.graphql.GetEmailDomainsQuery
import com.sudoplatform.sudoemail.graphql.GetEmailMessageQuery
import com.sudoplatform.sudoemail.graphql.ListEmailAddressesQuery
import com.sudoplatform.sudoemail.graphql.ListEmailMessagesQuery
import com.sudoplatform.sudoemail.graphql.ProvisionEmailAddressMutation
import com.sudoplatform.sudoemail.graphql.SendEmailMessageMutation
import com.sudoplatform.sudoemail.graphql.type.CheckEmailAddressAvailabilityInput
import com.sudoplatform.sudoemail.graphql.type.DeleteEmailMessageInput
import com.sudoplatform.sudoemail.graphql.type.DeprovisionEmailAddressInput
import com.sudoplatform.sudoemail.graphql.type.ProvisionEmailAddressInput
import com.sudoplatform.sudoemail.graphql.type.ListEmailAddressesInput
import com.sudoplatform.sudoemail.graphql.type.ListEmailMessagesInput
import com.sudoplatform.sudoemail.graphql.type.S3EmailObjectInput
import com.sudoplatform.sudoemail.graphql.type.SendEmailMessageInput
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.keys.PublicKeyService
import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudoemail.subscription.EmailMessageSubscriber
import com.sudoplatform.sudoemail.subscription.EmailMessageSubscriptionService
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.ListOutput
import com.sudoplatform.sudoemail.types.inputs.filters.EmailAddressFilter
import com.sudoplatform.sudoemail.types.inputs.filters.EmailMessageFilter
import com.sudoplatform.sudoemail.types.toResponseFetcher
import com.sudoplatform.sudoemail.types.transformers.EmailAddressTransformer
import com.sudoplatform.sudoemail.types.transformers.EmailMessageTransformer
import com.sudoplatform.sudoemail.types.transformers.Unsealer
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudoprofiles.DefaultS3Client
import com.sudoplatform.sudoprofiles.S3Client
import com.sudoplatform.sudoprofiles.Sudo
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudouser.SudoUserClient
import java.util.UUID
import java.util.concurrent.CancellationException

/**
 * Default implementation of the [SudoEmailClient] interface.
 *
 * @property context Application context.
 * @property appSyncClient GraphQL client used to make requests to AWS and call sudo email service API.
 * @property sudoUserClient The [SudoUserClient] used to determine if a user is signed in and gain access to the user owner ID.
 * @property sudoProfilesClient The [SudoProfilesClient] used to perform ownership proof lifecycle operations.
 * @property logger Errors and warnings will be logged here.
 * @property deviceKeyManager On device management of key storage.
 * @property publicKeyService Service that handles registering public keys with the backend.
 * @property region The AWS region.
 * @property identityBucket The S3 Bucket for objects belonging to this identity.
 * @property transientBucket The S3 Bucket for temporary objects.
 *
 * @since 2020-08-04
 */
internal class DefaultSudoEmailClient(
    private val context: Context,
    private val appSyncClient: AWSAppSyncClient,
    private val sudoUserClient: SudoUserClient,
    private val sudoProfilesClient: SudoProfilesClient,
    private val logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO)),
    private val deviceKeyManager: DeviceKeyManager,
    private val publicKeyService: PublicKeyService,
    private val region: String = Regions.US_EAST_1.name,
    private val identityBucket: String,
    private val transientBucket: String,
    @VisibleForTesting
    private val s3TransientClient: S3Client = DefaultS3Client(context, sudoUserClient, region, transientBucket, logger),
    @VisibleForTesting
    private val s3IdentityClient: S3Client = DefaultS3Client(context, sudoUserClient, region, identityBucket, logger)
) : SudoEmailClient {

    companion object {
        /** Exception messages */
        private const val UNSEAL_EMAIL_ERROR_MSG = "Unable to unseal email message data"
        private const val KEY_RETRIEVAL_ERROR_MSG = "Failed to retrieve a public key pair"
        private const val NO_EMAIL_ERROR_MSG = "No email address returned"
        private const val INVALID_KEYRING_MSG = "Invalid key ring identifier"
        private const val INVALID_EMAIL_ADDRESS_MSG = "Invalid email address"
        private const val INSUFFICIENT_ENTITLEMENTS_MSG = "Entitlements have been exceeded"
        private const val NO_EMAIL_ID_ERROR_MSG = "No email message identifier returned"
        private const val INVALID_MESSAGE_CONTENT_MSG = "Invalid email message contents"
        private const val EMAIL_ADDRESS_NOT_FOUND_MSG = "Email address not found"
        private const val EMAIL_ADDRESS_UNAVAILABLE_MSG = "Email address is not available"
        private const val EMAIL_ADDRESS_UNAUTHORIZED_MSG = "Unauthorized email address"
        private const val EMAIL_MESSAGE_NOT_FOUND_MSG = "Email message not found"
        private const val SUDO_NOT_FOUND_MSG = "Sudo identifier not provided"

        /** Errors returned from the service */
        private const val ERROR_TYPE = "errorType"
        private const val SERVICE_ERROR = "ServiceError"
        private const val ERROR_INVALID_KEYRING = "InvalidKeyRingId"
        private const val ERROR_INVALID_EMAIL = "EmailValidation"
        private const val ERROR_POLICY_FAILED = "PolicyFailed"
        private const val ERROR_INVALID_EMAIL_CONTENTS = "InvalidEmailContents"
        private const val ERROR_UNAUTHORIZED_ADDRESS = "UnauthorizedAddress"
        private const val ERROR_ADDRESS_NOT_FOUND = "AddressNotFound"
        private const val ERROR_ADDRESS_UNAVAILABLE = "AddressUnavailable"
        private const val ERROR_INVALID_DOMAIN = "InvalidEmailDomain"
        private const val ERROR_MESSAGE_NOT_FOUND = "EmailMessageNotFound"
        private const val ERROR_INSUFFICIENT_ENTITLEMENTS = "InsufficientEntitlementsError"
    }

    /** This manages the subscriptions to email message creates and deletes */
    private val emailMessageSubscriptions = EmailMessageSubscriptionService(appSyncClient, deviceKeyManager, sudoUserClient, logger)

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun provisionEmailAddress(emailAddress: String, sudoId: String): EmailAddress {
        try {
            // Ensure there is a current key in the key ring so the email address can be sealed
            ensurePublicKeyIsRegistered()

            // Retrieve the ownership proof used to map a Sudo to an email address
            val ownerProof = getOwnershipProof(sudoId)

            val mutationInput = ProvisionEmailAddressInput.builder()
                .emailAddress(emailAddress)
                .ownershipProofTokens(listOf(ownerProof))
                .keyRingId(deviceKeyManager.getKeyRingId())
                .build()
            val mutation = ProvisionEmailAddressMutation.builder()
                .input(mutationInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretEmailAddressError(mutationResponse.errors().first())
            }

            val result = mutationResponse.data()?.provisionEmailAddress()
            result?.let {
                return EmailAddressTransformer.toEntityFromProvisionEmailAddressMutationResult(result)
            }
            throw SudoEmailClient.EmailAddressException.ProvisionFailedException(NO_EMAIL_ERROR_MSG)
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(cause = e)
                is ApolloException -> throw SudoEmailClient.EmailAddressException.ProvisionFailedException(cause = e)
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
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretEmailAddressError(mutationResponse.errors().first())
            }

            val result = mutationResponse.data()?.deprovisionEmailAddress()
            result?.let {
                return EmailAddressTransformer.toEntityFromDeprovisionEmailAddressMutationResult(result)
            }
            throw SudoEmailClient.EmailAddressException.DeprovisionFailedException(NO_EMAIL_ERROR_MSG)
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(cause = e)
                is ApolloException -> throw SudoEmailClient.EmailAddressException.DeprovisionFailedException(cause = e)
                else -> throw interpretEmailAddressException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun sendEmailMessage(rfc822Data: ByteArray, senderEmailAddressId: String): String {
        var s3ObjectKey = ""
        try {
            val clientRefId = UUID.randomUUID().toString()

            s3ObjectKey = s3TransientClient.upload(rfc822Data, clientRefId)

            val s3EmailObject = S3EmailObjectInput.builder()
                .key(s3ObjectKey)
                .region(region)
                .bucket(transientBucket)
                .build()

            val mutationInput = SendEmailMessageInput.builder()
                .emailAddressId(senderEmailAddressId)
                .clientRefId(clientRefId)
                .message(s3EmailObject)
                .build()
            val mutation = SendEmailMessageMutation.builder()
                .input(mutationInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretEmailMessageError(mutationResponse.errors().first())
            }

            return mutationResponse.data()?.sendEmailMessage()
                ?: throw SudoEmailClient.EmailMessageException.FailedException(NO_EMAIL_ID_ERROR_MSG)
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMessageException.AuthenticationException(cause = e)
                is ApolloException -> throw SudoEmailClient.EmailMessageException.SendFailedException(cause = e)
                else -> throw interpretEmailMessageException(e)
            }
        } finally {
            try {
                if (s3ObjectKey.isNotBlank()) {
                    s3TransientClient.delete(s3ObjectKey)
                }
            } catch (e: Throwable) {
                logger.warning("$e")
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
                logger.warning("errors = ${queryResponse.errors()}")
                throw interpretEmailDomainError(queryResponse.errors().first())
            }
            logger.verbose("${queryResponse.data()?.emailDomains?.domains()?.size ?: 0} domains returned")

            return queryResponse.data()?.emailDomains?.domains() ?: emptyList()
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(cause = e)
                is ApolloException -> throw SudoEmailClient.EmailAddressException.FailedException(cause = e)
                else -> throw interpretEmailAddressException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun getEmailAddress(id: String, cachePolicy: CachePolicy): EmailAddress? {
        try {
            val query = GetEmailAddressQuery.builder()
                .id(id)
                .build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(cachePolicy.toResponseFetcher())
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors()}")
                throw interpretEmailAddressError(queryResponse.errors().first())
            }

            val result = queryResponse.data()?.emailAddress ?: return null
            return EmailAddressTransformer.toEntityFromGetEmailAddressQueryResult(result)
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(cause = e)
                is ApolloException -> throw SudoEmailClient.EmailAddressException.FailedException(cause = e)
                else -> throw interpretEmailAddressException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailAddressException::class)
    override suspend fun listEmailAddresses(
        sudoId: String?,
        limit: Int,
        nextToken: String?,
        cachePolicy: CachePolicy,
        filter: () -> EmailAddressFilter?
    ): ListOutput<EmailAddress> {
        try {
            val filters = filter.invoke()
            val queryInput = ListEmailAddressesInput.builder()
                .limit(limit)
                .nextToken(nextToken)
                .filter(EmailAddressTransformer.toGraphQLFilter(filters))
                .sudoId(sudoId)
                .build()

            val query = ListEmailAddressesQuery.builder()
                .input(queryInput)
                .build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(cachePolicy.toResponseFetcher())
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors()}")
                throw interpretEmailAddressError(queryResponse.errors().first())
            }

            val result = queryResponse.data()?.listEmailAddresses() ?: return ListOutput(emptyList(), null)
            val emailAddresses = EmailAddressTransformer.toEntityFromListEmailAddressesQueryResult(result.items())
            return ListOutput(emailAddresses, result.nextToken())
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(cause = e)
                is ApolloException -> throw SudoEmailClient.EmailAddressException.FailedException(cause = e)
                else -> throw interpretEmailAddressException(e)
            }
        }
    }

    @Throws(SudoEmailClient.EmailMessageException::class)
    override suspend fun getEmailMessage(id: String, cachePolicy: CachePolicy): EmailMessage? {
        try {
            val keyPairResult = publicKeyService.getCurrentKeyPair(PublicKeyService.MissingKeyPolicy.GENERATE_IF_MISSING)
                ?: throw SudoEmailClient.EmailMessageException.PublicKeyException(KEY_RETRIEVAL_ERROR_MSG)
            val keyId = keyPairResult.keyId

            val messageId = "$id-$keyId"
            val query = GetEmailMessageQuery.builder()
                .id(messageId)
                .build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(cachePolicy.toResponseFetcher())
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors()}")
                throw interpretEmailMessageError(queryResponse.errors().first())
            }

            val result = queryResponse.data()?.emailMessage ?: return null
            return EmailMessageTransformer.toEntityFromGetEmailMessageQueryResult(deviceKeyManager, result)
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMessageException.AuthenticationException(cause = e)
                is ApolloException -> throw SudoEmailClient.EmailMessageException.FailedException(cause = e)
                else -> throw interpretEmailMessageException(e)
            }
        }
    }

    override suspend fun getEmailMessageRfc822Data(id: String, cachePolicy: CachePolicy): ByteArray? {
        try {
            val emailMessage = getEmailMessage(id, cachePolicy) ?: return null
            val s3Key = makeS3Key(emailMessage.id)
            val sealedRfc822Data = s3IdentityClient.download(s3Key)
            return EmailMessageTransformer.toUnsealedRfc822Data(
                deviceKeyManager,
                emailMessage.keyId,
                emailMessage.algorithm,
                sealedRfc822Data
            )
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMessageException.AuthenticationException(cause = e)
                is ApolloException -> throw SudoEmailClient.EmailMessageException.FailedException(cause = e)
                else -> throw interpretEmailMessageException(e)
            }
        }
    }

    override suspend fun listEmailMessages(
        emailAddressId: String?,
        sudoId: String?,
        limit: Int,
        nextToken: String?,
        cachePolicy: CachePolicy,
        filter: () -> EmailMessageFilter?
    ): ListOutput<EmailMessage> {
        try {
            val filters = filter.invoke()
            val queryInput = ListEmailMessagesInput.builder()
                .emailAddressId(emailAddressId)
                .sudoId(sudoId)
                .limit(limit)
                .nextToken(nextToken)
                .filter(EmailMessageTransformer.toGraphQLFilter(filters))
                .build()

            val query = ListEmailMessagesQuery.builder()
                .input(queryInput)
                .build()

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(cachePolicy.toResponseFetcher())
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors()}")
                throw interpretEmailMessageError(queryResponse.errors().first())
            }

            val result = queryResponse.data()?.listEmailMessages() ?: return ListOutput(emptyList(), null)
            val emailMessages = EmailMessageTransformer.toEntityFromListEmailMessagesQueryResult(deviceKeyManager, result.items())
            return ListOutput(emailMessages, result.nextToken())
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMessageException.AuthenticationException(cause = e)
                is ApolloException -> throw SudoEmailClient.EmailMessageException.FailedException(cause = e)
                else -> throw interpretEmailMessageException(e)
            }
        }
    }

    override suspend fun checkEmailAddressAvailability(localParts: List<String>, domains: List<String>): List<String> {
        try {
            val input = CheckEmailAddressAvailabilityInput.builder()
                .localParts(localParts)
                .domains(domains)
                .build()

            val query = CheckEmailAddressAvailabilityQuery.builder()
                .input(input)
                .build()

            logger.debug("localParts=$localParts domains=$domains")

            val queryResponse = appSyncClient.query(query)
                .responseFetcher(CachePolicy.REMOTE_ONLY.toResponseFetcher())
                .enqueueFirst()

            if (queryResponse.hasErrors()) {
                logger.warning("errors = ${queryResponse.errors()}")
                throw interpretEmailAddressError(queryResponse.errors().first())
            }

            logger.debug("response=${queryResponse.data()?.checkEmailAddressAvailability()}")

            return queryResponse.data()?.checkEmailAddressAvailability()?.addresses() ?: return emptyList()
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailAddressException.AuthenticationException(cause = e)
                is ApolloException -> throw SudoEmailClient.EmailAddressException.FailedException(cause = e)
                else -> throw interpretEmailAddressException(e)
            }
        }
    }

    override suspend fun deleteEmailMessage(id: String): String {
        try {
            val mutationInput = DeleteEmailMessageInput.builder()
                .messageId(id)
                .build()
            val mutation = DeleteEmailMessageMutation.builder()
                .input(mutationInput)
                .build()

            val mutationResponse = appSyncClient.mutate(mutation)
                .enqueue()

            if (mutationResponse.hasErrors()) {
                logger.warning("errors = ${mutationResponse.errors()}")
                throw interpretEmailMessageError(mutationResponse.errors().first())
            }

            return mutationResponse.data()?.deleteEmailMessage()
                ?: throw SudoEmailClient.EmailMessageException.FailedException(NO_EMAIL_ID_ERROR_MSG)
        } catch (e: Throwable) {
            logger.debug("unexpected error $e")
            when (e) {
                is NotAuthorizedException -> throw SudoEmailClient.EmailMessageException.AuthenticationException(cause = e)
                is ApolloException -> throw SudoEmailClient.EmailMessageException.FailedException(cause = e)
                else -> throw interpretEmailMessageException(e)
            }
        }
    }

    override suspend fun subscribeToEmailMessages(id: String, subscriber: EmailMessageSubscriber) {
        emailMessageSubscriptions.subscribe(id, subscriber)
    }

    override suspend fun unsubscribeFromEmailMessages(id: String) {
        emailMessageSubscriptions.unsubscribe(id)
    }

    override suspend fun unsubscribeAll() {
        emailMessageSubscriptions.unsubscribeAll()
    }

    override fun close() {
        emailMessageSubscriptions.close()
    }

    override fun reset() {
        close()
    }

    /** Private Methods */

    private fun makeS3Key(sealedId: String): String {
        val identityId = sudoUserClient.getCredentialsProvider().identityId
        return "$identityId/email/$sealedId"
    }

    // Ensure there is a current key in the key ring registered with the backend so the data can be sealed
    private suspend fun ensurePublicKeyIsRegistered() {

        val keyPair = publicKeyService.getCurrentKeyPair(PublicKeyService.MissingKeyPolicy.GENERATE_IF_MISSING)
            ?: throw SudoEmailClient.EmailAddressException.PublicKeyException(KEY_RETRIEVAL_ERROR_MSG)

        // Get the key ring for the current key pair from the backend and check that it contains the current key pair
        val keyRing = publicKeyService.getKeyRing(keyPair.keyRingId, CachePolicy.REMOTE_ONLY)
        if (keyRing?.keys?.find { it.keyId == keyPair.keyId } != null) {
            // Key ring on the backend contains the current key pair
            return
        }

        // Register the current key pair with the backend
        publicKeyService.create(keyPair)
    }

    private suspend fun getOwnershipProof(sudoId: String): String {
        val emailAudience = "sudoplatform.email.email-address"
        val sudo = Sudo(sudoId)
        return sudoProfilesClient.getOwnershipProof(sudo, emailAudience)
    }

    private fun interpretEmailDomainError(e: Error): SudoEmailClient.EmailAddressException {
        return SudoEmailClient.EmailAddressException.FailedException(e.toString())
    }

    private fun interpretEmailAddressError(e: Error): SudoEmailClient.EmailAddressException {
        val error = e.customAttributes()[ERROR_TYPE]?.toString() ?: ""
        if (error.contains(ERROR_INVALID_KEYRING)) {
            return SudoEmailClient.EmailAddressException.PublicKeyException(INVALID_KEYRING_MSG)
        } else if (error.contains(ERROR_INVALID_EMAIL) || error.contains(ERROR_INVALID_DOMAIN)) {
            return SudoEmailClient.EmailAddressException.InvalidEmailAddressException(INVALID_EMAIL_ADDRESS_MSG)
        } else if (error.contains(ERROR_ADDRESS_NOT_FOUND)) {
            return SudoEmailClient.EmailAddressException.EmailAddressNotFoundException(EMAIL_ADDRESS_NOT_FOUND_MSG)
        } else if (error.contains(ERROR_ADDRESS_UNAVAILABLE)) {
            return SudoEmailClient.EmailAddressException.UnavailableEmailAddressException(EMAIL_ADDRESS_UNAVAILABLE_MSG)
        } else if (error.contains(ERROR_UNAUTHORIZED_ADDRESS)) {
            return SudoEmailClient.EmailAddressException.UnauthorizedEmailAddressException(EMAIL_ADDRESS_UNAUTHORIZED_MSG)
        } else if (error.contains(ERROR_INSUFFICIENT_ENTITLEMENTS) || error.contains(ERROR_POLICY_FAILED)) {
            return SudoEmailClient.EmailAddressException.InsufficientEntitlementsException(INSUFFICIENT_ENTITLEMENTS_MSG)
        }
        return SudoEmailClient.EmailAddressException.FailedException(e.toString())
    }

    private fun interpretEmailAddressException(e: Throwable): Throwable {
        return when (e) {
            is CancellationException,
            is SudoEmailClient.EmailAddressException -> e
            is PublicKeyService.PublicKeyServiceException ->
                SudoEmailClient.EmailAddressException.PublicKeyException(KEY_RETRIEVAL_ERROR_MSG, e)
            else -> SudoEmailClient.EmailAddressException.UnknownException(e)
        }
    }

    private fun interpretEmailMessageError(e: Error): SudoEmailClient.EmailMessageException {
        val error = e.customAttributes()[ERROR_TYPE]?.toString() ?: ""
        if (error.contains(ERROR_INVALID_EMAIL_CONTENTS)) {
            return SudoEmailClient.EmailMessageException.InvalidMessageContentException(INVALID_MESSAGE_CONTENT_MSG)
        } else if (error.contains(ERROR_UNAUTHORIZED_ADDRESS)) {
            return SudoEmailClient.EmailMessageException.UnauthorizedAddressException(EMAIL_ADDRESS_UNAUTHORIZED_MSG)
        } else if (error.contains(ERROR_MESSAGE_NOT_FOUND)) {
            return SudoEmailClient.EmailMessageException.EmailMessageNotFoundException(EMAIL_MESSAGE_NOT_FOUND_MSG)
        } else if (error.contains(SERVICE_ERROR)) {
            return SudoEmailClient.EmailMessageException.EmailMessageNotFoundException(SUDO_NOT_FOUND_MSG)
        }
        return SudoEmailClient.EmailMessageException.FailedException(e.toString())
    }

    private fun interpretEmailMessageException(e: Throwable): Throwable {
        return when (e) {
            is CancellationException,
            is SudoEmailClient.EmailMessageException -> e
            is PublicKeyService.PublicKeyServiceException ->
                SudoEmailClient.EmailMessageException.PublicKeyException(KEY_RETRIEVAL_ERROR_MSG, e)
            is Unsealer.UnsealerException ->
                SudoEmailClient.EmailMessageException.UnsealingException(UNSEAL_EMAIL_ERROR_MSG, e)
            else -> SudoEmailClient.EmailMessageException.UnknownException(e)
        }
    }
}
