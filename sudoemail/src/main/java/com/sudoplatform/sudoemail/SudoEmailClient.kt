/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoconfigmanager.DefaultSudoConfigManager
import com.sudoplatform.sudoconfigmanager.SudoConfigManager
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.keys.DefaultPublicKeyService
import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudoemail.subscription.EmailMessageSubscriber
import com.sudoplatform.sudoemail.types.CachePolicy
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudoemail.types.ListOutput
import com.sudoplatform.sudoemail.types.inputs.filters.EmailAddressFilter
import com.sudoplatform.sudoemail.types.inputs.filters.EmailMessageFilter
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudoprofiles.Sudo
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudouser.SudoUserClient
import org.json.JSONException
import java.util.Objects

/**
 * Interface encapsulating a library for interacting with the Sudo Platform Email service.
 *
 * @sample com.sudoplatform.sudoemail.samples.Samples.sudoEmailClient
 * @since 2020-08-04
 */
interface SudoEmailClient : AutoCloseable {

    companion object {
        /** Create a [Builder] for [SudoEmailClient]. */
        @JvmStatic
        fun builder() = Builder()

        const val DEFAULT_EMAIL_ADDRESS_LIMIT = 10
        const val DEFAULT_EMAIL_MESSAGE_LIMIT = 10

        private const val CONFIG_IDENTITY_SERVICE = "identityService"
        private const val CONFIG_REGION = "region"
        private const val CONFIG_IDENTITY_BUCKET = "bucket"
        private const val CONFIG_TRANSIENT_BUCKET = "transientBucket"

        internal data class S3Configuration(
            val region: String,
            val identityBucket: String,
            val transientBucket: String
        )

        @Throws(Builder.ConfigurationException::class)
        @VisibleForTesting
        internal fun readConfiguration(
            context: Context,
            logger: Logger,
            configManager: SudoConfigManager = DefaultSudoConfigManager(context, logger)
        ): S3Configuration {

            val preamble = "sudoplatformconfig.json does not contain"
            val postamble = "the $CONFIG_IDENTITY_SERVICE stanza"

            val identityConfig = try {
                configManager.getConfigSet(CONFIG_IDENTITY_SERVICE)
            } catch (e: JSONException) {
                throw Builder.ConfigurationException("$preamble $postamble", e)
            }
            identityConfig ?: throw Builder.ConfigurationException("$preamble $CONFIG_TRANSIENT_BUCKET in $postamble")

            val region = try {
                identityConfig.getString(CONFIG_REGION)
            } catch (e: JSONException) {
                throw Builder.ConfigurationException("$preamble $CONFIG_REGION in $postamble", e)
            }

            val identityBucket = try {
                identityConfig.getString(CONFIG_IDENTITY_BUCKET)
            } catch (e: JSONException) {
                throw Builder.ConfigurationException("$preamble $CONFIG_IDENTITY_BUCKET in $postamble", e)
            }

            val transientBucket = try {
                identityConfig.getString(CONFIG_TRANSIENT_BUCKET)
            } catch (e: JSONException) {
                throw Builder.ConfigurationException("$preamble $CONFIG_TRANSIENT_BUCKET in $postamble", e)
            }

            return S3Configuration(region, identityBucket, transientBucket)
        }
    }

