/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import com.sudoplatform.sudoemail.SudoEmailClient.EmailAddressException
import com.sudoplatform.sudoemail.SudoEmailClient.EmailAliasException
import com.sudoplatform.sudoemail.types.EmailAddress
import io.kotlintest.shouldBe
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.CancellationException

/**
 * Test that [isDefinitiveRejection] classifies every provisioning failure, so a key pair is removed
 * only when the service, or the client before sending, refused the request.
 */
class DefinitiveRejectionTest {
    private val cause = RuntimeException("mock")

    private val emailAddressExceptions: Map<EmailAddressException, Boolean> =
        mapOf(
            EmailAddressException.AuthenticationException() to true,
            EmailAddressException.InsufficientEntitlementsException() to true,
            EmailAddressException.InvalidEmailAddressException() to true,
            EmailAddressException.EmailAddressNotFoundException() to true,
            EmailAddressException.UnavailableEmailAddressException() to true,
            EmailAddressException.UnauthorizedEmailAddressException() to true,
            EmailAddressException.PublicKeyException() to true,
            EmailAddressException.LimitExceededException() to true,
            EmailAddressException.InvalidArgumentException() to true,
            EmailAddressException.SingletonKeyPairNotFoundException() to true,
            EmailAddressException.FailedException() to false,
            EmailAddressException.ProvisionFailedException() to false,
            EmailAddressException.UnsealingException() to false,
            EmailAddressException.DeprovisionFailedException() to false,
            EmailAddressException.UpdateFailedException() to false,
            EmailAddressException.KeyPairRemovalFailedException(mock<EmailAddress>(), listOf("keyId")) to false,
            EmailAddressException.UnknownException(cause) to false,
        )

    private val emailAliasExceptions: Map<EmailAliasException, Boolean> =
        mapOf(
            EmailAliasException.AuthenticationException() to true,
            EmailAliasException.InsufficientEntitlementsException() to true,
            EmailAliasException.InvalidArgumentException() to true,
            EmailAliasException.InvalidEmailAddressException() to true,
            EmailAliasException.UnavailableEmailAddressException() to true,
            EmailAliasException.EmailAliasAlreadyExistsException() to true,
            EmailAliasException.EmailAliasLockedException() to true,
            EmailAliasException.EmailAliasNotFoundException() to true,
            EmailAliasException.PublicKeyException() to true,
            EmailAliasException.SingletonKeyPairNotFoundException() to true,
            EmailAliasException.FailedException() to false,
            EmailAliasException.ProvisionFailedException() to false,
            EmailAliasException.DeprovisionFailedException() to false,
            EmailAliasException.UpdateFailedException() to false,
            EmailAliasException.UnsealingException() to false,
            EmailAliasException.UnknownException(cause) to false,
        )

    @Test
    fun `every EmailAddressException subtype should be classified as expected`() {
        emailAddressExceptions.keys.map { it::class }.toSet() shouldBe EmailAddressException::class.sealedSubclasses.toSet()
        emailAddressExceptions.forEach { (exception, expected) ->
            (exception::class.simpleName to exception.isDefinitiveRejection) shouldBe (exception::class.simpleName to expected)
        }
    }

    @Test
    fun `every EmailAliasException subtype should be classified as expected`() {
        emailAliasExceptions.keys.map { it::class }.toSet() shouldBe EmailAliasException::class.sealedSubclasses.toSet()
        emailAliasExceptions.forEach { (exception, expected) ->
            (exception::class.simpleName to exception.isDefinitiveRejection) shouldBe (exception::class.simpleName to expected)
        }
    }

    @Test
    fun `failures that aren't SDK exceptions should not be definitive rejections`() {
        RuntimeException("transport").isDefinitiveRejection shouldBe false
        CancellationException("cancelled").isDefinitiveRejection shouldBe false
    }
}
