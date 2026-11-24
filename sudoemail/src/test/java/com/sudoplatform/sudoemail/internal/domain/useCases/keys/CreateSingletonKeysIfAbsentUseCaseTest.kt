/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.keys

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient.EmailCryptographicKeysException
import com.sudoplatform.sudoemail.keys.KeyPair
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.keys.SingletonKeyPairResult
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [CreateSingletonKeysIfAbsentUseCase] using mocks.
 */
@RunWith(RobolectricTestRunner::class)
class CreateSingletonKeysIfAbsentUseCaseTest : BaseTests() {
    private val keyPair =
        KeyPair(
            keyId = "singletonKeyId",
            keyRingId = "keyRingId",
            publicKey = "publicKey".toByteArray(),
            privateKey = "privateKey".toByteArray(),
        )

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>()
    }

    private fun useCase(enforceSingletonKeyPair: Boolean) =
        CreateSingletonKeysIfAbsentUseCase(
            serviceKeyManager = mockServiceKeyManager,
            enforceSingletonKeyPair = enforceSingletonKeyPair,
            logger = mockLogger,
        )

    @After
    fun fini() {
        verifyNoMoreInteractions(mockServiceKeyManager)
    }

    @Test
    fun `execute() should throw InvalidConfigurationException without touching keys when the singleton isn't enforced`() {
        shouldThrow<EmailCryptographicKeysException.InvalidConfigurationException> {
            useCase(enforceSingletonKeyPair = false).execute()
        }
    }

    @Test
    fun `execute() should return the result of creating the singleton when it is enforced`() {
        val expected = SingletonKeyPairResult(keyPair = keyPair, created = true)
        mockServiceKeyManager.stub {
            on { createSingletonKeyPairIfAbsent() } doReturn expected
        }

        useCase(enforceSingletonKeyPair = true).execute() shouldBe expected

        verify(mockServiceKeyManager).createSingletonKeyPairIfAbsent()
    }
}
