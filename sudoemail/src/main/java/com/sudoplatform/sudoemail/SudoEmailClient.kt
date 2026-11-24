/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoconfigmanager.DefaultSudoConfigManager
import com.sudoplatform.sudoconfigmanager.SudoConfigManager
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultEmailCryptoService
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.subscription.EmailMessageSubscriber
import com.sudoplatform.sudoemail.subscription.Subscriber
import com.sudoplatform.sudoemail.types.BatchOperationResult
import com.sudoplatform.sudoemail.types.BlockedEmailAddressAction
import com.sudoplatform.sudoemail.types.BlockedEmailAddressLevel
import com.sudoplatform.sudoemail.types.ConfigurationData
import com.sudoplatform.sudoemail.types.CreateSingletonKeysIfAbsentResult
import com.sudoplatform.sudoemail.types.DeleteEmailMessageSuccessResult
import com.sudoplatform.sudoemail.types.DraftEmailMessageMetadata
import com.sudoplatform.sudoemail.types.DraftEmailMessageWithContent
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailAddressPublicInfo
import com.sudoplatform.sudoemail.types.EmailAlias
import com.sudoplatform.sudoemail.types.EmailAliasListItem
import com.sudoplatform.sudoemail.types.EmailDomain
import com.sudoplatform.sudoemail.types.EmailFolder
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.EmailMessageOperationFailureResult
import com.sudoplatform.sudoemail.types.EmailMessageWithBody
import com.sudoplatform.sudoemail.types.ListAPIResult
import com.sudoplatform.sudoemail.types.ListOutput
import com.sudoplatform.sudoemail.types.PartialEmailAddress
import com.sudoplatform.sudoemail.types.PartialEmailAlias
import com.sudoplatform.sudoemail.types.PartialEmailAliasListItem
import com.sudoplatform.sudoemail.types.PartialEmailMessage
import com.sudoplatform.sudoemail.types.ScheduledDraftMessage
import com.sudoplatform.sudoemail.types.SendEmailMessageResult
import com.sudoplatform.sudoemail.types.UnsealedBlockedAddress
import com.sudoplatform.sudoemail.types.UpdatedEmailMessageResult.UpdatedEmailMessageSuccess
import com.sudoplatform.sudoemail.types.VerifyExternalEmailAddressResult
import com.sudoplatform.sudoemail.types.inputs.CancelScheduledDraftMessageInput
import com.sudoplatform.sudoemail.types.inputs.CheckEmailAddressAvailabilityInput
import com.sudoplatform.sudoemail.types.inputs.CreateCustomEmailFolderInput
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.DeleteCustomEmailFolderInput
import com.sudoplatform.sudoemail.types.inputs.DeleteDraftEmailMessagesInput
import com.sudoplatform.sudoemail.types.inputs.DeleteMessagesForFolderIdInput
import com.sudoplatform.sudoemail.types.inputs.DeprovisionEmailAliasInput
import com.sudoplatform.sudoemail.types.inputs.DisableEmailAliasInput
import com.sudoplatform.sudoemail.types.inputs.EnableEmailAliasInput
import com.sudoplatform.sudoemail.types.inputs.FlushMessageBodyCacheInput
import com.sudoplatform.sudoemail.types.inputs.GetDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailAddressInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageWithBodyInput
import com.sudoplatform.sudoemail.types.inputs.ListDraftEmailMessageMetadataForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ListDraftEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesForSudoIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailAddressesInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailAliasesForOwnerInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailAliasesInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailFoldersForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoemail.types.inputs.ListEmailMessagesInput
import com.sudoplatform.sudoemail.types.inputs.ListScheduledDraftMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.LookupEmailAddressesPublicInfoInput
import com.sudoplatform.sudoemail.types.inputs.ProvisionEmailAddressInput
import com.sudoplatform.sudoemail.types.inputs.ProvisionEmailAliasInput
import com.sudoplatform.sudoemail.types.inputs.ScheduleSendDraftMessageInput
import com.sudoplatform.sudoemail.types.inputs.SendEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.SendEmailMessageViaAliasInput
import com.sudoplatform.sudoemail.types.inputs.UpdateCustomEmailFolderInput
import com.sudoplatform.sudoemail.types.inputs.UpdateDraftEmailMessageInput
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailAddressMetadataInput
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailAliasInput
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailMessagesInput
import com.sudoplatform.sudoemail.types.inputs.VerifyExternalEmailAddressInput
import com.sudoplatform.sudokeymanager.AndroidSQLiteStore
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoPlatformSignInCallback
import com.sudoplatform.sudouser.SudoUserClient
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

        /**
         * Default namespace the client's keys are stored under. [SudoEmailClient] and
         * [SudoEmailNotifiableClient] must use the same namespace for notifications to be
         * decrypted.
         *
         * Every platform uses this namespace, which key archives need in order to import across
         * platforms. Keys stored by an earlier version of the SDK under its old default, `"eml"`,
         * aren't found under this one; pass that namespace to [Builder.setNamespace] and
         * [SudoEmailNotifiableClient.Builder.setNamespace] to keep using them.
         */
        const val DEFAULT_KEY_NAMESPACE = "sudo-email"
        const val DEFAULT_KEYRING_SERVICE_NAME = "sudo-email"

        private const val CONFIG_EMAIL_SERVICE = "emService"
        private const val CONFIG_REGION = "region"
        private const val CONFIG_EMAIL_BUCKET = "bucket"
        private const val CONFIG_EMAIL_TRANSIENT_BUCKET = "transientBucket"
        private const val CONFIG_TEST_SENT_EMAIL_BUCKET = "testSentEmailBucket"

        internal data class S3Configuration(
            val region: String,
            val emailBucket: String,
            val transientBucket: String,
            val testSentEmailBucket: String? = null,
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

            val emailConfig =
                try {
                    configManager.getConfigSet(CONFIG_EMAIL_SERVICE)
                } catch (e: JSONException) {
                    throw Builder.ConfigurationException("$preamble $postamble", e)
                }
            emailConfig
                ?: throw Builder.ConfigurationException("$preamble $CONFIG_EMAIL_TRANSIENT_BUCKET in $postamble")

            val region =
                try {
                    emailConfig.getString(CONFIG_REGION)
                } catch (e: JSONException) {
                    throw Builder.ConfigurationException("$preamble $CONFIG_REGION in $postamble", e)
                }

            val emailBucket =
                try {
                    emailConfig.getString(CONFIG_EMAIL_BUCKET)
                } catch (e: JSONException) {
                    throw Builder.ConfigurationException(
                        "$preamble $CONFIG_EMAIL_BUCKET in $postamble",
                        e,
                    )
                }

            val emailTransientBucket =
                try {
                    emailConfig.getString(CONFIG_EMAIL_TRANSIENT_BUCKET)
                } catch (e: JSONException) {
                    throw Builder.ConfigurationException(
                        "$preamble $CONFIG_EMAIL_TRANSIENT_BUCKET in $postamble",
                        e,
                    )
                }

            val testSentEmailBucket =
                try {
                    emailConfig.getString(CONFIG_TEST_SENT_EMAIL_BUCKET)
                } catch (e: JSONException) {
                    logger.debug("Optional configuration $CONFIG_TEST_SENT_EMAIL_BUCKET not found in $postamble")
                    null
                }

            return S3Configuration(region, emailBucket, emailTransientBucket, testSentEmailBucket)
        }
    }

    /**
     * Builder used to construct the [SudoEmailClient].
     */
    class Builder internal constructor() {
        private var context: Context? = null
        private var sudoUserClient: SudoUserClient? = null
        private var apiClient: ApiClient? = null
        private var keyManager: KeyManagerInterface? = null
        private var s3Client: S3Client? = null
        private var logger: Logger =
            Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))
        private var namespace: String = DEFAULT_KEY_NAMESPACE
        private var notificationHandler: SudoEmailNotificationHandler? = null
        private var databaseName: String = AndroidSQLiteStore.DEFAULT_DATABASE_NAME
        private var enforceSingletonKeyPair: Boolean = false
        private var allowCallerManagedKeys: Boolean = true

        /**
         * Provide the application context (required input).
         */
        fun setContext(context: Context) =
            also {
                this.context = context
            }

        /**
         * Provide the implementation of the [SudoUserClient] used to perform
         * sign in and ownership operations (required input).
         */
        fun setSudoUserClient(sudoUserClient: SudoUserClient) =
            also {
                this.sudoUserClient = sudoUserClient
            }

        /**
         * Provide a [ApiClient] for the [SudoEmailClient] to use
         * (optional input). If this is not supplied, an [ApiClient] will
         * be constructed and used.
         */
        fun setApiClient(apiClient: ApiClient) =
            also {
                this.apiClient = apiClient
            }

        fun setS3Client(s3Client: S3Client) =
            also {
                this.s3Client = s3Client
            }

        /**
         * Provide the implementation of the [KeyManagerInterface] used for key management and
         * cryptographic operations (optional input). If a value is not supplied a default
         * implementation will be used.
         */
        fun setKeyManager(keyManager: KeyManagerInterface) =
            also {
                this.keyManager = keyManager
            }

        /**
         * Provide the implementation of the [Logger] used for logging errors (optional input).
         * If a value is not supplied a default implementation will be used.
         */
        fun setLogger(logger: Logger) =
            also {
                this.logger = logger
            }

        /**
         * Provide the namespace to use for internal data and cryptographic keys. This should be unique
         * per client per app to avoid name conflicts between multiple clients. If a value is not supplied
         * [DEFAULT_KEY_NAMESPACE] will be used. Key archives record it and import only into a client
         * with the same namespace, so use the same value on every device and platform that keys move
         * between.
         */
        fun setNamespace(namespace: String) =
            also {
                this.namespace = namespace
            }

        fun setNotificationHandler(notificationHandler: SudoEmailNotificationHandler) =
            also {
                this.notificationHandler = notificationHandler
            }

        /**
         * Provide the database name to use for exportable key store database.
         */
        fun setDatabaseName(databaseName: String) =
            also {
                this.databaseName = databaseName
            }

        /**
         * Provide whether every email address and email alias is provisioned with one shared key
         * pair instead of a key pair of its own (optional input). Defaults to `false`.
         *
         * When `true`, every email address and email alias provisioned without a `keyId` is
         * registered with one shared key pair, which the app creates with
         * [createSingletonKeysIfAbsent] before provisioning. The client never creates it itself:
         * provisioning without it throws
         * [EmailAddressException.SingletonKeyPairNotFoundException] or
         * [EmailAliasException.SingletonKeyPairNotFoundException]. Deprovisioning never removes
         * a key pair, even with [setAllowCallerManagedKeys] set to `false`, and the shared key
         * pair is never removed when a provisioning fails; only [reset] removes it.
         *
         * Messages are still sealed, but every email address's messages are sealed to the same
         * key, so all of them can be read with that one key. Apps that don't need email addresses
         * kept cryptographically separate from one another can use this to keep key management
         * and key export and import simple.
         *
         * When `false`, each email address and email alias provisioned without a `keyId` gets a
         * key pair of its own, which is removed from the device when the service rejects its
         * provisioning. See [setAllowCallerManagedKeys] for removing it after a deprovision.
         *
         * Either way, a `keyId` supplied when provisioning is used instead, if
         * [setAllowCallerManagedKeys] is `true`.
         */
        fun setEnforceSingletonKeyPair(enforceSingletonKeyPair: Boolean) =
            also {
                this.enforceSingletonKeyPair = enforceSingletonKeyPair
            }

        /**
         * Provide whether the app may supply the key pair an email address or email alias is
         * provisioned with, as the `keyId` of [ProvisionEmailAddressInput] or
         * [ProvisionEmailAliasInput] (optional input). Defaults to `true`.
         *
         * When `true`, the app is responsible for its key pairs, so deprovisioning never removes
         * one: key pairs stay on the device until [reset], including those the client generated.
         *
         * When `false`, the client manages every key pair itself: provisioning with a `keyId`
         * throws [EmailAddressException.InvalidArgumentException] or
         * [EmailAliasException.InvalidArgumentException], and deprovisioning an email address
         * removes its key pairs from the device once no other email address or email alias uses
         * them; see [deprovisionEmailAddress]. Deprovisioning removes nothing while
         * [setEnforceSingletonKeyPair] is `true`.
         */
        fun setAllowCallerManagedKeys(allowCallerManagedKeys: Boolean) =
            also {
                this.allowCallerManagedKeys = allowCallerManagedKeys
            }

        /** A configuration item that is needed is missing */
        class ConfigurationException(
            message: String,
            cause: Throwable? = null,
        ) : RuntimeException(message, cause)

        /**
         * Construct the [SudoEmailClient]. Will throw a [NullPointerException] if
         * the [context] and [sudoUserClient] has not been provided.
         */
        @Throws(NullPointerException::class, ConfigurationException::class)
        fun build(): SudoEmailClient {
            Objects.requireNonNull(context, "Context must be provided.")
            Objects.requireNonNull(sudoUserClient, "SudoUserClient must be provided.")

            val client =
                apiClient ?: ApiClient(
                    ApiClientManager.getClient(
                        this@Builder.context!!,
                        this@Builder.sudoUserClient!!,
                        CONFIG_EMAIL_SERVICE,
                    ),
                    logger,
                )

            val serviceKeyManager =
                DefaultServiceKeyManager(
                    keyRingServiceName = DEFAULT_KEYRING_SERVICE_NAME,
                    userClient = sudoUserClient!!,
                    keyManager =
                        keyManager ?: KeyManagerFactory(context!!).createAndroidKeyManager(
                            this.namespace,
                            this.databaseName,
                        ),
                )

            val sealingService =
                DefaultSealingService(
                    deviceKeyManager = serviceKeyManager,
                    logger = logger,
                )

            val emailCryptoService =
                DefaultEmailCryptoService(
                    deviceKeyManager = serviceKeyManager,
                    logger = logger,
                )

            val (region, emailBucket, transientBucket) = readConfiguration(context!!, logger)

            return DefaultSudoEmailClient(
                context = context!!,
                apiClient = client,
                sudoUserClient = sudoUserClient!!,
                logger = logger,
                serviceKeyManager = serviceKeyManager,
                region = region,
                emailBucket = emailBucket,
                transientBucket = transientBucket,
                sealingService = sealingService,
                emailCryptoService = emailCryptoService,
                notificationHandler = notificationHandler,
                s3TransientClient = s3Client ?: DefaultS3Client(context!!, sudoUserClient!!, region, transientBucket, logger),
                s3EmailClient = s3Client ?: DefaultS3Client(context!!, sudoUserClient!!, region, emailBucket, logger),
                enforceSingletonKeyPair = enforceSingletonKeyPair,
                allowCallerManagedKeys = allowCallerManagedKeys,
            )
        }
    }

    /**
     * Defines the exceptions for the email address based methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class EmailAddressException(
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        class FailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        class InsufficientEntitlementsException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        class ProvisionFailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        class InvalidEmailAddressException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        class EmailAddressNotFoundException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        class UnavailableEmailAddressException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        class UnauthorizedEmailAddressException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        class PublicKeyException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        class UnsealingException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        class DeprovisionFailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        class UpdateFailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        class AuthenticationException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        class LimitExceededException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        /**
         * An argument is not valid, for example a [ProvisionEmailAddressInput.keyId] supplied to a
         * client built with [Builder.setAllowCallerManagedKeys] set to `false`.
         */
        class InvalidArgumentException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        /**
         * The client enforces a singleton key pair, but this device does not have it. Call
         * [createSingletonKeysIfAbsent] to create it, or import a key archive that contains it
         * with [importKeys] (for example on a second device, before provisioning).
         */
        class SingletonKeyPairNotFoundException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        /**
         * The email address was deprovisioned, but this device could not remove some of its key
         * pairs. Only thrown by a client built with [Builder.setAllowCallerManagedKeys] set to
         * `false`. Treat the email address as deprovisioned: [emailAddress] is the email address,
         * as a successful [deprovisionEmailAddress] would have returned it. The key pairs in
         * [keyIds] stay on the device until [reset]. Removing a key pair that is already absent
         * succeeds, so this means the device key store itself failed, and other key operations
         * are likely to fail too. The [cause] carries the first underlying error.
         *
         * @property emailAddress [EmailAddress] The deprovisioned email address.
         * @property keyIds [List<String>] Identifiers of the key pairs that could not be removed.
         */
        class KeyPairRemovalFailedException(
            val emailAddress: EmailAddress,
            val keyIds: List<String>,
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAddressException(message = message, cause = cause)

        class UnknownException(
            cause: Throwable,
        ) : EmailAddressException(cause = cause)
    }

    /**
     * Defines the exceptions for the email folder based methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class EmailFolderException(
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        class FailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailFolderException(message = message, cause = cause)

        class AuthenticationException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailFolderException(message = message, cause = cause)

        class UnknownException(
            cause: Throwable,
        ) : EmailFolderException(cause = cause)
    }

    /**
     * Defines the exceptions for the email blocklist based methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class EmailBlocklistException(
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        class InvalidInputException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailBlocklistException(message = message, cause = cause)

        class FailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailBlocklistException(message = message, cause = cause)

        class UnknownException(
            cause: Throwable? = null,
        ) : EmailBlocklistException(cause = cause)
    }

    /**
     * Defines the exceptions for the email alias based methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class EmailAliasException(
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        class FailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAliasException(message = message, cause = cause)

        class InvalidArgumentException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAliasException(message = message, cause = cause)

        class ProvisionFailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAliasException(message = message, cause = cause)

        class DeprovisionFailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAliasException(message = message, cause = cause)

        class UpdateFailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAliasException(message = message, cause = cause)

        class InvalidEmailAddressException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAliasException(message, cause)

        class UnavailableEmailAddressException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAliasException(message = message, cause = cause)

        class EmailAliasAlreadyExistsException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAliasException(message = message, cause = cause)

        class EmailAliasLockedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAliasException(message = message, cause = cause)

        class UnsealingException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAliasException(message = message, cause = cause)

        class EmailAliasNotFoundException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAliasException(message = message, cause = cause)

        class PublicKeyException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAliasException(message = message, cause = cause)

        class InsufficientEntitlementsException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAliasException(message = message, cause = cause)

        /**
         * The client enforces a singleton key pair, but this device does not have it. Call
         * [createSingletonKeysIfAbsent] to create it, or import a key archive that contains it
         * with [importKeys] (for example on a second device, before provisioning).
         */
        class SingletonKeyPairNotFoundException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAliasException(message = message, cause = cause)

        class AuthenticationException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailAliasException(message = message, cause = cause)

        class UnknownException(
            cause: Throwable,
        ) : EmailAliasException(cause = cause)
    }

    /**
     * Defines the exceptions for the email message based methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class EmailMessageException(
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        class FailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailMessageException(message = message, cause = cause)

        class SendFailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailMessageException(message = message, cause = cause)

        class InvalidMessageContentException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailMessageException(message = message, cause = cause)

        class UnauthorizedAddressException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailMessageException(message = message, cause = cause)

        class InNetworkAddressNotFoundException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailMessageException(message = message, cause = cause)

        class UnsealingException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailMessageException(message = message, cause = cause)

        class AuthenticationException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailMessageException(message = message, cause = cause)

        class EmailMessageNotFoundException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailMessageException(message = message, cause = cause)

        class LimitExceededException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailMessageException(message = message, cause = cause)

        class EmailMessageSizeLimitExceededException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailMessageException(message = message, cause = cause)

        class InvalidArgumentException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailMessageException(message = message, cause = cause)

        class RecordNotFoundException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailMessageException(message = message, cause = cause)

        class UnknownException(
            cause: Throwable,
        ) : EmailMessageException(cause = cause)
    }

    /**
     * Defines the exceptions for the email configuration based methods.
     *
     * @property message [String] Accompanying message for the exception.
     * @property cause [Throwable] The cause for the exception.
     */
    sealed class EmailConfigurationException(
        message: String? = null,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause) {
        class FailedException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailConfigurationException(message = message, cause = cause)

        class UnknownException(
            cause: Throwable,
        ) : EmailConfigurationException(cause = cause)
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
        class SecureKeyArchiveException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailCryptographicKeysException(message = message, cause = cause)

        /**
         * The client's configuration does not support the operation, for example
         * [createSingletonKeysIfAbsent] on a client built without
         * [Builder.setEnforceSingletonKeyPair].
         */
        class InvalidConfigurationException(
            message: String? = null,
            cause: Throwable? = null,
        ) : EmailCryptographicKeysException(message = message, cause = cause)

        /** An unexpected error wrapping the underlying cause. */
        class UnknownException(
            cause: Throwable,
        ) : EmailCryptographicKeysException(cause = cause)
    }

    /**
     * A suspend function invoked when an operation is attempted while the user is not signed in.
     * Implementations should perform any necessary sign-in and return normally on success,
     * or throw an exception to abort the operation.
     */
    fun interface SignInCallback {
        suspend fun invoke()
    }

    /**
     * Sets an optional callback to be invoked when operations are attempted while not signed in.
     * When set, all operations (except subscriptions and initialization) will check sign-in status
     * and invoke this callback if the user is not signed in.
     * Setting the callback to null disables automatic sign-in checking, restoring the default
     * behavior where operations proceed without sign-in checks.
     * If the callback throws an exception, that exception is propagated
     * to the caller and the original operation is not executed.
     *
     * @param callback An optional [SudoPlatformSignInCallback] to handle sign-in. Pass null to disable
     *                 automatic sign-in checking. The callback may throw exceptions which
     *                 will be propagated to the caller.
     */
    fun setSignInCallback(callback: SudoPlatformSignInCallback?)

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
     * Get a list of all email domains with their associated metadata.
     *
     * @return A list of [EmailDomain]s.
     *
     * @throws [EmailAddressException].
     */
    @Throws(EmailAddressException::class)
    suspend fun listEmailDomains(): List<EmailDomain>

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
     * The email address is registered with the key pair [ProvisionEmailAddressInput.keyId] names
     * if one is supplied, otherwise with the singleton key pair if the client was built with
     * [Builder.setEnforceSingletonKeyPair], otherwise with a new key pair of its own. A new key
     * pair is removed again if the service rejects the provisioning.
     *
     * @param input [ProvisionEmailAddressInput] Parameters used to provision an email address.
     * @return The provisioned [EmailAddress].
     *
     * @throws [EmailAddressException], including
     *  [EmailAddressException.InvalidArgumentException] if a `keyId` is supplied to a client built
     *  with [Builder.setAllowCallerManagedKeys] set to `false`, and
     *  [EmailAddressException.SingletonKeyPairNotFoundException] if the client enforces a
     *  singleton key pair that this device does not have.
     */
    @Throws(EmailAddressException::class)
    suspend fun provisionEmailAddress(input: ProvisionEmailAddressInput): EmailAddress

    /**
     * Deprovision an [EmailAddress].
     *
     * With [Builder.setAllowCallerManagedKeys] set to `false`, once the email address is
     * deprovisioned its key pair is removed from the device, unless another of the user's email
     * addresses or email aliases still uses it. Nothing is removed if the client was built with
     * [Builder.setEnforceSingletonKeyPair], or if the service can't say whether the key pair is
     * still used. By default, with [Builder.setAllowCallerManagedKeys] set to `true`,
     * deprovisioning never removes a key pair. Deprovisioning an email alias never removes a key
     * pair.
     *
     * @param id [String] The identifier of the [EmailAddress] to deprovision.
     * @return The deprovisioned [EmailAddress].
     *
     * @throws [EmailAddressException] if the email address was not deprovisioned, or
     *  [EmailAddressException.KeyPairRemovalFailedException] if it was deprovisioned but its key
     *  pair could not be removed from the device (only with [Builder.setAllowCallerManagedKeys]
     *  set to `false`).
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
     * Delete a custom named [EmailFolder].
     * Any messages in the custom folder will be moved to TRASH.
     *
     * @param input [DeleteCustomEmailFolderInput] Parameters used to delete the folder.
     * @return The deleted [EmailFolder] or undefined if it was not found.
     *
     * @throws [EmailFolderException]
     */
    @Throws(EmailFolderException::class)
    suspend fun deleteCustomEmailFolder(input: DeleteCustomEmailFolderInput): EmailFolder?

    /**
     * Update a custom named [EmailFolder].
     *
     * @param input [UpdateCustomEmailFolderInput] Parameters used to update the folder.
     * @return The updated [EmailFolder].
     *
     * @throws [EmailFolderException]
     */
    @Throws(EmailFolderException::class)
    suspend fun updateCustomEmailFolder(input: UpdateCustomEmailFolderInput): EmailFolder

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
     * Send an email message using RFC 6854 (supersedes RFC 822)(https://tools.ietf.org/html/rfc6854) data via an email alias.
     *
     * Email messages sent to in-network recipients (i.e. email addresses that exist within the Sudo Platform)
     * will be sent end-to-end encrypted.
     *
     * @param input [SendEmailMessageViaAliasInput] Parameters used to send an email message via an email alias.
     * @return The identifier of the [EmailMessage] that is being sent.
     *
     * @throws [EmailMessageException].
     */
    @Throws(EmailMessageException::class)
    suspend fun sendEmailMessageViaAlias(input: SendEmailMessageViaAliasInput): SendEmailMessageResult

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
    suspend fun updateEmailMessages(
        input: UpdateEmailMessagesInput,
    ): BatchOperationResult<
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
    suspend fun listEmailMessages(input: ListEmailMessagesInput): ListAPIResult<EmailMessage, PartialEmailMessage>

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
     * Lists the metadata and content of draft messages for the specified email address identifier with pagination support.
     *
     * @param input [ListDraftEmailMessagesForEmailAddressIdInput] The input object containing the email address ID and pagination parameters.
     * @return [ListOutput] containing the list of [DraftEmailMessageWithContent] and optional next token.
     *
     * @throws [EmailMessageException].
     */
    @Throws(EmailMessageException::class)
    suspend fun listDraftEmailMessagesForEmailAddressId(
        input: ListDraftEmailMessagesForEmailAddressIdInput,
    ): ListOutput<DraftEmailMessageWithContent>

    /**
     * Lists the metadata of all draft messages for the specified email address identifier with pagination support.
     *
     * @param input [ListDraftEmailMessageMetadataForEmailAddressIdInput] The input containing email address ID and pagination parameters.
     * @return [ListOutput] The list of draft email message metadata with optional next token for pagination.
     * @throws [EmailMessageException].
     */
    @Throws(EmailMessageException::class)
    suspend fun listDraftEmailMessageMetadataForEmailAddressId(
        input: ListDraftEmailMessageMetadataForEmailAddressIdInput,
    ): ListOutput<DraftEmailMessageMetadata>

    /**
     * Schedule a draft message to be sent at a specified time in the future.
     *
     * @param input [ScheduleSendDraftMessageInput] The parameters needed to schedule a draft message to be sent.
     * @return Record of the ScheduledDraftMessage that has been created.
     *
     * @Throws [EmailMessageException].
     */
    @Throws(EmailMessageException::class)
    suspend fun scheduleSendDraftMessage(input: ScheduleSendDraftMessageInput): ScheduledDraftMessage

    /**
     * Cancel a scheduled draft message. If the scheduled draft cannot be found, an error will be thrown.
     *
     * @param input [CancelScheduledDraftMessageInput] The parameters needed to cancel a scheduled draft message.
     * @return The id of the scheduled draft message
     *
     * @Throws [EmailMessageException]
     */
    @Throws(EmailMessageException::class)
    suspend fun cancelScheduledDraftMessage(input: CancelScheduledDraftMessageInput): String

    /**
     * List scheduled draft messages associated with an email address.
     * @param input [ListScheduledDraftMessagesForEmailAddressIdInput]  Parameters used to list scheduled draft messages for an email address
     * @returns [ListOutput<ScheduledDraftMessage>]
     */
    @Throws(EmailMessageException::class)
    suspend fun listScheduledDraftMessagesForEmailAddressId(
        input: ListScheduledDraftMessagesForEmailAddressIdInput,
    ): ListOutput<ScheduledDraftMessage>

    /**
     * Blocks the given email address(es) for the user identified
     *
     * @param addresses [List<String>] The list of email addresses to block
     * @param action [BlockedEmailAddressAction] (optional) The action to take on incoming messages from the blocked address(es). Defaults to DROP
     * @property emailAddressId [String] If present, the blocked address is only blocked for this email address.
     * @property level [BlockedEmailAddressLevel] (optional) The level at which to block the address. Defaults to ADDRESS
     * @return A success, partial or failed [BatchOperationResult] result containing either a list of identifiers
     *  of email addresses that succeeded or failed to be blocked.
     */
    suspend fun blockEmailAddresses(
        addresses: List<String>,
        action: BlockedEmailAddressAction? = BlockedEmailAddressAction.DROP,
        emailAddressId: String? = null,
        level: BlockedEmailAddressLevel? = BlockedEmailAddressLevel.ADDRESS,
    ): BatchOperationResult<String, String>

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
     * Delete all messages in an email folder.
     * Deletion will be processed asynchronously since it may take a substantial amount of time.
     * This method does not wait for deletion to complete. To check for completion, listen for subscriptions or check list endpoints.
     *
     * @param input [DeleteMessagesForFolderIdInput] Input values needed to delete messages for a folder
     * @return [String] The id of the folder
     */
    suspend fun deleteMessagesForFolderId(input: DeleteMessagesForFolderIdInput): String

    /**
     * Import cryptographic keys from a key archive.
     *
     * Every key already on this device is removed first, so the store ends up matching the
     * archive rather than merging with it.
     *
     * The archive's keys must all be in this client's key namespace ([Builder.setNamespace]), so a
     * client on another device or platform must use the same namespace. If the archive has no keys
     * for this client's namespace, or has keys for another namespace, the import fails and the
     * existing keys are kept.
     *
     * @param archiveData [ByteArray] Key archive data to import the keys from.
     *
     * @throws [EmailCryptographicKeysException] if the archive is empty or cannot be read, holds no
     *  keys for this client's namespace, or holds keys for another namespace. The existing keys are
     *  kept.
     */
    @Throws(EmailCryptographicKeysException::class)
    suspend fun importKeys(archiveData: ByteArray)

    /**
     * Export the cryptographic keys to a key archive.
     *
     * The archive records this client's key namespace, and imports only into a client with the
     * same namespace.
     *
     * @returns Key archive data.
     *
     * @throws [EmailCryptographicKeysException]
     */
    @Throws(EmailCryptographicKeysException::class)
    suspend fun exportKeys(): ByteArray

    /**
     * Create the singleton key pair every email address and email alias is sealed to, if this
     * device does not have it.
     *
     * Requires a client built with [Builder.setEnforceSingletonKeyPair]. Call it once the user is
     * signed in, before provisioning: provisioning never creates the key, and fails with
     * [EmailAddressException.SingletonKeyPairNotFoundException] or
     * [EmailAliasException.SingletonKeyPairNotFoundException] without it.
     *
     * The result says whether the key had to be created. If it was, the device's key material has
     * changed, so back the keys up again with [exportKeys]. If the user already has email
     * addresses or email aliases, a new key also means this device does not have the one they are
     * sealed to (for example a second device, or one whose keys were removed), so import the
     * user's key backup with [importKeys].
     *
     * @return Whether the singleton key pair was created, and its identifier.
     * @throws EmailCryptographicKeysException.InvalidConfigurationException if the client does not
     *  enforce a singleton key pair; otherwise [EmailCryptographicKeysException] if the key cannot
     *  be created.
     */
    @Throws(EmailCryptographicKeysException::class)
    suspend fun createSingletonKeysIfAbsent(): CreateSingletonKeysIfAbsentResult

    /**
     * Provisions a new email alias with the specified configuration.
     *
     * The key pair is chosen as for [provisionEmailAddress]: the supplied
     * [ProvisionEmailAliasInput.keyId], otherwise the singleton key pair if the client enforces
     * one, otherwise a new key pair of its own. Deprovisioning an email alias never removes a key
     * pair.
     *
     * @param {ProvisionEmailAliasInput} input The input parameters for provisioning the email alias
     * @returns {EmailAlias} The newly created email alias
     * @throws [EmailAliasException], including
     *  [EmailAliasException.InvalidArgumentException] if a `keyId` is supplied to a client built
     *  with [Builder.setAllowCallerManagedKeys] set to `false`, and
     *  [EmailAliasException.SingletonKeyPairNotFoundException] if the client enforces a singleton
     *  key pair that this device does not have.
     */
    suspend fun provisionEmailAlias(input: ProvisionEmailAliasInput): EmailAlias

    /**
     * Deprovisions an existing email alias, permanently removing it from the system.
     *
     * @param {DeprovisionEmailAliasInput} input The input parameters for deprovisioning the email alias
     * @returns {EmailAlias} The deprovisioned email alias
     */
    suspend fun deprovisionEmailAlias(input: DeprovisionEmailAliasInput): PartialEmailAlias

    /**
     * Updates or clears the metadata or expiration date of an existing email alias.
     *
     * @param {UpdateEmailAliasInput} input The input parameters for updating the email alias
     * @returns {EmailAlias} A promise that resolves to the updated email alias
     */
    suspend fun updateEmailAlias(input: UpdateEmailAliasInput): EmailAlias

    /**
     * Enables a previously disabled email alias, allowing it to forward emails again.
     *
     * @param {EnableEmailAliasInput} input The input parameters for enabling the email alias
     * @returns {EmailAlias} A promise that resolves to the enabled email alias
     */
    suspend fun enableEmailAlias(input: EnableEmailAliasInput): EmailAlias

    /**
     * Disables an active email alias, preventing it from forwarding emails.
     *
     * @param {DisableEmailAliasInput} input The input parameters for disabling the email alias
     * @returns {EmailAlias} A promise that resolves to the disabled email alias
     */
    suspend fun disableEmailAlias(input: DisableEmailAliasInput): EmailAlias

    /**
     * Lists email aliases owned by the current user, with optional filtering and pagination.
     *
     * @param {ListEmailAliasesForOwnerInput} input The input parameters for filtering and pagination
     * @returns {ListAPIResult<EmailAlias, PartialEmailAlias>} A promise that resolves to a list of email aliases and pagination info
     */
    @Deprecated("Deprecated", replaceWith = ReplaceWith("listEmailAliases"))
    suspend fun listEmailAliasesForOwner(input: ListEmailAliasesForOwnerInput): ListAPIResult<EmailAlias, PartialEmailAlias>

    /**
     * Lists email aliases owned by the current user, with optional filtering and pagination.
     *
     * @param {ListEmailAliasesInput} input The input parameters for filtering and pagination
     * @returns {ListAPIResult<EmailAliasListItem, PartialEmailAliasListItem>} A promise that resolves to a list of email aliases and pagination info
     */
    suspend fun listEmailAliases(input: ListEmailAliasesInput): ListAPIResult<EmailAliasListItem, PartialEmailAliasListItem>

    /**
     * Retrieves the email alias associated with the given unique identifier.
     *
     * @param id [String] The unique identifier of the email alias to retrieve
     * @return [EmailAlias] The retrieved email alias
     * @throws [EmailAliasException.EmailAliasNotFoundException] if no alias matches the given id
     */
    suspend fun getEmailAliasById(id: String): EmailAlias

    /**
     * Verifies an external email address for an email alias by sending a verification code
     * or confirming ownership with a provided code.
     *
     * When called without a verification code, triggers sending a verification email to the
     * external address. When called with a verification code, attempts to verify ownership
     * of the external address.
     *
     * @param input [VerifyExternalEmailAddressInput] The input parameters containing the email address,
     *  email alias ID, and optional verification code.
     * @return [VerifyExternalEmailAddressResult] Result indicating whether verification succeeded
     *  and an optional reason if it failed.
     * @throws [EmailAliasException] if the operation fails.
     */
    suspend fun verifyExternalEmailAddress(input: VerifyExternalEmailAddressInput): VerifyExternalEmailAddressResult

    /**
     * Subscribes to be notified of new and deleted [EmailMessage]s. Subscribing multiple
     * times with the same subscriber id will cause the previous subscriber to be unsubscribed.
     *
     * @param id [String] Unique identifier of the subscriber.
     * @param subscriber [EmailMessageSubscriber] Subscriber to notify.
     */
    @Throws(EmailMessageException.AuthenticationException::class)
    suspend fun subscribeToEmailMessages(
        id: String,
        subscriber: EmailMessageSubscriber,
    )

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
     * Flush the local message body cache for a specific sudo or email address.
     *
     * Removes all cached sealed message bodies matching the given scope. Exactly one of
     * [FlushMessageBodyCacheInput.sudoId] or [FlushMessageBodyCacheInput.emailAddressId]
     * must be provided.
     *
     * @param input [FlushMessageBodyCacheInput] The scope of the flush operation.
     * @throws IllegalArgumentException If neither sudoId nor emailAddressId is provided.
     */
    suspend fun flushMessageBodyCache(input: FlushMessageBodyCacheInput)

    /**
     * Update the maximum size of the local message body cache.
     *
     * If the new limit is lower than the current total cached size, the least recently
     * used entries are immediately evicted until the total size is within the new limit.
     * Setting [bytes] to 0 disables caching entirely.
     *
     * @param bytes [Long] New cache size limit in bytes. Must be >= 0.
     * @throws IllegalArgumentException If [bytes] is negative.
     */
    suspend fun setMessageBodyCacheSizeLimit(bytes: Long)

    /**
     * Reset any internal state and cached content, and remove every key from the device. This
     * includes the singleton key pair, so call [createSingletonKeysIfAbsent] again before
     * provisioning.
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
) = subscribeToEmailMessages(
    id,
    object : EmailMessageSubscriber {
        override fun connectionStatusChanged(state: Subscriber.ConnectionState) {
            onConnectionChange.invoke(state)
        }

        override fun emailMessageChanged(
            emailMessage: EmailMessage,
            type: EmailMessageSubscriber.ChangeType,
        ) {
            onEmailMessageChanged.invoke(emailMessage, type)
        }
    },
)
