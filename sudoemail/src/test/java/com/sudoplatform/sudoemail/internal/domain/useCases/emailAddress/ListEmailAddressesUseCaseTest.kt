/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListAPIResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ListEmailAddressesOutput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ListEmailAddressesRequest
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import io.kotlintest.fail
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [ListEmailAddressesUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class ListEmailAddressesUseCaseTest : BaseTests() {
    private val sealedEmailAddress by before {
        EntityDataFactory.getSealedEmailAddressEntity()
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking { list(any()) } doReturn
                ListEmailAddressesOutput(
                    items = listOf(sealedEmailAddress),
                    nextToken = null,
                )
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { decryptWithSymmetricKeyId(any(), any()) } doReturn "unsealed".toByteArray()
        }
    }

    private val useCase by before {
        ListEmailAddressesUseCase(
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
    fun `execute() should return success result with unsealed email addresses`() =
        runTest {
            val input =
                ListEmailAddressesUseCaseInput(
                    limit = null,
                    nextToken = null,
                )

            val listAPIResultEntity = useCase.execute(input)

            listAPIResultEntity shouldNotBe null
            when (listAPIResultEntity) {
                is ListAPIResultEntity.Success -> {
                    val successResult = listAPIResultEntity.result
                    successResult.items.size shouldBe 1
                    successResult.items[0].id shouldBe mockEmailAddressId
                    successResult.items[0].emailAddress shouldBe "example@sudoplatform.com"
                    successResult.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAddressService).list(
                ListEmailAddressesRequest(
                    limit = null,
                    nextToken = null,
                ),
            )
        }

    @Test
    fun `execute() should return empty success result when no email addresses exist`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailAddressesOutput(
                        items = emptyList(),
                        nextToken = null,
                    )
            }

            val input =
                ListEmailAddressesUseCaseInput(
                    limit = null,
                    nextToken = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    val successResult = result.result
                    successResult.items.size shouldBe 0
                    successResult.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAddressService).list(any())
        }

    @Test
    fun `execute() should handle limit parameter correctly`() =
        runTest {
            val input =
                ListEmailAddressesUseCaseInput(
                    limit = 10,
                    nextToken = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    // Success - as expected
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAddressService).list(
                ListEmailAddressesRequest(
                    limit = 10,
                    nextToken = null,
                ),
            )
        }

    @Test
    fun `execute() should handle nextToken parameter correctly`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(sealedEmailAddress),
                        nextToken = "nextToken123",
                    )
            }

            val input =
                ListEmailAddressesUseCaseInput(
                    limit = null,
                    nextToken = "previousToken456",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    val successResult = result.result
                    successResult.nextToken shouldBe "nextToken123"
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAddressService).list(
                ListEmailAddressesRequest(
                    limit = null,
                    nextToken = "previousToken456",
                ),
            )
        }

    @Test
    fun `execute() should handle multiple email addresses correctly`() =
        runTest {
            val sealedAddress1 = EntityDataFactory.getSealedEmailAddressEntity(id = "id1")
            val sealedAddress2 = EntityDataFactory.getSealedEmailAddressEntity(id = "id2")
            val sealedAddress3 = EntityDataFactory.getSealedEmailAddressEntity(id = "id3")

            mockEmailAddressService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(sealedAddress1, sealedAddress2, sealedAddress3),
                        nextToken = null,
                    )
            }

            val input =
                ListEmailAddressesUseCaseInput(
                    limit = null,
                    nextToken = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    val successResult = result.result
                    successResult.items.size shouldBe 3
                    successResult.items[0].id shouldBe "id1"
                    successResult.items[1].id shouldBe "id2"
                    successResult.items[2].id shouldBe "id3"
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAddressService).list(any())
        }

    @Test
    fun `execute() should return partial result when unsealing fails for some addresses`() =
        runTest {
            val sealedAddress1 = EntityDataFactory.getSealedEmailAddressEntity(id = "id1")
            val sealedAddress2 =
                EntityDataFactory.getSealedEmailAddressEntity(
                    id = "id2",
                    alias =
                        SealedAttributeEntity(
                            algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                            keyId = "keyId",
                            plainTextType = "string",
                            base64EncodedSealedData = "badData",
                        ),
                )
            val sealedAddress3 = EntityDataFactory.getSealedEmailAddressEntity(id = "id3")

            mockEmailAddressService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(sealedAddress1, sealedAddress2, sealedAddress3),
                        nextToken = null,
                    )
            }

            mockServiceKeyManager.stub {
                on { decryptWithSymmetricKeyId(any(), any()) } doAnswer {
                    "unsealed".toByteArray()
                } doAnswer {
                    "unsealed".toByteArray()
                } doAnswer {
                    throw RuntimeException("Decryption failed")
                }
            }

            val input =
                ListEmailAddressesUseCaseInput(
                    limit = null,
                    nextToken = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Partial -> {
                    val partialResult = result.result
                    partialResult.items.size shouldBe 2
                    partialResult.items[0].id shouldBe "id1"
                    partialResult.items[1].id shouldBe "id3"
                    partialResult.failed.size shouldBe 1
                    partialResult.failed[0].partial.id shouldBe "id2"
                    partialResult.failed[0].partial.folders[0] shouldNotBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAddressService).list(any())
        }

    @Test
    fun `execute() should include nextToken in partial result`() =
        runTest {
            val sealedAddress1 = EntityDataFactory.getSealedEmailAddressEntity(id = "id1")
            val sealedAddress2 =
                EntityDataFactory.getSealedEmailAddressEntity(
                    id = "id2",
                    alias =
                        SealedAttributeEntity(
                            algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                            keyId = "keyId",
                            plainTextType = "string",
                            base64EncodedSealedData = "badData",
                        ),
                )

            mockEmailAddressService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(sealedAddress1, sealedAddress2),
                        nextToken = "nextToken789",
                    )
            }

            mockServiceKeyManager.stub {
                on { decryptWithSymmetricKeyId(any(), any()) } doAnswer {
                    "unsealed".toByteArray()
                } doAnswer {
                    "unsealed".toByteArray()
                } doAnswer {
                    throw RuntimeException("Decryption failed")
                }
            }

            val input =
                ListEmailAddressesUseCaseInput(
                    limit = null,
                    nextToken = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Partial -> {
                    val partialResult = result.result
                    partialResult.items.size shouldBe 1
                    partialResult.failed.size shouldBe 1
                    partialResult.nextToken shouldBe "nextToken789"
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAddressService).list(any())
        }

    @Test
    fun `execute() should throw when service throws`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { list(any()) } doThrow RuntimeException("Service failed")
            }

            val input =
                ListEmailAddressesUseCaseInput(
                    limit = null,
                    nextToken = null,
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).list(any())
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
                onBlocking { list(any()) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(sealedAddress),
                        nextToken = null,
                    )
            }
            mockServiceKeyManager.stub {
                on { decryptWithSymmetricKeyId(any(), any()) } doReturn "My Email".toByteArray()
            }

            val input =
                ListEmailAddressesUseCaseInput(
                    limit = null,
                    nextToken = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    val successResult = result.result
                    successResult.items.size shouldBe 1
                    successResult.items[0].alias shouldBe "My Email"
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAddressService).list(any())
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
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
                onBlocking { list(any()) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(sealedAddress),
                        nextToken = null,
                    )
            }

            val input =
                ListEmailAddressesUseCaseInput(
                    limit = null,
                    nextToken = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            when (result) {
                is ListAPIResultEntity.Success -> {
                    val successResult = result.result
                    successResult.items.size shouldBe 1
                    successResult.items[0].folders.size shouldBe 1
                    successResult.items[0].folders[0].id shouldBe mockFolderId
                    successResult.items[0].folders[0].folderName shouldBe "INBOX"
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAddressService).list(any())
        }
}
