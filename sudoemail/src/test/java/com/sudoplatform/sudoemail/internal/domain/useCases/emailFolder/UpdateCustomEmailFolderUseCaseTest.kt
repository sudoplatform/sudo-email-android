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
import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.EmailFolderService
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [UpdateCustomEmailFolderUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class UpdateCustomEmailFolderUseCaseTest : BaseTests() {
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
            onBlocking { updateCustom(any()) } doReturn sealedEmailFolder
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { getCurrentSymmetricKeyId() } doReturn mockSymmetricKeyId
            on { decryptWithSymmetricKeyId(any(), any()) } doReturn mockCustomFolderName.toByteArray()
        }
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on { sealString(any(), any()) } doReturn sealedString
        }
    }

    private val useCase by before {
        UpdateCustomEmailFolderUseCase(
            mockEmailFolderService,
            mockServiceKeyManager,
            mockSealingService,
            mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockEmailFolderService,
            mockServiceKeyManager,
            mockSealingService,
        )
    }

    @Test
    fun `execute() should return unsealed email folder when update successful`() =
        runTest {
            val input =
                UpdateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    emailFolderId = mockFolderId,
                    customFolderName = "UpdatedFolderName",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            with(result) {
                id shouldBe mockFolderId
                emailAddressId shouldBe mockEmailAddressId
                folderName shouldBe "folderName"
                customFolderName shouldBe mockCustomFolderName
            }

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(mockSymmetricKeyId, "UpdatedFolderName".toByteArray())
            verify(mockEmailFolderService).updateCustom(
                check { request ->
                    request.emailAddressId shouldBe mockEmailAddressId
                    request.emailFolderId shouldBe mockFolderId
                    request.customFolderName shouldBe
                        SealedAttributeInput(
                            algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                            keyId = mockSymmetricKeyId,
                            plainTextType = "string",
                            base64EncodedSealedData = String(Base64.encode(sealedString), Charsets.UTF_8),
                        )
                },
            )
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should handle null customFolderName`() =
        runTest {
            val input =
                UpdateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    emailFolderId = mockFolderId,
                    customFolderName = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockEmailFolderService).updateCustom(
                check { request ->
                    request.emailAddressId shouldBe mockEmailAddressId
                    request.emailFolderId shouldBe mockFolderId
                    request.customFolderName shouldBe null
                },
            )
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should seal custom folder name correctly`() =
        runTest {
            val customFolderName = "My Updated Folder"
            val input =
                UpdateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    emailFolderId = mockFolderId,
                    customFolderName = customFolderName,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.customFolderName shouldBe mockCustomFolderName

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(mockSymmetricKeyId, customFolderName.toByteArray())
            verify(mockEmailFolderService).updateCustom(
                check { request ->
                    request.customFolderName shouldBe
                        SealedAttributeInput(
                            algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                            keyId = mockSymmetricKeyId,
                            plainTextType = "string",
                            base64EncodedSealedData = String(Base64.encode(sealedString), Charsets.UTF_8),
                        )
                },
            )
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should use correct emailFolderId in request`() =
        runTest {
            val customEmailFolderId = "custom-folder-id-12345"
            val input =
                UpdateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    emailFolderId = customEmailFolderId,
                    customFolderName = "UpdatedFolder",
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(any(), any())
            verify(mockEmailFolderService).updateCustom(
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
                UpdateCustomEmailFolderUseCaseInput(
                    emailAddressId = customEmailAddressId,
                    emailFolderId = mockFolderId,
                    customFolderName = "UpdatedFolder",
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(any(), any())
            verify(mockEmailFolderService).updateCustom(
                check { request ->
                    request.emailFolderId shouldBe mockFolderId
                    request.emailAddressId shouldBe customEmailAddressId
                },
            )
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should throw KeyNotFoundException when symmetric key not found`() =
        runTest {
            mockServiceKeyManager.stub {
                on { getCurrentSymmetricKeyId() } doReturn null
            }

            val input =
                UpdateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    emailFolderId = mockFolderId,
                    customFolderName = "UpdatedFolder",
                )

            shouldThrow<KeyNotFoundException> {
                useCase.execute(input)
            }

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        }

    @Test
    fun `execute() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockEmailFolderService.stub {
                onBlocking { updateCustom(any()) } doThrow NotAuthorizedException("Mock")
            }

            val input =
                UpdateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    emailFolderId = mockFolderId,
                    customFolderName = "UpdatedFolder",
                )

            shouldThrow<SudoEmailClient.EmailFolderException.AuthenticationException> {
                useCase.execute(input)
            }

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(any(), any())
            verify(mockEmailFolderService).updateCustom(any())
        }

    @Test
    fun `execute() should handle special characters in folder name`() =
        runTest {
            val specialFolderName = "Updated Special Folder! @#$%"
            val input =
                UpdateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    emailFolderId = mockFolderId,
                    customFolderName = specialFolderName,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(mockSymmetricKeyId, specialFolderName.toByteArray())
            verify(mockEmailFolderService).updateCustom(any())
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should unseal folder correctly`() =
        runTest {
            val unsealedFolderName = "UnsealedUpdatedFolder"
            val sealedFolderWithCustomName =
                EntityDataFactory.getSealedEmailFolderEntity(
                    id = "updatedFolderId",
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
                onBlocking { updateCustom(any()) } doReturn sealedFolderWithCustomName
            }

            val input =
                UpdateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    emailFolderId = "updatedFolderId",
                    customFolderName = "NewUpdatedName",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.id shouldBe "updatedFolderId"
            result.emailAddressId shouldBe mockEmailAddressId
            result.customFolderName shouldNotBe null

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(any(), any())
            verify(mockEmailFolderService).updateCustom(any())
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should handle long folder names`() =
        runTest {
            val longFolderName = "A".repeat(100)
            val input =
                UpdateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    emailFolderId = mockFolderId,
                    customFolderName = longFolderName,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(mockSymmetricKeyId, longFolderName.toByteArray())
            verify(mockEmailFolderService).updateCustom(any())
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should handle unicode characters in folder name`() =
        runTest {
            val unicodeFolderName = "Updated Folder 📁 with émojis 🎉"
            val input =
                UpdateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    emailFolderId = mockFolderId,
                    customFolderName = unicodeFolderName,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(mockSymmetricKeyId, unicodeFolderName.toByteArray())
            verify(mockEmailFolderService).updateCustom(any())
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should not seal when customFolderName is null`() =
        runTest {
            val sealedFolderWithoutCustomName =
                EntityDataFactory.getSealedEmailFolderEntity(
                    id = mockFolderId,
                    emailAddressId = mockEmailAddressId,
                    folderName = "INBOX",
                    sealedCustomFolderName = null,
                )

            mockEmailFolderService.stub {
                onBlocking { updateCustom(any()) } doReturn sealedFolderWithoutCustomName
            }

            val input =
                UpdateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    emailFolderId = mockFolderId,
                    customFolderName = null,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.customFolderName shouldBe null

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            // Note: sealString should NOT be called when customFolderName is null
            verify(mockEmailFolderService).updateCustom(
                check { request ->
                    request.customFolderName shouldBe null
                },
            )
        }

    @Test
    fun `execute() should pass all parameters correctly to service`() =
        runTest {
            val testEmailFolderId = "test-folder-123"
            val testEmailAddressId = "test-email-address-456"
            val testCustomFolderName = "Test Updated Folder"
            val input =
                UpdateCustomEmailFolderUseCaseInput(
                    emailAddressId = testEmailAddressId,
                    emailFolderId = testEmailFolderId,
                    customFolderName = testCustomFolderName,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(mockSymmetricKeyId, testCustomFolderName.toByteArray())
            verify(mockEmailFolderService).updateCustom(
                check { request ->
                    request.emailFolderId shouldBe testEmailFolderId
                    request.emailAddressId shouldBe testEmailAddressId
                    request.customFolderName shouldNotBe null
                },
            )
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }
}
