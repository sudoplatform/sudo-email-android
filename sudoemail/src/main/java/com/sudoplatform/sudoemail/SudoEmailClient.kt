/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoconfigmanager.DefaultSudoConfigManager
import com.sudoplatform.sudoconfigmanager.SudoConfigManager
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudoemail.secure.DefaultEmailCryptoService
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.subscription.EmailMessageSubscriber
import com.sudoplatform.sudoemail.subscription.Subscriber
import com.sudoplatform.sudoemail.types.BatchOperationResult
import com.sudoplatform.sudoemail.types.ConfigurationData
import com.sudoplatform.sudoemail.types.DeleteEmailMessageSuccessResult
import com.sudoplatform.sudoemail.types.DraftEmailMessageMetadata
import com.sudoplatform.sudoemail.types.DraftEmailMessageWithContent
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailAddressPublicInfo
import com.sudoplatform.sudoemail.types.EmailFolder
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.EmailMessageOperationFailureResult
import com.sudoplatform.sudoemail.types.EmailMessageRfc822Data
import com.sudoplatform.sudoemail.types.EmailMessageWithBody
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.ListOutput
import com.sudoplatform.sudoemail.types.PartialEmailAddress
import com.sudoplatform.sudoemail.types.PartialEmailMessage
import com.sudoplatform.sudoemail.types.SendEmailMessageResult
import com.sudoplatform.sudoemail.types.UnsealedBlockedAddress
import com.sudoplatform.sudoemail.types.UpdatedEmailMessageResult.UpdatedEmailMessageSuccess
import com.sudoplatform.sudoemail.types.inputs.CheckEmailAddressAvailabilityInput
import com.sudoplatform.sudoemail.types.inputs.CreateCustomEmailFolderInput
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
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.AndroidSQLiteStore
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import org.json.JSONException
import java.util.Objects

/**
 * Interface encapsulating a library for interacting with the Sudo Platform Email service.
 *
 * @sample com.sudoplatform.sudoemail.samples.Samples.sudoEmailClient
 */
interface SudoEmailClient : AutoCloseable {

    companion object {
        /** Create a [Builder] for [SudoEmailClient]. */
        @JvmStatic
        fun builder() = Builder()

        const val DEFAULT_EMAIL_ADDRESS_LIMIT = 10
        const val DEFAULT_EMAIL_FOLDER_LIMIT = 10
        const val DEFAULT_EMAIL_MESSAGE_LIMIT = 10

        const val DEFAULT_KEY_NAMESPACE = "eml"

        private const val CONFIG_EMAIL_SERVICE = "emService"
        private const val CONFIG_REGION = "region"
        private const val CONFIG_EMAIL_BUCKET = "bucket"
        private const val CONFIG_EMAIL_TRANSIENT_BUCKET = "transientBucket"

        internal data class S3Configuration(
            val region: String,
            val emailBucket: String,
            val transientBucket: String,
        )

        @Throws(Builder.ConfigurationException::class)
        @VisibleForTesting
        internal fun readConfiguration(
            context: Context,
            logger: Logger,
            configManager: SudoConfigManager = DefaultSudoConfigManager(context, logger),
        ): S3Configuration {
            val preamble = "sudoplatformconfig.json does not contain"
            val postamble = "the $CONFIG_EMAIL_SERVICE stanza"

            val emailConfig = try {
                configManager.getConfigSet(CONFIG_EMAIL_SERVICE)
            } catch (e: JSONException) {
                throw Builder.ConfigurationException("$preamble $postamble", e)
            }
            emailConfig
                ?: throw Builder.ConfigurationException("$preamble $CONFIG_EMAIL_TRANSIENT_BUCKET in $postamble")

            val region = try {
                emailConfig.getString(CONFIG_REGION)
            } catch (e: JSONException) {
                throw Builder.ConfigurationException("$preamble $CONFIG_REGION in $postamble", e)
            }

            val emailBucket = try {
                emailConfig.getString(CONFIG_EMAIL_BUCKET)
            } catch (e: JSONException) {
                throw Builder.ConfigurationException(
                    "$preamble $CONFIG_EMAIL_BUCKET in $postamble",
                    e,
                )
            }

            val emailTransientBucket = try {
                emailConfig.getString(CONFIG_EMAIL_TRANSIENT_BUCKET)
            } catch (e: JSONException) {
                throw Builder.ConfigurationException(
                    "$preamble $CONFIG_EMAIL_TRANSIENT_BUCKET in $postamble",
                    e,
                )
            }

            return S3Configuration(region, emailBucket, emailTransientBucket)
        }
    }

