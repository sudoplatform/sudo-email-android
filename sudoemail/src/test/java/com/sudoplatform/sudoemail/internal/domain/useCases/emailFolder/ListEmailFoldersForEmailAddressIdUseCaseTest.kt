/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.EmailFolderService
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.ListEmailFoldersOutput
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
 * Test the correct operation of [ListEmailFoldersForEmailAddressIdUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class ListEmailFoldersForEmailAddressIdUseCaseTest : BaseTests() {
    private val sealedEmailFolder by before {
        EntityDataFactory.getSealedEmailFolderEntity()
    }

    private val mockEmailFolderService by before {
        mock<EmailFolderService>().stub {
            onBlocking { listForEmailAddressId(any()) } doReturn
                ListEmailFoldersOutput(
                    items = listOf(sealedEmailFolder),
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
        ListEmailFoldersForEmailAddressIdUseCase(
            emailFolderService = mockEmailFolderService,
            serviceKeyManager = mockServiceKeyManager,
            logger = mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockEmailFolderService,
            mockServiceKeyManager,
        )
    }

    @Test
    fun `execute() should return unsealed email folders when successful`() =
        runTest {
            val input =
                ListEmailFoldersForEmailAddressIdUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.items[0].id shouldBe mockFolderId
            result.items[0].folderName shouldBe "INBOX"
            result.nextToken shouldBe null

            verify(mockEmailFolderService).listForEmailAddressId(
                com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.ListEmailFoldersForEmailAddressIdRequest(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = null,
                ),
            )
        }

    @Test
    fun `execute() should return empty result when no folders exist for email address`() =
        runTest {
            mockEmailFolderService.stub {
                onBlocking { listForEmailAddressId(any()) } doReturn
                    ListEmailFoldersOutput(
                        items = emptyList(),
                        nextToken = null,
                    )
            }

            val input =
                ListEmailFoldersForEmailAddressIdUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.items.size shouldBe 0
            result.nextToken shouldBe null

            verify(mockEmailFolderService).listForEmailAddressId(any())
        }

    @Test
    fun `execute() should handle limit parameter correctly`() =
        runTest {
            val input =
                ListEmailFoldersForEmailAddressIdUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    limit = 10,
                    nextToken = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.items.size shouldBe 1

            verify(mockEmailFolderService).listForEmailAddressId(
                com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.ListEmailFoldersForEmailAddressIdRequest(
                    emailAddressId = mockEmailAddressId,
                    limit = 10,
                    nextToken = null,
                ),
            )
        }

    @Test
    fun `execute() should handle nextToken parameter correctly`() =
        runTest {
            mockEmailFolderService.stub {
                onBlocking { listForEmailAddressId(any()) } doReturn
                    ListEmailFoldersOutput(
                        items = listOf(sealedEmailFolder),
                        nextToken = "nextToken123",
                    )
            }

            val input =
                ListEmailFoldersForEmailAddressIdUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = "previousToken456",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.nextToken shouldBe "nextToken123"

            verify(mockEmailFolderService).listForEmailAddressId(
                com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.ListEmailFoldersForEmailAddressIdRequest(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = "previousToken456",
                ),
            )
        }

    @Test
    fun `execute() should handle both limit and nextToken parameters`() =
        runTest {
            mockEmailFolderService.stub {
                onBlocking { listForEmailAddressId(any()) } doReturn
                    ListEmailFoldersOutput(
                        items = listOf(sealedEmailFolder),
                        nextToken = "newNextToken",
                    )
            }

            val input =
                ListEmailFoldersForEmailAddressIdUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    limit = 50,
                    nextToken = "currentToken",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.nextToken shouldBe "newNextToken"

            verify(mockEmailFolderService).listForEmailAddressId(
                com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.ListEmailFoldersForEmailAddressIdRequest(
                    emailAddressId = mockEmailAddressId,
                    limit = 50,
                    nextToken = "currentToken",
                ),
            )
        }

    @Test
    fun `execute() should handle multiple email folders correctly`() =
        runTest {
            val sealedFolder1 = EntityDataFactory.getSealedEmailFolderEntity(id = "folder1", folderName = "INBOX")
            val sealedFolder2 = EntityDataFactory.getSealedEmailFolderEntity(id = "folder2", folderName = "SENT")
            val sealedFolder3 = EntityDataFactory.getSealedEmailFolderEntity(id = "folder3", folderName = "TRASH")

            mockEmailFolderService.stub {
                onBlocking { listForEmailAddressId(any()) } doReturn
                    ListEmailFoldersOutput(
                        items = listOf(sealedFolder1, sealedFolder2, sealedFolder3),
                        nextToken = null,
                    )
            }

            val input =
                ListEmailFoldersForEmailAddressIdUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.items.size shouldBe 3
            result.items[0].id shouldBe "folder1"
            result.items[0].folderName shouldBe "INBOX"
            result.items[1].id shouldBe "folder2"
            result.items[1].folderName shouldBe "SENT"
            result.items[2].id shouldBe "folder3"
            result.items[2].folderName shouldBe "TRASH"

            verify(mockEmailFolderService).listForEmailAddressId(any())
        }

    @Test
    fun `execute() should throw when service throws`() =
        runTest {
            mockEmailFolderService.stub {
                onBlocking { listForEmailAddressId(any()) } doThrow RuntimeException("Service failed")
            }

            val input =
                ListEmailFoldersForEmailAddressIdUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = null,
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockEmailFolderService).listForEmailAddressId(any())
        }

    @Test
    fun `execute() should use correct mockEmailAddressId in service request`() =
        runTest {
            val customEmailAddressId = "custom-email-address-id-123"
            val input =
                ListEmailFoldersForEmailAddressIdUseCaseInput(
                    emailAddressId = customEmailAddressId,
                    limit = null,
                    nextToken = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockEmailFolderService).listForEmailAddressId(
                com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.ListEmailFoldersForEmailAddressIdRequest(
                    emailAddressId = customEmailAddressId,
                    limit = null,
                    nextToken = null,
                ),
            )
        }

    @Test
    fun `execute() should throw when unsealing fails`() =
        runTest {
            val sealedFolder =
                EntityDataFactory.getSealedEmailFolderEntity(
                    id = mockFolderId,
                    sealedCustomFolderName =
                        SealedAttributeEntity(
                            algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                            keyId = "keyId",
                            plainTextType = "string",
                            base64EncodedSealedData = mockSeal("badData"),
                        ),
                )

            mockEmailFolderService.stub {
                onBlocking { listForEmailAddressId(any()) } doReturn
                    ListEmailFoldersOutput(
                        items = listOf(sealedFolder),
                        nextToken = null,
                    )
            }

            mockServiceKeyManager.stub {
                on {
                    decryptWithSymmetricKeyId(any(), any())
                } doThrow RuntimeException("Decryption failed")
            }

            val input =
                ListEmailFoldersForEmailAddressIdUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = null,
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockEmailFolderService).listForEmailAddressId(any())
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should unseal customFolderName when present`() =
        runTest {
            val sealedCustomFolderName =
                SealedAttributeEntity(
                    algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                    keyId = "keyId",
                    plainTextType = "string",
                    base64EncodedSealedData = mockSeal("My Custom Folder"),
                )
            val sealedFolder =
                EntityDataFactory.getSealedEmailFolderEntity(
                    id = mockFolderId,
                    sealedCustomFolderName = sealedCustomFolderName,
                )

            mockEmailFolderService.stub {
                onBlocking { listForEmailAddressId(any()) } doReturn
                    ListEmailFoldersOutput(
                        items = listOf(sealedFolder),
                        nextToken = null,
                    )
            }

            mockServiceKeyManager.stub {
                on { decryptWithSymmetricKeyId(any(), any()) } doReturn "My Custom Folder".toByteArray()
            }

            val input =
                ListEmailFoldersForEmailAddressIdUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.items[0].customFolderName shouldBe "My Custom Folder"

            verify(mockEmailFolderService).listForEmailAddressId(any())
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should handle folders without customFolderName`() =
        runTest {
            val sealedFolder =
                EntityDataFactory.getSealedEmailFolderEntity(
                    id = mockFolderId,
                    folderName = "INBOX",
                    sealedCustomFolderName = null,
                )

            mockEmailFolderService.stub {
                onBlocking { listForEmailAddressId(any()) } doReturn
                    ListEmailFoldersOutput(
                        items = listOf(sealedFolder),
                        nextToken = null,
                    )
            }

            val input =
                ListEmailFoldersForEmailAddressIdUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.items[0].id shouldBe mockFolderId
            result.items[0].folderName shouldBe "INBOX"
            result.items[0].customFolderName shouldBe null

            verify(mockEmailFolderService).listForEmailAddressId(any())
        }

    @Test
    fun `execute() should correctly preserve folder metadata`() =
        runTest {
            val sealedFolder =
                EntityDataFactory.getSealedEmailFolderEntity(
                    id = mockFolderId,
                    owner = "owner123",
                    emailAddressId = mockEmailAddressId,
                    folderName = "SENT",
                    size = 1024.0,
                    unseenCount = 5,
                    version = 2,
                )

            mockEmailFolderService.stub {
                onBlocking { listForEmailAddressId(any()) } doReturn
                    ListEmailFoldersOutput(
                        items = listOf(sealedFolder),
                        nextToken = null,
                    )
            }

            val input =
                ListEmailFoldersForEmailAddressIdUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.items.size shouldBe 1
            with(result.items[0]) {
                id shouldBe mockFolderId
                owner shouldBe "owner123"
                this.emailAddressId shouldBe mockEmailAddressId
                folderName shouldBe "SENT"
                size shouldBe 1024.0
                unseenCount shouldBe 5
                version shouldBe 2
            }

            verify(mockEmailFolderService).listForEmailAddressId(any())
        }
}
