/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.common

/**
 * Collection of string constants used throughout the Email SDK for error messages,
 * content encoding values, metadata keys, and error type identifiers.
 */
object StringConstants {
    /** Content encoding values for email message data. */
    const val CRYPTO_CONTENT_ENCODING = "sudoplatform-crypto"
    const val BINARY_DATA_CONTENT_ENCODING = "sudoplatform-binary-data"
    const val COMPRESSION_CONTENT_ENCODING = "sudoplatform-compression"

    /** Key names used for storing metadata with draft messages in S3 */
    const val DRAFT_METADATA_KEY_ID_NAME = "key-id"
    const val DRAFT_METADATA_LEGACY_KEY_ID_NAME = "keyId"
    const val DRAFT_METADATA_ALGORITHM_NAME = "algorithm"

    /** Exception messages */
    const val UNSEAL_EMAIL_ADDRESS_ERROR_MSG = "Unable to unseal email address data"
    const val UNSEAL_EMAIL_MSG_ERROR_MSG = "Unable to unseal email message data"
    const val UNSEAL_EMAIL_MASK_ERROR_MSG = "Unable to unseal email mask data"
    const val KEY_GENERATION_ERROR_MSG = "Failed to generate a public key pair"
    const val KEY_ARCHIVE_ERROR_MSG = "Unable to perform key archive operation"
    const val NO_EMAIL_ADDRESS_ERROR_MSG = "No email address returned"
    const val INVALID_KEYRING_MSG = "Invalid key ring identifier"
    const val INVALID_EMAIL_ADDRESS_MSG = "Invalid email address"
    const val INSUFFICIENT_ENTITLEMENTS_MSG = "Entitlements have been exceeded"
    const val NO_EMAIL_ID_ERROR_MSG = "No email message identifier returned"
    const val IN_NETWORK_EMAIL_ADDRESSES_NOT_FOUND_ERROR_MSG =
        "At least one email address does not exist in network"
    const val INVALID_MESSAGE_CONTENT_MSG = "Invalid email message contents"
    const val EMAIL_ADDRESS_NOT_FOUND_MSG = "Email address not found"
    const val EMAIL_ADDRESS_UNAVAILABLE_MSG = "Email address is not available"
    const val EMAIL_ADDRESS_UNAUTHORIZED_MSG = "Unauthorized email address"
    const val EMAIL_MESSAGE_NOT_FOUND_MSG = "Email message not found"
    const val ID_LIMIT_EXCEEDED_ERROR_MSG = "Input cannot exceed "
    const val RECIPIENT_LIMIT_EXCEEDED_ERROR_MSG = "Number of recipients cannot exceed "
    const val INVALID_ARGUMENT_ERROR_MSG = "Invalid input"
    const val SYMMETRIC_KEY_NOT_FOUND_ERROR_MSG = "Symmetric key not found"
    const val PUBLIC_KEY_NOT_FOUND_ERROR_MSG = "Public Key not found"
    const val S3_KEY_ID_ERROR_MSG = "No sealed keyId associated with s3 object"
    const val S3_ALGORITHM_ERROR_MSG = "No sealed algorithm associated with s3 object"
    const val S3_NOT_FOUND_ERROR_CODE = "404 Not Found"
    const val ADDRESS_BLOCKLIST_EMPTY_MSG = "At least one email address must be passed"
    const val ADDRESS_BLOCKLIST_DUPLICATE_MSG =
        "Duplicate email address found. Please include each address only once"
    const val KEY_ATTACHMENTS_NOT_FOUND_ERROR_MSG = "Key attachments could not be found"
    const val BODY_ATTACHMENT_NOT_FOUND_ERROR_MSG =
        "Body attachments could not be found"
    const val EMAIL_CRYPTO_ERROR_MSG =
        "Unable to perform cryptographic operation on email data"
    const val SERVICE_QUOTA_EXCEEDED_ERROR_MSG = "Daily message quota limit exceeded"
    const val RECORD_NOT_FOUND_ERROR_MSG = "Record not found"

    const val EMAIL_MASK_NOT_FOUND_MSG = "Email mask not found"
    const val EMAIL_MASK_ALREADY_EXISTS_MSG = "Email mask already exists"
    const val EMAIL_MASK_LOCKED_MSG = "Email mask locked"

    const val KEY_NOT_FOUND_ERROR = "Key not found"
    const val DECODE_ERROR = "Could not decode"

    /** Errors returned from the service */
    const val ERROR_TYPE = "errorType"
    const val ERROR_INVALID_KEYRING = "InvalidKeyRingId"
    const val ERROR_INVALID_ARGUMENT = "InvalidArgument"
    const val ERROR_INVALID_EMAIL = "EmailValidation"
    const val ERROR_POLICY_FAILED = "PolicyFailed"
    const val ERROR_INVALID_EMAIL_CONTENTS = "InvalidEmailContents"
    const val ERROR_UNAUTHORIZED_ADDRESS = "UnauthorizedAddress"
    const val ERROR_ADDRESS_NOT_FOUND = "AddressNotFound"
    const val ERROR_ADDRESS_UNAVAILABLE = "AddressUnavailable"
    const val ERROR_INVALID_DOMAIN = "InvalidEmailDomain"
    const val ERROR_MESSAGE_NOT_FOUND = "EmailMessageNotFound"
    const val ERROR_INSUFFICIENT_ENTITLEMENTS = "InsufficientEntitlementsError"
    const val ERROR_SERVICE_QUOTA_EXCEEDED = "ServiceQuotaExceededError"
    const val ERROR_RECORD_NOT_FOUND = "RecordNotFound"

    const val ERROR_EMAIL_MASK_NOT_FOUND = "EmailMaskNotFound"
    const val ERROR_EMAIL_MASK_ALREADY_EXISTS = "EmailMaskAlreadyExists"
    const val ERROR_EMAIL_MASK_LOCKED = "EmailMaskLocked"

    const val UNKNOWN_ERROR_MSG = "An unknown error occurred"
}
