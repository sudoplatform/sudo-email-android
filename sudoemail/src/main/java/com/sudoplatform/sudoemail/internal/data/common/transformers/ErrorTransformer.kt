/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.common.transformers

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.data.common.mechanisms.Unsealer
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.s3.S3Exception
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudouser.exceptions.HTTP_STATUS_CODE_KEY
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException

/**
 * Transformer for interpreting and converting errors from various sources into appropriate
 * SudoEmailClient exception types.
 */
internal object ErrorTransformer {
    /**
     * Interprets a GraphQL error and converts it to an appropriate EmailAddressException.
     *
     * @param e [GraphQLResponse.Error] The GraphQL error to interpret.
     * @return The corresponding [SudoEmailClient.EmailAddressException].
     */
    fun interpretEmailAddressError(e: GraphQLResponse.Error): SudoEmailClient.EmailAddressException {
        val httpStatusCode = e.extensions?.get(HTTP_STATUS_CODE_KEY) as Int?
        val error = e.extensions?.get(StringConstants.ERROR_TYPE)?.toString() ?: ""

        if (httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return SudoEmailClient.EmailAddressException.AuthenticationException("$e")
        }
        if (httpStatusCode != null && httpStatusCode >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
            return SudoEmailClient.EmailAddressException.FailedException("$e")
        }
        if (error.contains(StringConstants.ERROR_INVALID_KEYRING)) {
            return SudoEmailClient.EmailAddressException.PublicKeyException(StringConstants.INVALID_KEYRING_MSG)
        }
        if (error.contains(StringConstants.ERROR_INVALID_EMAIL) || error.contains(StringConstants.ERROR_INVALID_DOMAIN)) {
            return SudoEmailClient.EmailAddressException.InvalidEmailAddressException(
                StringConstants.INVALID_EMAIL_ADDRESS_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_ADDRESS_NOT_FOUND)) {
            return SudoEmailClient.EmailAddressException.EmailAddressNotFoundException(
                StringConstants.EMAIL_ADDRESS_NOT_FOUND_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_ADDRESS_UNAVAILABLE)) {
            return SudoEmailClient.EmailAddressException.UnavailableEmailAddressException(
                StringConstants.EMAIL_ADDRESS_UNAVAILABLE_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_UNAUTHORIZED_ADDRESS)) {
            return SudoEmailClient.EmailAddressException.UnauthorizedEmailAddressException(
                StringConstants.EMAIL_ADDRESS_UNAUTHORIZED_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_INSUFFICIENT_ENTITLEMENTS) ||
            error.contains(
                StringConstants.ERROR_POLICY_FAILED,
            )
        ) {
            return SudoEmailClient.EmailAddressException.InsufficientEntitlementsException(
                StringConstants.INSUFFICIENT_ENTITLEMENTS_MSG,
            )
        }
        return SudoEmailClient.EmailAddressException.FailedException(e.toString())
    }

    /**
     * Interprets a throwable exception and converts it to an appropriate EmailAddressException.
     *
     * @param e [Throwable] The throwable to interpret.
     * @return [Throwable] The corresponding exception, which may be the original exception if already handled.
     */
    fun interpretEmailAddressException(e: Throwable): Throwable =
        when (e) {
            is CancellationException,
            is SudoEmailClient.EmailAddressException,
            is KeyNotFoundException,
            -> e

            is Unsealer.UnsealerException ->
                SudoEmailClient.EmailAddressException.UnsealingException(
                    StringConstants.UNSEAL_EMAIL_ADDRESS_ERROR_MSG,
                    e,
                )

            is DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException ->
                SudoEmailClient.EmailAddressException.PublicKeyException(StringConstants.KEY_GENERATION_ERROR_MSG)

            else -> SudoEmailClient.EmailAddressException.UnknownException(e)
        }

    /**
     * Interprets a GraphQL error and converts it to an EmailConfigurationException.
     *
     * @param e [GraphQLResponse.Error] The GraphQL error to interpret.
     * @return [SudoEmailClient.EmailConfigurationException] The corresponding EmailConfigurationException.
     */
    fun interpretEmailConfigurationError(e: GraphQLResponse.Error): SudoEmailClient.EmailConfigurationException =
        SudoEmailClient.EmailConfigurationException.FailedException(e.toString())

    /**
     * Interprets a throwable exception and converts it to an appropriate EmailConfigurationException.
     *
     * @param e [Throwable] The throwable to interpret.
     * @return [Throwable] The corresponding exception, which may be the original exception if already handled.
     */
    fun interpretEmailConfigurationException(e: Throwable): Throwable =
        when (e) {
            is CancellationException,
            is SudoEmailClient.EmailConfigurationException,
            -> e

            else -> SudoEmailClient.EmailConfigurationException.UnknownException(e)
        }

    /**
     * Interprets a GraphQL error and converts it to an EmailAddressException for email domain operations.
     *
     * @param e [GraphQLResponse.Error] The GraphQL error to interpret.
     * @return [SudoEmailClient.EmailAddressException] The corresponding EmailAddressException.
     */
    fun interpretEmailDomainError(e: GraphQLResponse.Error): SudoEmailClient.EmailAddressException =
        SudoEmailClient.EmailAddressException.FailedException(e.toString())

    /**
     * Interprets a GraphQL error and converts it to an EmailFolderException.
     *
     * @param e [GraphQLResponse.Error] The GraphQL error to interpret.
     * @return [SudoEmailClient.EmailFolderException] The corresponding EmailFolderException.
     */
    fun interpretEmailFolderError(e: GraphQLResponse.Error): SudoEmailClient.EmailFolderException =
        SudoEmailClient.EmailFolderException.FailedException(e.toString())

