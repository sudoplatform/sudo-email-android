/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress

import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [UpdateEmailAddressMetadataUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class UpdateEmailAddressMetadataUseCaseTest : BaseTests() {
    private val sealedString = "sealedString".toByteArray()
    private val symmetricKeyId = "symmetricKeyId"
    private val emailAddressId = mockEmailAddressId

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking { updateMetadata(any()) } doReturn emailAddressId
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { getCurrentSymmetricKeyId() } doReturn symmetricKeyId
        }
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on { sealString(any(), any()) } doReturn sealedString
        }
    }

    private val useCase by before {
        UpdateEmailAddressMetadataUseCase(
            mockEmailAddressService,
            mockServiceKeyManager,
            mockSealingService,
            mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockEmailAddressService,
            mockServiceKeyManager,
            mockSealingService,
        )
    }

    @Test
    fun `execute() should return email address id when updating with alias`() =
        runTest {
            val input =
                UpdateEmailAddressMetadataUseCaseInput(
                    emailAddressId = emailAddressId,
                    alias = "My Email",
                )

            val result = useCase.execute(input)

            result shouldBe emailAddressId

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(symmetricKeyId, "My Email".toByteArray())
            verify(mockEmailAddressService).updateMetadata(
                check { request ->
                    request.id shouldBe emailAddressId
                    request.alias shouldBe
                        SealedAttributeInput(
                            algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                            keyId = symmetricKeyId,
                            plainTextType = "string",
                            base64EncodedSealedData = String(Base64.encode(sealedString), Charsets.UTF_8),
                        )
                },
            )
        }

    @Test
    fun `execute() should return email address id when updating with null alias`() =
        runTest {
            val input =
                UpdateEmailAddressMetadataUseCaseInput(
                    emailAddressId = emailAddressId,
                    alias = null,
                )

            val result = useCase.execute(input)

            result shouldBe emailAddressId

            verify(mockServiceKeyManager, times(0)).getCurrentSymmetricKeyId()
            verify(mockEmailAddressService).updateMetadata(
                check { request ->
                    request.id shouldBe emailAddressId
                    request.alias shouldBe null
                },
            )
        }

    @Test
    fun `execute() should throw KeyNotFoundException when symmetric key is null`() =
        runTest {
            mockServiceKeyManager.stub {
                on { getCurrentSymmetricKeyId() } doReturn null
            }

            val input =
                UpdateEmailAddressMetadataUseCaseInput(
                    emailAddressId = emailAddressId,
                    alias = "My Email",
                )

            shouldThrow<KeyNotFoundException> {
                useCase.execute(input)
            }

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        }

    @Test
    fun `execute() should throw when encryption fails`() =
        runTest {
            mockSealingService.stub {
                on { sealString(any(), any()) } doThrow RuntimeException("Encryption failed")
            }

            val input =
                UpdateEmailAddressMetadataUseCaseInput(
                    emailAddressId = emailAddressId,
                    alias = "My Email",
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(symmetricKeyId, "My Email".toByteArray())
        }

    @Test
    fun `execute() should throw when email address service fails`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { updateMetadata(any()) } doThrow RuntimeException("Service failed")
            }

            val input =
                UpdateEmailAddressMetadataUseCaseInput(
                    emailAddressId = emailAddressId,
                    alias = "My Email",
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(symmetricKeyId, "My Email".toByteArray())
            verify(mockEmailAddressService).updateMetadata(any())
        }

    @Test
    fun `execute() should handle empty string alias`() =
        runTest {
            val input =
                UpdateEmailAddressMetadataUseCaseInput(
                    emailAddressId = emailAddressId,
                    alias = "",
                )

            val result = useCase.execute(input)

            result shouldBe emailAddressId

            verify(mockServiceKeyManager, times(0)).getCurrentSymmetricKeyId()
            verify(mockSealingService, times(0)).sealString(symmetricKeyId, "".toByteArray())
            verify(mockEmailAddressService).updateMetadata(
                check { request ->
                    request.id shouldBe emailAddressId
                    request.alias shouldBe null
                    request.clearAlias shouldBe true
                },
            )
        }

    @Test
    fun `execute() should handle special characters in alias`() =
        runTest {
            val specialAlias = "My Email 🎉 <test@example.com>"
            val input =
                UpdateEmailAddressMetadataUseCaseInput(
                    emailAddressId = emailAddressId,
                    alias = specialAlias,
                )

            val result = useCase.execute(input)

            result shouldBe emailAddressId

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(symmetricKeyId, specialAlias.toByteArray())
            verify(mockEmailAddressService).updateMetadata(
                check { request ->
                    request.id shouldBe emailAddressId
                    request.alias shouldBe
                        SealedAttributeInput(
                            algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                            keyId = symmetricKeyId,
                            plainTextType = "string",
                            base64EncodedSealedData = String(Base64.encode(sealedString), Charsets.UTF_8),
                        )
                },
            )
        }

    @Test
    fun `execute() should use correct encryption algorithm`() =
        runTest {
            val input =
                UpdateEmailAddressMetadataUseCaseInput(
                    emailAddressId = emailAddressId,
                    alias = "My Email",
                )

            val result = useCase.execute(input)

            result shouldBe emailAddressId

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(symmetricKeyId, "My Email".toByteArray())
            verify(mockEmailAddressService).updateMetadata(
                check { request ->
                    request.alias?.algorithm shouldBe SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString()
                    request.alias?.keyId shouldBe symmetricKeyId
                    request.alias?.plainTextType shouldBe "string"
                },
            )
        }
}
