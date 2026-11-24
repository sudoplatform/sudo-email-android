/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import com.sudoplatform.sudoemail.SudoEmailClient.EmailAddressException
import com.sudoplatform.sudoemail.SudoEmailClient.EmailAliasException

/**
 * Whether this failure of a provisioning call proves the service, or the client before sending,
 * refused the request, so a key pair generated for it can be removed.
 *
 * Decided on the exception the GraphQL services map a failure to. That mapping works from the
 * service's typed `errorType` and the HTTP status, so a typed rejection is proof the service did
 * not act. Anything that could follow a request the service accepted does not qualify: a 5xx or
 * unclassified error (`FailedException`), a response with no result (`ProvisionFailedException`),
 * a failure after the response arrived (`UnsealingException`), a transport or unexpected failure
 * (`UnknownException`), and any other throwable, including cancellation. After those the email
 * address or email alias may exist, and its key is the only way to read its messages.
 *
 * The `when`s are exhaustive so a new subtype must be classified.
 */
internal val Throwable.isDefinitiveRejection: Boolean
    get() =
        when (this) {
            is EmailAddressException ->
                when (this) {
                    is EmailAddressException.AuthenticationException,
                    is EmailAddressException.InsufficientEntitlementsException,
                    is EmailAddressException.InvalidEmailAddressException,
                    is EmailAddressException.EmailAddressNotFoundException,
                    is EmailAddressException.UnavailableEmailAddressException,
                    is EmailAddressException.UnauthorizedEmailAddressException,
                    is EmailAddressException.PublicKeyException,
                    is EmailAddressException.LimitExceededException,
                    is EmailAddressException.InvalidArgumentException,
                    is EmailAddressException.SingletonKeyPairNotFoundException,
                    -> true
                    is EmailAddressException.FailedException,
                    is EmailAddressException.ProvisionFailedException,
                    is EmailAddressException.UnsealingException,
                    is EmailAddressException.DeprovisionFailedException,
                    is EmailAddressException.UpdateFailedException,
                    is EmailAddressException.KeyPairRemovalFailedException,
                    is EmailAddressException.UnknownException,
                    -> false
                }
            is EmailAliasException ->
                when (this) {
                    is EmailAliasException.AuthenticationException,
                    is EmailAliasException.InsufficientEntitlementsException,
                    is EmailAliasException.InvalidArgumentException,
                    is EmailAliasException.InvalidEmailAddressException,
                    is EmailAliasException.UnavailableEmailAddressException,
                    is EmailAliasException.EmailAliasAlreadyExistsException,
                    is EmailAliasException.EmailAliasLockedException,
                    is EmailAliasException.EmailAliasNotFoundException,
                    is EmailAliasException.PublicKeyException,
                    is EmailAliasException.SingletonKeyPairNotFoundException,
                    -> true
                    is EmailAliasException.FailedException,
                    is EmailAliasException.ProvisionFailedException,
                    is EmailAliasException.DeprovisionFailedException,
                    is EmailAliasException.UpdateFailedException,
                    is EmailAliasException.UnsealingException,
                    is EmailAliasException.UnknownException,
                    -> false
                }
            else -> false
        }
