/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [GetEmailAddressUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class GetEmailAddressUseCaseTest : BaseTests() {
    private val emailAddressId = mockEmailAddressId

    private val sealedEmailAddress by before {
        EntityDataFactory.getSealedEmailAddressEntity()
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking { get(any()) } doReturn sealedEmailAddress
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { decryptWithSymmetricKeyId(any(), any()) } doReturn "unsealed".toByteArray()
        }
    }

    private val useCase by before {
        GetEmailAddressUseCase(
            mockEmailAddressService,
            mockServiceKeyManager,
            mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockEmailAddressService,
            mockServiceKeyManager,
        )
    }

    @Test
    fun `execute() should return unsealed email address when found`() =
        runTest {
            val input =
                GetEmailAddressUseCaseInput(
                    emailAddressId = emailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result!!.id shouldBe mockEmailAddressId
            result.emailAddress shouldBe "example@sudoplatform.com"

            verify(mockEmailAddressService).get(
                com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.GetEmailAddressRequest(
                    id = emailAddressId,
                ),
            )
        }

    @Test
    fun `execute() should return null when email address not found`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { get(any()) } doReturn null
            }

            val input =
                GetEmailAddressUseCaseInput(
                    emailAddressId = emailAddressId,
                )

            val result = useCase.execute(input)

            result shouldBe null

            verify(mockEmailAddressService).get(any())
        }

    @Test
    fun `execute() should unseal alias when present`() =
        runTest {
            val sealedAlias =
                SealedAttributeEntity(
                    algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                    keyId = "keyId",
                    plainTextType = "string",
                    base64EncodedSealedData = mockSeal("My Email"),
                )
            val sealedAddress =
                EntityDataFactory.getSealedEmailAddressEntity(
                    alias = sealedAlias,
                )
            mockEmailAddressService.stub {
                onBlocking { get(any()) } doReturn sealedAddress
            }
            mockServiceKeyManager.stub {
                on { decryptWithSymmetricKeyId(any(), any()) } doReturn "My Email".toByteArray()
            }

            val input =
                GetEmailAddressUseCaseInput(
                    emailAddressId = emailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result!!.alias shouldBe "My Email"

            verify(mockEmailAddressService).get(any())
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should handle null alias`() =
        runTest {
            val sealedAddress =
                EntityDataFactory.getSealedEmailAddressEntity(
                    alias = null,
                )
            mockEmailAddressService.stub {
                onBlocking { get(any()) } doReturn sealedAddress
            }

            val input =
                GetEmailAddressUseCaseInput(
                    emailAddressId = emailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result!!.alias shouldBe null

            verify(mockEmailAddressService).get(any())
        }

    @Test
    fun `execute() should throw when service throws`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { get(any()) } doThrow RuntimeException("Service failed")
            }

            val input =
                GetEmailAddressUseCaseInput(
                    emailAddressId = emailAddressId,
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).get(any())
        }

    @Test
    fun `execute() should throw when unsealing throws`() =
        runTest {
            val sealedAlias =
                SealedAttributeEntity(
                    algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                    keyId = "keyId",
                    plainTextType = "string",
                    base64EncodedSealedData = mockSeal("My Email"),
                )
            val sealedAddress =
                EntityDataFactory.getSealedEmailAddressEntity(
                    alias = sealedAlias,
                )
            mockEmailAddressService.stub {
                onBlocking { get(any()) } doReturn sealedAddress
            }
            mockServiceKeyManager.stub {
                on { decryptWithSymmetricKeyId(any(), any()) } doThrow RuntimeException("Decryption failed")
            }

            val input =
                GetEmailAddressUseCaseInput(
                    emailAddressId = emailAddressId,
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).get(any())
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should use correct email address id in service request`() =
        runTest {
            val customId = "custom-email-address-id-123"
            val input =
                GetEmailAddressUseCaseInput(
                    emailAddressId = customId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockEmailAddressService).get(
                com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.GetEmailAddressRequest(
                    id = customId,
                ),
            )
        }

    @Test
    fun `execute() should properly unseal folders`() =
        runTest {
            val sealedFolder =
                EntityDataFactory.getSealedEmailFolderEntity(
                    id = mockFolderId,
                    folderName = "INBOX",
                )
            val sealedAddress =
                EntityDataFactory.getSealedEmailAddressEntity(
                    folders = listOf(sealedFolder),
                )
            mockEmailAddressService.stub {
                onBlocking { get(any()) } doReturn sealedAddress
            }

            val input =
                GetEmailAddressUseCaseInput(
                    emailAddressId = emailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result!!.folders.size shouldBe 1
            result.folders[0].id shouldBe mockFolderId
            result.folders[0].folderName shouldBe "INBOX"

            verify(mockEmailAddressService).get(any())
        }
}