    /**
     * Builder used to construct the [SudoEmailClient].
     */
    class Builder internal constructor() {
        private var context: Context? = null
        private var sudoUserClient: SudoUserClient? = null
        private var sudoProfilesClient: SudoProfilesClient? = null
        private var appSyncClient: AWSAppSyncClient? = null
        private var keyManager: KeyManagerInterface? = null
        private var logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))

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
         * Provide the implementation of the [SudoProfilesClient] used to perform
         * ownership proof lifecycle operations (required input).
         */
        fun setSudoProfilesClient(sudoProfilesClient: SudoProfilesClient) = also {
            this.sudoProfilesClient = sudoProfilesClient
        }

        /**
         * Provide an [AWSAppSyncClient] for the [SudoEmailClient] to use
         * (optional input). If this is not supplied, an [AWSAppSyncClient] will
         * be constructed and used.
         */
        fun setAppSyncClient(appSyncClient: AWSAppSyncClient) = also {
            this.appSyncClient = appSyncClient
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

        /** A configuration item that is needed is missing */
        class ConfigurationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

        /**
         * Construct the [SudoEmailClient]. Will throw a [NullPointerException] if
         * the [context], [sudoUserClient] and [sudoProfilesClient] has not been provided.
         */
        @Throws(NullPointerException::class, ConfigurationException::class)
        fun build(): SudoEmailClient {
            Objects.requireNonNull(context, "Context must be provided.")
            Objects.requireNonNull(sudoUserClient, "SudoUserClient must be provided.")
            Objects.requireNonNull(sudoProfilesClient, "SudoProfilesClient must be provided.")

            val appSyncClient = appSyncClient ?: ApiClientManager.getClient(this@Builder.context!!, this@Builder.sudoUserClient!!)

            val deviceKeyManager = DefaultDeviceKeyManager(
                context = context!!,
                keyRingServiceName = "sudo-email",
                userClient = sudoUserClient!!,
                keyManager = keyManager ?: KeyManagerFactory(context!!).createAndroidKeyManager()
            )

            val publicKeyService = DefaultPublicKeyService(
                deviceKeyManager = deviceKeyManager,
                appSyncClient = appSyncClient,
                logger = logger
            )

            val (region, identityBucket, transientBucket) = readConfiguration(context!!, logger)

            return DefaultSudoEmailClient(
                context = context!!,
                appSyncClient = appSyncClient,
                sudoUserClient = sudoUserClient!!,
                sudoProfilesClient = sudoProfilesClient!!,
                logger = logger,
                deviceKeyManager = deviceKeyManager,
                publicKeyService = publicKeyService,
                region = region,
                identityBucket = identityBucket,
                transientBucket = transientBucket
            )
        }
    }

    /**
     * Defines the exceptions for the email address based methods.
     *
     * @property message Accompanying message for the exception.
     * @property cause The cause for the exception.
     */
    sealed class EmailAddressException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
        class FailedException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)
        @Deprecated("InsufficientEntitlementsException is now thrown instead")
        class EntitlementsExceededException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)
        class InsufficientEntitlementsException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)
        class InvalidAddressFilterException(message: String? = null, cause: Throwable? = null) :
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
        class DeprovisionFailedException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)
        class AuthenticationException(message: String? = null, cause: Throwable? = null) :
            EmailAddressException(message = message, cause = cause)
        class UnknownException(cause: Throwable) :
            EmailAddressException(cause = cause)
    }

    /**
     * Defines the exceptions for the email message based methods.
     *
     * @property message Accompanying message for the exception.
     * @property cause The cause for the exception.
     */
    sealed class EmailMessageException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause) {
        class FailedException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)
        class SendFailedException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)
        class InvalidMessageFilterException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)
        class InvalidMessageContentException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)
        class UnauthorizedAddressException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)
        class PublicKeyException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)
        class UnsealingException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)
        class AuthenticationException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)
        class EmailMessageNotFoundException(message: String? = null, cause: Throwable? = null) :
            EmailMessageException(message = message, cause = cause)
        class UnknownException(cause: Throwable) :
            EmailMessageException(cause = cause)
    }

    /**
     * Provision an [EmailAddress].
     *
     * @param emailAddress The email address to provision.
     * @param sudoId Identifier of the [Sudo] used to provision an email address.
     * @return The provisioned [EmailAddress].
     */
    @Throws(EmailAddressException::class)
    suspend fun provisionEmailAddress(emailAddress: String, sudoId: String): EmailAddress

    /**
     * De-provision an [EmailAddress].
     *
     * @param id The identifier of the [EmailAddress] to de-provision.
     * @return The de-provisioned [EmailAddress].
     */
    @Throws(EmailAddressException::class)
    suspend fun deprovisionEmailAddress(id: String): EmailAddress

    /**
     * Send an email message using [RFC 6854] (supersedes RFC 822)(https://tools.ietf.org/html/rfc6854) data.
     *
     * @param rfc822Data Data formatted under the [RFC-6854] (supersedes RFC 822) (https://tools.ietf.org/html/rfc6854) standard.
     * @param senderEmailAddressId Identifier of the [EmailAddress] being used to send the email. The identifier must match the identifier
     * of the address of the `from` field in the RFC 6854 data.
     * @return The identifier of the email message that is being sent.
     */
    @Throws(EmailMessageException::class)
    suspend fun sendEmailMessage(rfc822Data: ByteArray, senderEmailAddressId: String): String

    /**
     * Delete an [EmailMessage] using the [id] parameter.
     *
     * @param id Identifier of the [EmailMessage] to be deleted.
     * @returns The identifier of the [EmailMessage] that was deleted.
     */
    @Throws(EmailMessageException::class)
    suspend fun deleteEmailMessage(id: String): String

    /**
     * Get a list of the supported email domains from the service.
     *
     * @param cachePolicy Determines how the data will be fetched. When using [CachePolicy.CACHE_ONLY],
     * be aware that this will only return cached results of similar exact API calls.
     * @return [List] of supported domains.
     */
    @Throws(EmailAddressException::class)
    suspend fun getSupportedEmailDomains(cachePolicy: CachePolicy = CachePolicy.REMOTE_ONLY): List<String>

    /**
     * Check if an email address is available to be provisioned within a domain.
     *
     * @param localParts A starting point for the email service to search for addresses that match this in part or whole.
     * The [localParts] should only be the local part of an email address without the '@' character or the email domain.
     * @param domains The email domains in which to search for an available address.
     * @return [List] of available email addresses in the [domains] that match the [localParts].
     */
    @Throws(EmailAddressException::class)
    suspend fun checkEmailAddressAvailability(
        localParts: List<String>,
        domains: List<String>
    ): List<String>

    /**
     * Get an [EmailAddress] using the [id] parameter.
     *
     * @param id Identifier of the [EmailAddress] to be retrieved.
     * @param cachePolicy Determines how the data will be fetched. When using [CachePolicy.CACHE_ONLY],
     * be aware that this will only return cached results of similar exact API calls.
     * @return The [EmailAddress] associated with the [id] or null if the email address cannot be found.
     */
    @Throws(EmailAddressException::class)
    suspend fun getEmailAddress(id: String, cachePolicy: CachePolicy = CachePolicy.REMOTE_ONLY): EmailAddress?

    /**
     * Get a [ListOutput] of [EmailAddress]es. If no [EmailAddress]es can be found, the [ListOutput]
     * will contain null for the [ListOutput.nextToken] field and contain an empty list.
     *
     * @param sudoId The identifier of the [Sudo] that owns the [EmailAddress].
     * @param limit Number of [EmailAddress]es to return. If omitted the limit defaults to 10.
     * @param nextToken A token generated from previous calls to [listEmailAddresses].
     * This is to allow for pagination. This value should be generated from a previous
     * pagination call, otherwise it will throw an exception. The same arguments should be
     * supplied to this method if using a previously generated [nextToken].
     * @param cachePolicy Determines how the data will be fetched. When using [CachePolicy.CACHE_ONLY],
     * be aware that this will only return cached results of similar exact API calls.
     * @param filter Filter the email addresses so that only those that match all of the values of the
     * fields in the filter are returned.
     * @return A list of [EmailAddress]es or an empty list if no email addresses can be found.
     */
    @Throws(EmailAddressException::class)
    suspend fun listEmailAddresses(
        sudoId: String? = null,
        limit: Int = DEFAULT_EMAIL_ADDRESS_LIMIT,
        nextToken: String? = null,
        cachePolicy: CachePolicy = CachePolicy.REMOTE_ONLY,
        filter: () -> EmailAddressFilter? = { null }
    ): ListOutput<EmailAddress>

    /**
     * Get an [EmailMessage] using the [id] parameter.
     *
     * @param id Identifier of the [EmailMessage] to be retrieved.
     * @param cachePolicy Determines how the data will be fetched. When using [CachePolicy.CACHE_ONLY],
     * be aware that this will only return cached results of similar exact API calls.
     * @returns The [EmailMessage] associated with the [id] or null if the email message cannot be found.
     */
    @Throws(EmailMessageException::class)
    suspend fun getEmailMessage(id: String, cachePolicy: CachePolicy = CachePolicy.REMOTE_ONLY): EmailMessage?

    /**
     * Get the RFC 6854 (supersedes RFC 822) data of an [EmailMessage] using the [id] parameter.
     *
     * @param id Identifier of the [EmailMessage] data to be retrieved.
     * @param cachePolicy Determines how the data will be fetched. When using [CachePolicy.CACHE_ONLY],
     * be aware that this will only return cached results of similar exact API calls.
     * @returns The data associated with the [EmailMessage] with the unique [id] or null if the email message cannot be found.
     */
    @Throws(EmailMessageException::class)
    suspend fun getEmailMessageRfc822Data(id: String, cachePolicy: CachePolicy = CachePolicy.REMOTE_ONLY): ByteArray?

    /**
     * Get a [ListOutput] of [EmailMessage]s. If no email messages can be found, an empty list will be returned.
     *
     * @param emailAddressId The identifier of the [EmailAddress] that is associated with the [EmailMessage]. Note that when supplying an
     * [emailAddressId], a [sudoId] must also be provided.
     * @param sudoId The identifier of the [Sudo] that owns the [EmailAddress].
     * @param limit Number of email messages to return. If omitted the limit is 10.
     * @param nextToken Generated token by previous calls to [listEmailMessages]. This is used for pagination. This value should
     * have been generated from a previous pagination call, otherwise it will throw an error. It is important to note that the
     * same arguments should be used if using a when using a previously generated [nextToken].
     * @param cachePolicy Determines how the data is fetched. When using [CachePolicy.CACHE_ONLY], please be aware that this
     * will only return cached results of similar exact API calls.
     * @param filter Filter to be applied to results of query.
     * @return Email messages associated with the user, or an empty list if no email message can be found.
     */
    @Throws(EmailMessageException::class)
    suspend fun listEmailMessages(
        emailAddressId: String? = null,
        sudoId: String? = null,
        limit: Int = DEFAULT_EMAIL_MESSAGE_LIMIT,
        nextToken: String? = null,
        cachePolicy: CachePolicy = CachePolicy.REMOTE_ONLY,
        filter: () -> EmailMessageFilter? = { null }
    ): ListOutput<EmailMessage>

    /**
     * Subscribes to be notified of new and deleted [EmailMessage]s.
     *
     * @param id Unique ID for the subscriber.
     * @param subscriber Subscriber to notify.
     */
    @Throws(EmailMessageException.AuthenticationException::class)
    suspend fun subscribeToEmailMessages(id: String, subscriber: EmailMessageSubscriber)

    /**
     * Unsubscribe the specified subscriber so that it no longer receives notifications about
     * new or deleted [EmailMessage]s.
     *
     * @param id Unique ID for the subscriber.
     */
    suspend fun unsubscribeFromEmailMessages(id: String)

    /**
     * Unsubscribe all subscribers from receiving notifications about new or deleted [EmailMessage]s.
     */
    suspend fun unsubscribeAll()

    /**
     * Reset any internal state and cached content.
     */
    fun reset()
}