    /**
     * Interprets a throwable exception and converts it to an appropriate EmailFolderException.
     *
     * @param e [Throwable] The throwable to interpret.
     * @return [Throwable] The corresponding exception, which may be the original exception if already handled.
     */
    fun interpretEmailFolderException(e: Throwable): Throwable =
        when (e) {
            is CancellationException,
            is SudoEmailClient.EmailFolderException,
            -> e

            else -> SudoEmailClient.EmailFolderException.UnknownException(e)
        }

    /**
     * Interprets a throwable exception and converts it to an appropriate EmailMessageException.
     *
     * @param e [Throwable] The throwable to interpret.
     * @return [Throwable] The corresponding exception, which may be the original exception if already handled.
     */
    fun interpretEmailMessageException(e: Throwable): Throwable =
        when (e) {
            is NotAuthorizedException ->
                SudoEmailClient.EmailMessageException.AuthenticationException(
                    cause = e,
                )
            is CancellationException,
            is SudoEmailClient.EmailMessageException,
            -> e

            is Unsealer.UnsealerException ->
                SudoEmailClient.EmailMessageException.UnsealingException(
                    StringConstants.UNSEAL_EMAIL_MSG_ERROR_MSG,
                    e,
                )

            is DeviceKeyManager.DeviceKeyManagerException ->
                SudoEmailClient.EmailCryptographicKeysException.SecureKeyArchiveException(
                    StringConstants.KEY_ARCHIVE_ERROR_MSG,
                    e,
                )

            is EmailCryptoService.EmailCryptoServiceException ->
                SudoEmailClient.EmailMessageException.FailedException(
                    StringConstants.EMAIL_CRYPTO_ERROR_MSG,
                    e,
                )

            is AmazonS3Exception -> {
                if (e.errorCode == StringConstants.S3_NOT_FOUND_ERROR_CODE) {
                    SudoEmailClient.EmailMessageException.EmailMessageNotFoundException()
                } else {
                    e
                }
            }

            is S3Exception.DownloadException -> {
                if (e.message == StringConstants.S3_NOT_FOUND_ERROR_CODE) {
                    SudoEmailClient.EmailMessageException.EmailMessageNotFoundException()
                } else {
                    e
                }
            }

            else -> SudoEmailClient.EmailMessageException.UnknownException(e)
        }

    /**
     * Interprets a GraphQL error and converts it to an appropriate EmailMessageException.
     *
     * @param e [GraphQLResponse.Error] The GraphQL error to interpret.
     * @return [SudoEmailClient.EmailMessageException] The corresponding EmailMessageException.
     */
    fun interpretEmailMessageError(e: GraphQLResponse.Error): SudoEmailClient.EmailMessageException {
        val httpStatusCode = e.extensions?.get(HTTP_STATUS_CODE_KEY) as Int?
        val error = e.extensions?.get(StringConstants.ERROR_TYPE)?.toString() ?: ""

        if (httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return SudoEmailClient.EmailMessageException.AuthenticationException("$e")
        }
        if (httpStatusCode != null && httpStatusCode >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
            return SudoEmailClient.EmailMessageException.FailedException("$e")
        }
        if (error.contains(StringConstants.ERROR_INVALID_EMAIL_CONTENTS)) {
            return SudoEmailClient.EmailMessageException.InvalidMessageContentException(
                StringConstants.INVALID_MESSAGE_CONTENT_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_INVALID_ARGUMENT)) {
            return SudoEmailClient.EmailMessageException.InvalidArgumentException(
                StringConstants.INVALID_ARGUMENT_ERROR_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_UNAUTHORIZED_ADDRESS)) {
            return SudoEmailClient.EmailMessageException.UnauthorizedAddressException(
                StringConstants.EMAIL_ADDRESS_UNAUTHORIZED_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_MESSAGE_NOT_FOUND)) {
            return SudoEmailClient.EmailMessageException.EmailMessageNotFoundException(
                StringConstants.EMAIL_MESSAGE_NOT_FOUND_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_SERVICE_QUOTA_EXCEEDED)) {
            return SudoEmailClient.EmailMessageException.LimitExceededException(
                StringConstants.SERVICE_QUOTA_EXCEEDED_ERROR_MSG,
            )
        }
        if (error.contains(StringConstants.ERROR_RECORD_NOT_FOUND)) {
            return SudoEmailClient.EmailMessageException.RecordNotFoundException(StringConstants.RECORD_NOT_FOUND_ERROR_MSG)
        }
        return SudoEmailClient.EmailMessageException.FailedException(e.toString())
    }

    /**
     * Interprets a throwable exception and converts it to an appropriate EmailBlocklistException.
     *
     * @param e [Throwable] The throwable to interpret.
     * @return [Throwable] The corresponding exception, which may be the original exception if already handled.
     */
    fun interpretEmailBlocklistException(e: Throwable): Throwable =
        when (e) {
            is CancellationException,
            is SudoEmailClient.EmailBlocklistException,
            -> e

            else -> SudoEmailClient.EmailBlocklistException.UnknownException(e)
        }

    /**
     * Interprets a GraphQL error and converts it to an EmailBlocklistException.
     *
     * @param e [GraphQLResponse.Error] The GraphQL error to interpret.
     * @return [SudoEmailClient.EmailBlocklistException] The corresponding EmailBlocklistException.
     */
    fun interpretEmailBlocklistError(e: GraphQLResponse.Error): SudoEmailClient.EmailBlocklistException =
        SudoEmailClient.EmailBlocklistException.FailedException(e.toString())
}
