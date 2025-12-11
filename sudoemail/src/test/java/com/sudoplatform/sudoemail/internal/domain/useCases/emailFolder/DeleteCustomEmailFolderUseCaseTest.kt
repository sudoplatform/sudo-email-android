/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.EmailFolderService
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [DeleteCustomEmailFolderUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class DeleteCustomEmailFolderUseCaseTest : BaseTests() {
    private val sealedString = "sealedString".toByteArray()

    private val sealedEmailFolder by before {
        EntityDataFactory.getSealedEmailFolderEntity(
            id = mockFolderId,
            folderName = "folderName",
            emailAddressId = mockEmailAddressId,
            sealedCustomFolderName =
                SealedAttributeEntity(
                    algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                    keyId = mockSymmetricKeyId,
                    plainTextType = "string",
                    base64EncodedSealedData = String(Base64.encode(sealedString), Charsets.UTF_8),
                ),
        )
    }

    private val mockEmailFolderService by before {
        mock<EmailFolderService>().stub {
            onBlocking { deleteCustom(any()) } doReturn sealedEmailFolder
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {

            on { decryptWithSymmetricKeyId(anyString(), any()) } doReturn mockCustomFolderName.toByteArray()
        }
    }

    private val useCase by before {
        DeleteCustomEmailFolderUseCase(
            mockEmailFolderService,
            mockServiceKeyManager,
            mockLogger,
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
    fun `execute() should return unsealed email folder when deletion successful`() =
        runTest {
            val input =
                DeleteCustomEmailFolderUseCaseInput(
                    emailFolderId = mockFolderId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            with(result!!) {
                id shouldBe mockFolderId
                emailAddressId shouldBe mockEmailAddressId
                folderName shouldBe "folderName"
                customFolderName shouldBe mockCustomFolderName
            }

            verify(mockEmailFolderService).deleteCustom(
                check { request ->
                    request.emailFolderId shouldBe mockFolderId
                    request.emailAddressId shouldBe mockEmailAddressId
                },
            )
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should return null when folder not found`() =
        runTest {
            mockEmailFolderService.stub {
                onBlocking { deleteCustom(any()) } doReturn null
            }

            val input =
                DeleteCustomEmailFolderUseCaseInput(
                    emailFolderId = "nonExistentFolderId",
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldBe null

            verify(mockEmailFolderService).deleteCustom(
                check { request ->
                    request.emailFolderId shouldBe "nonExistentFolderId"
                    request.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `execute() should use correct emailFolderId in request`() =
        runTest {
            val customEmailFolderId = "custom-folder-id-12345"
            val input =
                DeleteCustomEmailFolderUseCaseInput(
                    emailFolderId = customEmailFolderId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockEmailFolderService).deleteCustom(
                check { request ->
                    request.emailFolderId shouldBe customEmailFolderId
                    request.emailAddressId shouldBe mockEmailAddressId
                },
            )
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should use correct emailAddressId in request`() =
        runTest {
            val customEmailAddressId = "custom-email-address-id-12345"
            val input =
                DeleteCustomEmailFolderUseCaseInput(
                    emailFolderId = mockFolderId,
                    emailAddressId = customEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockEmailFolderService).deleteCustom(
                check { request ->
                    request.emailFolderId shouldBe mockFolderId
                    request.emailAddressId shouldBe customEmailAddressId
                },
            )
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockEmailFolderService.stub {
                onBlocking { deleteCustom(any()) } doThrow NotAuthorizedException("Mock")
            }

            val input =
                DeleteCustomEmailFolderUseCaseInput(
                    emailFolderId = mockFolderId,
                    emailAddressId = mockEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailFolderException.AuthenticationException> {
                useCase.execute(input)
            }

            verify(mockEmailFolderService).deleteCustom(any())
        }

    @Test
    fun `execute() should unseal folder correctly when deleted`() =
        runTest {
            val unsealedFolderName = "UnsealedDeletedFolder"
            val sealedFolderWithCustomName =
                EntityDataFactory.getSealedEmailFolderEntity(
                    id = "deletedFolderId",
                    emailAddressId = mockEmailAddressId,
                    folderName = "folderName",
                    sealedCustomFolderName =
                        SealedAttributeEntity(
                            algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                            keyId = mockSymmetricKeyId,
                            plainTextType = "string",
                            base64EncodedSealedData = mockSeal(unsealedFolderName),
                        ),
                )

            mockEmailFolderService.stub {
                onBlocking { deleteCustom(any()) } doReturn sealedFolderWithCustomName
            }

            val input =
                DeleteCustomEmailFolderUseCaseInput(
                    emailFolderId = "deletedFolderId",
                    emailAddressId = mockEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result?.id shouldBe "deletedFolderId"
            result?.emailAddressId shouldBe mockEmailAddressId
            result?.customFolderName shouldNotBe null

            verify(mockEmailFolderService).deleteCustom(any())
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should pass both parameters correctly to service`() =
        runTest {
            val testEmailFolderId = "test-folder-123"
            val testEmailAddressId = "test-email-address-456"
            val input =
                DeleteCustomEmailFolderUseCaseInput(
                    emailFolderId = testEmailFolderId,
                    emailAddressId = testEmailAddressId,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockEmailFolderService).deleteCustom(
                check { request ->
                    request.emailFolderId shouldBe testEmailFolderId
                    request.emailAddressId shouldBe testEmailAddressId
                },
            )
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should preserve all folder properties when unsealing`() =
        runTest {
            val specificSealedFolder =
                EntityDataFactory.getSealedEmailFolderEntity(
                    id = "specificFolderId",
                    owner = "specificOwner",
                    emailAddressId = "specificEmailAddressId",
                    folderName = "specificFolderName",
                    size = 42.0,
                    unseenCount = 5,
                    version = 3,
                    sealedCustomFolderName =
                        SealedAttributeEntity(
                            algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                            keyId = mockSymmetricKeyId,
                            plainTextType = "string",
                            base64EncodedSealedData = String(Base64.encode(sealedString), Charsets.UTF_8),
                        ),
                )

            mockEmailFolderService.stub {
                onBlocking { deleteCustom(any()) } doReturn specificSealedFolder
            }

            val input =
                DeleteCustomEmailFolderUseCaseInput(
                    emailFolderId = "specificFolderId",
                    emailAddressId = "specificEmailAddressId",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            with(result!!) {
                id shouldBe "specificFolderId"
                owner shouldBe "specificOwner"
                emailAddressId shouldBe "specificEmailAddressId"
                folderName shouldBe "specificFolderName"
                size shouldBe 42.0
                unseenCount shouldBe 5
                version shouldBe 3
            }

            verify(mockEmailFolderService).deleteCustom(any())
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }
}