/**
 * Subscribes to be notified of new and deleted [EmailMessage]s.
 *
 * @param id Unique ID for the subscriber.
 * @param onConnectionChange Lambda that is called when the subscription connection state changes.
 * @param onEmailMessageCreated Lambda that receives new [EmailMessage]s.
 * @param onEmailMessageDeleted Lambda that receives deleted [EmailMessage]s.
 */
@Throws(SudoEmailClient.EmailMessageException::class)
suspend fun SudoEmailClient.subscribeToEmailMessages(
    id: String,
    onConnectionChange: (status: EmailMessageSubscriber.ConnectionState) -> Unit = {},
    onEmailMessageCreated: (emailMessage: EmailMessage) -> Unit,
    onEmailMessageDeleted: (emailMessage: EmailMessage) -> Unit
) =
    subscribeToEmailMessages(
        id,
        object : EmailMessageSubscriber {
            override fun connectionStatusChanged(state: EmailMessageSubscriber.ConnectionState) {
                onConnectionChange.invoke(state)
            }

            override fun emailMessageCreated(emailMessage: EmailMessage) {
                onEmailMessageCreated.invoke(emailMessage)
            }

            override fun emailMessageDeleted(emailMessage: EmailMessage) {
                onEmailMessageDeleted.invoke(emailMessage)
            }
        }
    )