    /**
     * Builder used to construct the [SudoEmailClient].
     */
    class Builder internal constructor() {
        private var context: Context? = null
        private var sudoUserClient: SudoUserClient? = null
        private var graphQLClient: GraphQLClient? = null
        private var keyManager: KeyManagerInterface? = null
        private var logger: Logger =
            Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))
        private var namespace: String = DEFAULT_KEY_NAMESPACE
        private var notificationHandler: SudoEmailNotificationHandler? = null
        private var databaseName: String = AndroidSQLiteStore.DEFAULT_DATABASE_NAME

        /**
         * Provide the application context (required input).
         */
        fun setContext(context: Context) = also {
            this.context = context
        }

        /**
         * Provide the implementation of the [SudoUserClient] used to perform
         * sign in and ownership operations (required input).
         */
        fun setSudoUserClient(sudoUserClient: SudoUserClient) = also {
            this.sudoUserClient = sudoUserClient
        }

        /**
         * Provide a [GraphQLClient] for the [SudoEmailClient] to use
         * (optional input). If this is not supplied, an [GraphQLClient] will
         * be constructed and used.
         */
        fun setGraphQLClient(graphQLClient: GraphQLClient) = also {
            this.graphQLClient = graphQLClient
        }

        /**
         * Provide the implementation of the [KeyManagerInterface] used for key management and
         * cryptographic operations (optional input). If a value is not supplied a default
         * implementation will be used.
         */
        fun setKeyManager(keyManager: KeyManagerInterface) = also {
            this.keyManager = keyManager
        }

        /**
         * Provide the implementation of the [Logger] used for logging errors (optional input).
         * If a value is not supplied a default implementation will be used.
         */
        fun setLogger(logger: Logger) = also {
            this.logger = logger
        }

        /**
         * Provide the namespace to use for internal data and cryptographic keys. This should be unique
         * per client per app to avoid name conflicts between multiple clients. If a value is not supplied
         * a default value will be used.
         */
        fun setNamespace(namespace: String) = also {
            this.namespace = namespace
        }

        fun setNotificationHandler(notificationHandler: SudoEmailNotificationHandler) = also {
            this.notificationHandler = notificationHandler
        }

        /**
         * Provide the database name to use for exportable key store database.
         */
        fun setDatabaseName(databaseName: String) = also {
            this.databaseName = databaseName
        }

        /** A configuration item that is needed is missing */
        class ConfigurationException(message: String, cause: Throwable? = null) :
            RuntimeException(message, cause)

        /**
         * Construct the [SudoEmailClient]. Will throw a [NullPointerException] if
         * the [context] and [sudoUserClient] has not been provided.
         */
        @Throws(NullPointerException::class, ConfigurationException::class)
        fun build(): SudoEmailClient {
            Objects.requireNonNull(context, "Context must be provided.")
            Objects.requireNonNull(sudoUserClient, "SudoUserClient must be provided.")

            val client = graphQLClient ?: ApiClientManager.getClient(
                this@Builder.context!!,
                this@Builder.sudoUserClient!!,
                CONFIG_EMAIL_SERVICE,
            )

            val serviceKeyManager = DefaultServiceKeyManager(
                keyRingServiceName = "sudo-email",
                userClient = sudoUserClient!!,
                keyManager = keyManager ?: KeyManagerFactory(context!!).createAndroidKeyManager(
                    this.namespace,
                    this.databaseName,
                ),
            )

            val emailMessageDataProcessor = Rfc822MessageDataProcessor(context!!)

            val sealingService = DefaultSealingService(
                deviceKeyManager = serviceKeyManager,
                logger = logger,
            )

            val emailCryptoService = DefaultEmailCryptoService(
                deviceKeyManager = serviceKeyManager,
                logger = logger,
            )

            val (region, emailBucket, transientBucket) = readConfiguration(context!!, logger)

            return DefaultSudoEmailClient(
                context = context!!,
                graphQLClient = client,
                sudoUserClient = sudoUserClient!!,
                logger = logger,
                serviceKeyManager = serviceKeyManager,
                region = region,
                emailBucket = emailBucket,
                transientBucket = transientBucket,
                emailMessageDataProcessor = emailMessageDataProcessor,
                sealingService = sealingService,
                emailCryptoService = emailCryptoService,
                notificationHandler = notificationHandler,
            )
        }
    }

    /**
     * Defines the exceptions for the email address based methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class EmailAddressException(message: String? = null, cause: Throwable? = null) :
        RuntimeException(message, cause) {
        class FailedException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)

        @Deprecated("InsufficientEntitlementsException is now thrown instead")
        class EntitlementsExceededException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)

        class InsufficientEntitlementsException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)

        class ProvisionFailedException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)

        class InvalidEmailAddressException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)

        class EmailAddressNotFoundException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)

        class UnavailableEmailAddressException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)

        class UnauthorizedEmailAddressException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)

        class PublicKeyException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)

        class UnsealingException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)

        class DeprovisionFailedException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)

        class UpdateFailedException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)

        class AuthenticationException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)

        class LimitExceededException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)

        class UnknownException(cause: Throwable) :
            EmailAddressException(cause = cause)
    }

    /**
     * Defines the exceptions for the email folder based methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class EmailFolderException(message: String? = null, cause: Throwable? = null) :
        RuntimeException(message, cause) {
        class FailedException(message: String? = null, cause: Throwable? = null) :
            EmailFolderException(message = message, cause = cause)

        class AuthenticationException(message: String? = null, cause: Throwable? = null) :
            EmailFolderException(message = message, cause = cause)

        class UnknownException(cause: Throwable) :
            EmailFolderException(cause = cause)
    }

    /**
     * Defines the exceptions for the email blocklist based methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class EmailBlocklistException(message: String? = null, cause: Throwable? = null) :
        RuntimeException(message, cause) {
        class InvalidInputException(message: String? = null, cause: Throwable? = null) :
            EmailBlocklistException(message = message, cause = cause)

        class FailedException(message: String? = null, cause: Throwable? = null) :
            EmailBlocklistException(message = message, cause = cause)

        class UnknownException(cause: Throwable? = null) :
            EmailBlocklistException(cause = cause)
    }

    /**
     * Defines the exceptions for the email message based methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class EmailMessageException(message: String? = null, cause: Throwable? = null) :
        RuntimeException(message, cause) {
        class FailedException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)

        class SendFailedException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)

        class InvalidMessageContentException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)

        class UnauthorizedAddressException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)

        class InNetworkAddressNotFoundException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)

        class UnsealingException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)

        class AuthenticationException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)

        class EmailMessageNotFoundException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)

        class LimitExceededException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)

        class EmailMessageSizeLimitExceededException(
            message: String? = null,
            cause: Throwable? = null,
        ) :
            EmailMessageException(message = message, cause = cause)

        class InvalidArgumentException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)

        class UnknownException(cause: Throwable) :
            EmailMessageException(cause = cause)
    }

    /**
     * Defines the exceptions for the email configuration based methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class EmailConfigurationException(message: String? = null, cause: Throwable? = null) :
        RuntimeException(message, cause) {
        class FailedException(message: String? = null, cause: Throwable? = null) :
            EmailConfigurationException(message = message, cause = cause)

        class UnknownException(cause: Throwable) :
            EmailConfigurationException(cause = cause)
    }

    /**
     * Defines the exceptions for the email cryptographic keys based methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class EmailCryptographicKeysException(
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        class SecureKeyArchiveException(message: String? = null, cause: Throwable? = null) :
            EmailCryptographicKeysException(message = message, cause = cause)
    }

    /**
     * Get the configuration data for the email service.
     *
     * @returns The configuration data [ConfigurationData] for the email service.
     *
     * @throws [EmailConfigurationException].
     */
    @Throws(EmailConfigurationException::class)
    suspend fun getConfigurationData(): ConfigurationData

    /**
     * Get a list of the supported email domains. Primarily intended to be used to perform a domain search
     * which occurs prior to provisioning an email address.
     *
     * @return A list of supported domains.
     *
     * @throws [EmailAddressException].
     */
    @Throws(EmailAddressException::class)
    suspend fun getSupportedEmailDomains(): List<String>

    /**
     * Get a list of all of the configured domains. Primarily intended to be used as part of performing
     * an email send operation in order to fetch all domains configured for the service so that appropriate
     * encryption decisions can be made.
     *
     * @return A list of all configured domains.
     *
     * @throws [EmailAddressException].
     */
    @Throws(EmailAddressException::class)
    suspend fun getConfiguredEmailDomains(): List<String>

    /**
     * Check if an email address is available to be provisioned within a domain.
     *
     * Criteria:
     *  - At least one local part is required.
     *  - A maximum of 5 local parts per request.
     *  - Local parts must not exceed 64 characters.
     *  - Local parts must match the following pattern: `^[a-zA-Z0-9](\.?[-_a-zA-Z0-9])*$`.
     *
     * @param input [CheckEmailAddressAvailabilityInput] Parameters used to check for email address availability.
     * @return A list of fully qualified available email addresses in the domains that match the local parts.
     *
     * @throws [EmailAddressException].
     */
    @Throws(EmailAddressException::class)
    suspend fun checkEmailAddressAvailability(input: CheckEmailAddressAvailabilityInput): List<String>

    /**
     * Provision an [EmailAddress].
     *
     * @param input [ProvisionEmailAddressInput] Parameters used to provision an email address.
     * @return The provisioned [EmailAddress].
     *
     * @throws [EmailAddressException].
     */
    @Throws(EmailAddressException::class)
    suspend fun provisionEmailAddress(input: ProvisionEmailAddressInput): EmailAddress

    /**
     * Deprovision an [EmailAddress].
     *
     * @param id [String] The identifier of the [EmailAddress] to deprovision.
     * @return The deprovisioned [EmailAddress].
     *
     * @throws [EmailAddressException].
     */
    @Throws(EmailAddressException::class)
    suspend fun deprovisionEmailAddress(id: String): EmailAddress

    /**
     * Update the metadata of an [EmailAddress].
     *
     * @property input [UpdateEmailAddressMetadataInput] Parameters used to update the metadata of
     *  an email address.
     * @return The identifier of the updated [EmailAddress].
     *
     * @throws [EmailAddressException].
     */
    @Throws(EmailAddressException::class)
    suspend fun updateEmailAddressMetadata(input: UpdateEmailAddressMetadataInput): String

    /**
     * Get an [EmailAddress] using its identifier.
     *
     * @param input [GetEmailAddressInput] Parameters used to retrieve an [EmailAddress].
     * @return The [EmailAddress] associated with the identifier or null if the email address cannot be found.
     *
     * @throws [EmailAddressException].
     */
    @Throws(EmailAddressException::class)
    suspend fun getEmailAddress(input: GetEmailAddressInput): EmailAddress?

    /**
     * Get a list of [EmailAddress]es.
     *
     * This API returns a [ListAPIResult]:
     * - On [ListAPIResult.Success] result, contains the list of requested [EmailAddress]es.
     * - On [ListAPIResult.Partial] result, contains the list of [PartialEmailAddress]es representing
     *     email addresses that could not be unsealed successfully and the exception indicating why
     *     the unsealing failed. An email address may fail to unseal if the client version is not up to
     *     date or the required cryptographic key is missing from the client device.
     *
     * If no [EmailAddress]es can be found, the result will contain null for the nextToken field and
     * contain an empty item list.
     *
     * @param input [ListEmailAddressesInput] Parameters used to retrieve a list of provisioned email addresses.
     * @return A [ListAPIResult.Success] or a [ListAPIResult.Partial] result containing either a list of
     *  [EmailAddress]es or [PartialEmailAddress]es respectively. Returns an empty list if no email addresses can be found.
     *
     * @throws [EmailAddressException].
     */
    @Throws(EmailAddressException::class)
    suspend fun listEmailAddresses(input: ListEmailAddressesInput): ListAPIResult<EmailAddress, PartialEmailAddress>

    /**
     * Get a list of [EmailAddress]es owned by the Sudo identified by sudoId.
     *
     * This API returns a [ListAPIResult]:
     * - On [ListAPIResult.Success] result, contains the list of requested [EmailAddress]es.
     * - On [ListAPIResult.Partial] result, contains the list of [PartialEmailAddress]es representing
     *     email addresses that could not be unsealed successfully and the exception indicating why
     *     the unsealing failed. An email address may fail to unseal if the client version is not up to
     *     date or the required cryptographic key is missing from the client device.
     *
     * If no [EmailAddress]es can be found, the result will contain null for the nextToken field and
     * contain an empty item list.
     *
     * @param input [ListEmailAddressesForSudoIdInput] Parameters used to retrieve a list of provisioned email addresses for a Sudo.
     * @return A [ListAPIResult.Success] or a [ListAPIResult.Partial] result containing either a list of
     *  [EmailAddress]es or [PartialEmailAddress]es respectively. Returns an empty list if no email addresses can be found.
     *
     * @throws [EmailAddressException].
     */
    @Throws(EmailAddressException::class)
    suspend fun listEmailAddressesForSudoId(input: ListEmailAddressesForSudoIdInput): ListAPIResult<EmailAddress, PartialEmailAddress>

    /**
     * Get a list of [EmailAddressPublicInfo] objects associated with the provided email addresses.
     *
     * Results can only be retrieved in batches of 50 or less. Anything greater will throw an
     * [EmailAddressException.LimitExceededException].
     *
     * @param input [LookupEmailAddressesPublicInfoInput] Parameters used to retrieve a list of email address public information objects.
     * @returns An array of [EmailAddressPublicInfo], or an empty array if email addresses or their public keys cannot be found.
     *
     * @throws [EmailAddressException].
     */
    @Throws(EmailAddressException::class)
    suspend fun lookupEmailAddressesPublicInfo(input: LookupEmailAddressesPublicInfoInput): List<EmailAddressPublicInfo>

    /**
     * Get a [ListOutput] of [EmailFolder]s.
     *
     * If no [EmailFolder]s can be found, the [ListOutput] will contain null for the [ListOutput.nextToken]
     * field and contain an empty [ListOutput.items] list.
     *
     * @param input [ListEmailFoldersForEmailAddressIdInput] Parameters used to list email folders for an email address identifier.
     * @return A list of [EmailFolder]s or an empty list if no email folders can be found.
     *
     * @throws [EmailFolderException].
     */
    @Throws(EmailFolderException::class)
    suspend fun listEmailFoldersForEmailAddressId(input: ListEmailFoldersForEmailAddressIdInput): ListOutput<EmailFolder>

    /**
     * Create a custom named [EmailFolder].
     *
     * @param input [CreateCustomEmailFolderInput] Parameters used to create the new folder.
     * @return The newly created [EmailFolder].
     *
     * @throws [EmailFolderException].
     */
    @Throws(EmailFolderException::class)
    suspend fun createCustomEmailFolder(input: CreateCustomEmailFolderInput): EmailFolder

    /**
     * Send an email message using RFC 6854 (supersedes RFC 822)(https://tools.ietf.org/html/rfc6854) data.
     *
     * Email messages sent to in-network recipients (i.e. email addresses that exist within the Sudo Platform)
     * will be sent end-to-end encrypted.
     *
     * @param input [SendEmailMessageInput] Parameters used to send an email message.
     * @return The identifier of the [EmailMessage] that is being sent.
     *
     * @throws [EmailMessageException].
     */
    @Throws(EmailMessageException::class)
    suspend fun sendEmailMessage(input: SendEmailMessageInput): SendEmailMessageResult

    /**
     * Update multiple [EmailMessage]s using a list of identifiers.
     *
     * Email messages can only be updated in batches of 100 or less. Anything greater will throw an
     * [EmailMessageException.LimitExceededException].
     *
     * This API returns a [BatchOperationResult]:
     * - On Success, all email messages succeeded to update.
     * - On Partial, only a partial amount of messages succeeded to update. Result includes a list
     *     of identifiers of the email messages that failed and succeeded to update.
     * - On Failure, all email messages failed to update. Result contains a list of identifiers of
     *     email messages that failed to update.
     *
     * @param input [UpdateEmailMessagesInput] Parameters used to update a list of email messages.
     * @return A success, partial or failed [BatchOperationResult] result containing either a list of identifiers
     *  of email messages that succeeded or failed to be updated.
     *
     * @throws [EmailMessageException].
     */
    @Throws(EmailMessageException::class)
    suspend fun updateEmailMessages(input: UpdateEmailMessagesInput): BatchOperationResult<
        UpdatedEmailMessageSuccess,
        EmailMessageOperationFailureResult,
        >

    /**
     * Delete multiple [EmailMessage]s using a list of identifiers.
     *
     * Email messages can only be deleted in batches of 100 or less. Anything greater will throw an
     * [EmailMessageException.LimitExceededException].
     *
     * This API returns a [BatchOperationResult]:
     * - On Success, all email messages succeeded to delete.
     * - On Partial, only a partial amount of messages succeeded to delete. Result includes two lists;
     *     one containing identifiers of email messages that were successfully deleted, and the other
     *     containing identifiers and failure reasons of email messages that failed to delete.
     * - On Failure, all email messages failed to delete. Result contains a list of identifiers of and
     *     failure reasons of email messages that failed to delete.
     *
     * @param ids [List<String>] A list of one or more identifiers of the email messages to be deleted.
     *  There is a limit of 100 email message identifiers per request. Exceeding this will cause an exception.
     *  to be thrown.
     * @return A success, partial or failed [BatchOperationResult] result containing a list of ids of email
     *  messages that failed to delete, and/or a list of ids of email messages that were successfully deleted.
     *
     * @throws [EmailMessageException].
     */
    @Throws(EmailMessageException::class)
    suspend fun deleteEmailMessages(
        ids: List<String>,
    ): BatchOperationResult<DeleteEmailMessageSuccessResult, EmailMessageOperationFailureResult>

    /**
     * Delete a single [EmailMessage] using the [id] parameter.
     *
     * @param id [String] Identifier of the [EmailMessage] to be deleted.
     * @returns Result of object containing the identifier of the [EmailMessage] that was deleted,
     *  or null if the email message could not be deleted.
     *
     * @throws [EmailMessageException].
     */
    @Throws(EmailMessageException::class)
    suspend fun deleteEmailMessage(id: String): DeleteEmailMessageSuccessResult?

    /**
     * Get an [EmailMessage] using its identifier.
     *
     * @param input [GetEmailMessageInput] Parameters used to retrieve an [EmailMessage].
     * @return The [EmailMessage] associated with the identifier or null if the email message cannot be found.
     */
    @Throws(EmailMessageException::class)
    suspend fun getEmailMessage(input: GetEmailMessageInput): EmailMessage?

    /**
     * Get the RFC 6854 (supersedes RFC 822) data of an [EmailMessage].
     *
     * @param input [GetEmailMessageRfc822DataInput] Parameters used to retrieve the data of the email message.
     * @returns The data associated with the [EmailMessage] or null if the email message cannot be found.
     */
    @Deprecated(
        "Use getEmailMessageWithBody instead to retrieve email message data",
        ReplaceWith("getEmailMessageWithBody"),
    )
    @Throws(EmailMessageException::class)
    suspend fun getEmailMessageRfc822Data(input: GetEmailMessageRfc822DataInput): EmailMessageRfc822Data?

    /**
     * Get the body and attachment data of an [EmailMessage].
     *
     * @param input [GetEmailMessageWithBodyInput] Parameters used to retrieve the data of the email message.
     * @returns The data associated with the [EmailMessage] or null if the email message cannot be found.
     */
    @Throws(EmailMessageException::class)
    suspend fun getEmailMessageWithBody(input: GetEmailMessageWithBodyInput): EmailMessageWithBody?

    /**
     * Get a list of all [EmailMessage]s for the user.
     *
     * This API returns a [ListAPIResult]:
     * - On [ListAPIResult.Success] result, contains the list of requested [EmailMessage]s.
     * - On [ListAPIResult.Partial] result, contains the list of [PartialEmailMessage]s representing
     *     email messages that could not be unsealed successfully and the exception indicating why
     *     the unsealing failed. An email message may fail to unseal if the client version is not up to
     *     date or the required cryptographic key is missing from the client device.
     *
     * If no [EmailMessage]s can be found, the result will contain null for the nextToken field and
     * contain an empty item list.
     *
     * @param input [ListEmailMessagesInput] Parameters used to retrieve a list of all email messages
     *  for the user.
     * @return A [ListAPIResult.Success] or a [ListAPIResult.Partial] result containing either a list of
     *  [EmailMessage]s or [PartialEmailMessage]s respectively. Returns an empty list if no email messages can be found.
     *
     * @throws [EmailMessageException].
     */
    @Throws(EmailMessageException::class)
    suspend fun listEmailMessages(
        input: ListEmailMessagesInput,
    ): ListAPIResult<EmailMessage, PartialEmailMessage>

    /**
     * Get a list of [EmailMessage]s for the specified email address identifier.
     *
     * This API returns a [ListAPIResult]:
     * - On [ListAPIResult.Success] result, contains the list of requested [EmailMessage]s.
     * - On [ListAPIResult.Partial] result, contains the list of [PartialEmailMessage]s representing
     *     email messages that could not be unsealed successfully and the exception indicating why
     *     the unsealing failed. An email message may fail to unseal if the client version is not up to
     *     date or the required cryptographic key is missing from the client device.
     *
     * If no [EmailMessage]s can be found, the result will contain null for the nextToken field and
     * contain an empty item list.
     *
     * @param input [ListEmailMessagesForEmailAddressIdInput] Parameters used to retrieve a list of email messages
     *  for an email address identifier.
     * @return A [ListAPIResult.Success] or a [ListAPIResult.Partial] result containing either a list of
     *  [EmailMessage]s or [PartialEmailMessage]s respectively. Returns an empty list if no email messages can be found.
     *
     * @throws [EmailMessageException].
     */
    @Throws(EmailMessageException::class)
    suspend fun listEmailMessagesForEmailAddressId(
        input: ListEmailMessagesForEmailAddressIdInput,
    ): ListAPIResult<EmailMessage, PartialEmailMessage>

    /**
     * Get a list of [EmailMessage]s for the specified email folder identifier.
     *
     * This API returns a [ListAPIResult]:
     * - On [ListAPIResult.Success] result, contains the list of requested [EmailMessage]s.
     * - On [ListAPIResult.Partial] result, contains the list of [PartialEmailMessage]s representing
     *     email messages that could not be unsealed successfully and the exception indicating why
     *     the unsealing failed. An email message may fail to unseal if the client version is not up to
     *     date or the required cryptographic key is missing from the client device.
     *
     * If no [EmailMessage]s can be found, the result will contain null for the nextToken field and
     * contain an empty item list.
     *
     * @param input [ListEmailMessagesForEmailFolderIdInput] Parameters used to retrieve a list of email messages
     *  for an email folder identifier.
     * @return A [ListAPIResult.Success] or a [ListAPIResult.Partial] result containing either a list of
     *  [EmailMessage]s or [PartialEmailMessage]s respectively. Returns an empty list if no email messages can be found.
     *
     * @throws [EmailMessageException].
     */
    @Throws(EmailMessageException::class)
    suspend fun listEmailMessagesForEmailFolderId(
        input: ListEmailMessagesForEmailFolderIdInput,
    ): ListAPIResult<EmailMessage, PartialEmailMessage>

    /**
     * Creates a draft email message using RFC 6854 (supersedes RFC 822)(https://tools.ietf.org/html/rfc6854) data.
     *
     * @param input [CreateDraftEmailMessageInput] Parameters used to create a draft email message.
     * @return The identifier of the draft message that is being created.
     *
     * @throws [EmailMessageException], [EmailAddressException].
     */
    @Throws(EmailMessageException::class, EmailAddressException::class)
    suspend fun createDraftEmailMessage(input: CreateDraftEmailMessageInput): String

    /**
     * Updates a draft email message using RFC 6854 (supersedes RFC 822)(https://tools.ietf.org/html/rfc6854) data.
     *
     * @param input [UpdateDraftEmailMessageInput] Parameters used to update a draft email message.
     * @return The identifier of the draft message that is being updated.
     *
     * @throws [EmailMessageException], [EmailAddressException].
     */
    @Throws(EmailMessageException::class, EmailAddressException::class)
    suspend fun updateDraftEmailMessage(input: UpdateDraftEmailMessageInput): String

    /**
     * Delete multiple draft email messages with a list of identifiers.
     *
     * This API returns a [BatchOperationResult]:
     * - On Success, all email messages succeeded to delete.
     * - On Partial, only a partial amount of messages succeeded to delete. Result includes two lists;
     *     one containing identifiers of email messages that were successfully deleted, and the other
     *     containing identifiers and failure reasons of email messages that failed to delete.
     * - On Failure, all email messages failed to delete. Result contains a list of identifiers of and
     *     failure reasons of email messages that failed to delete.
     *
     * @param input [DeleteDraftEmailMessagesInput] Input parameters containing a list of draft email message
     * identifiers and an email address identifier.
     * @return A success, partial or failed [BatchOperationResult] result containing a list of ids of email
     *  messages that failed to delete with an associated error type, and/or a list of ids of email messages
     *  that were successfully deleted.
     *
     * @throws [EmailAddressException], [EmailMessageException].
     */
    @Throws(EmailAddressException::class, EmailMessageException::class)
    suspend fun deleteDraftEmailMessages(
        input: DeleteDraftEmailMessagesInput,
    ): BatchOperationResult<DeleteEmailMessageSuccessResult, EmailMessageOperationFailureResult>

    /**
     * Retrieves a draft email message.
     *
     * @param input [GetDraftEmailMessageInput] Parameters used to retrieve a draft email message.
     * @return The [DraftEmailMessageWithContent] containing metadata and content.
     *
     * @throws [EmailMessageException]
     */
    @Throws(EmailMessageException::class)
    suspend fun getDraftEmailMessage(input: GetDraftEmailMessageInput): DraftEmailMessageWithContent

    /**
     * Lists the metadata and content of all draft messages for the user.
     *
     * @return List of [DraftEmailMessageWithContent].
     *
     * @throws [EmailMessageException].
     */
    @Throws(EmailMessageException::class)
    suspend fun listDraftEmailMessages(): List<DraftEmailMessageWithContent>

    /**
     * Lists the metadata and content of all draft messages for the specified email address identifier.
     *
     * @param emailAddressId [String] The identifier of the email address associated with the draft email messages.
     * @return List of [DraftEmailMessageWithContent].
     *
     * @throws [EmailMessageException].
     */
    @Throws(EmailMessageException::class)
    suspend fun listDraftEmailMessagesForEmailAddressId(emailAddressId: String): List<DraftEmailMessageWithContent>

    /**
     * Lists the metadata of all draft messages for the user.
     *
     * @return List of [DraftEmailMessageMetadata].
     *
     * @throws [EmailMessageException].
     */
    @Throws(EmailMessageException::class)
    suspend fun listDraftEmailMessageMetadata(): List<DraftEmailMessageMetadata>

    /**
     * Lists the metadata of all draft messages for the specified email address identifier.
     *
     * @param emailAddressId [String] The identifier of the email address associated with the draft email messages.
     * @return List of [DraftEmailMessageMetadata].
     *
     * @throws [EmailMessageException].
     */
    @Throws(EmailMessageException::class)
    suspend fun listDraftEmailMessageMetadataForEmailAddressId(emailAddressId: String): List<DraftEmailMessageMetadata>

    /**
     * Blocks the given email address(es) for the user identified
     *
     * @param addresses [List<String>] The list of email addresses to block
     * @return A success, partial or failed [BatchOperationResult] result containing either a list of identifiers
     *  of email addresses that succeeded or failed to be blocked.
     */
    suspend fun blockEmailAddresses(addresses: List<String>): BatchOperationResult<String, String>

    /**
     * Unblocks the given email address(es) for the logged in user
     *
     * @param addresses [List<String>] The list of email addresses to unblock
     * @return A success, partial or failed [BatchOperationResult] result containing either a list of identifiers
     *  of email addresses that succeeded or failed to be unblocked.
     */
    suspend fun unblockEmailAddresses(addresses: List<String>): BatchOperationResult<String, String>

    /**
     * Unblocks the email addresses associated with the hashed values passed in for the logged in user
     *
     * @param hashedValues [List<String>] The list of hashedValues to unblock
     * @return A success, partial or failed [BatchOperationResult] result containing either a list of identifiers
     *  of email addresses that succeeded or failed to be blocked.
     */
    suspend fun unblockEmailAddressesByHashedValue(hashedValues: List<String>): BatchOperationResult<String, String>

    /**
     * Get email address blocklist for given owner.
     *
     * @return A list of the blocked addresses
     */
    suspend fun getEmailAddressBlocklist(): List<UnsealedBlockedAddress>

    /**
     * Import cryptographic keys from a key archive.
     *
     * @param archiveData [ByteArray] Key archive data to import the keys from.
     *
     * @throws [EmailCryptographicKeysException]
     */
    @Throws(EmailCryptographicKeysException::class)
    suspend fun importKeys(archiveData: ByteArray)

    /**
     * Export the cryptographic keys to a key archive.
     *
     * @returns Key archive data.
     *
     * @throws [EmailCryptographicKeysException]
     */
    @Throws(EmailCryptographicKeysException::class)
    suspend fun exportKeys(): ByteArray

    /**
     * Subscribes to be notified of new and deleted [EmailMessage]s. Subscribing multiple
     * times with the same subscriber id will cause the previous subscriber to be unsubscribed.
     *
     * @param id [String] Unique identifier of the subscriber.
     * @param subscriber [EmailMessageSubscriber] Subscriber to notify.
     */
    @Throws(EmailMessageException.AuthenticationException::class)
    suspend fun subscribeToEmailMessages(id: String, subscriber: EmailMessageSubscriber)

    /**
     * Unsubscribe the specified subscriber so that it no longer receives notifications about
     * new or deleted [EmailMessage]s.
     *
     * @param id [String] Unique identifier of the subscriber.
     */
    suspend fun unsubscribeFromEmailMessages(id: String)

    /**
     * Unsubscribe all subscribers from receiving notifications about modifications to [EmailMessage]s.
     */
    suspend fun unsubscribeAllFromEmailMessages()

    /**
     * Reset any internal state and cached content.
     */
    fun reset()
}

/**
 * Subscribes to be notified of new and deleted [EmailMessage]s.
 *
 * @param id [String] Unique identifier for the subscriber.
 * @param onConnectionChange Lambda that is called when the subscription connection state changes.
 * @param onEmailMessageChanged Lambda that receives updates as [EmailMessage]s are modified.
 */
@Throws(SudoEmailClient.EmailMessageException::class)
suspend fun SudoEmailClient.subscribeToEmailMessages(
    id: String,
    onConnectionChange: (status: Subscriber.ConnectionState) -> Unit = {},
    onEmailMessageChanged: (emailMessage: EmailMessage, type: EmailMessageSubscriber.ChangeType) -> Unit,
) =
    subscribeToEmailMessages(
        id,
        object : EmailMessageSubscriber {
            override fun connectionStatusChanged(state: Subscriber.ConnectionState) {
                onConnectionChange.invoke(state)
            }

            override fun emailMessageChanged(emailMessage: EmailMessage, type: EmailMessageSubscriber.ChangeType) {
                onEmailMessageChanged.invoke(emailMessage, type)
            }
        },
    )
