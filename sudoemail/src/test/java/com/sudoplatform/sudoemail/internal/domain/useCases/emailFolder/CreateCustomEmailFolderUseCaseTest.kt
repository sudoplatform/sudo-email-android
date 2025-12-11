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
 * Test the correct operation of [CreateCustomEmailFolderUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class CreateCustomEmailFolderUseCaseTest : BaseTests() {
    private val sealedString = "sealedString".toByteArray()

    private val sealedEmailFolder by before {
        EntityDataFactory.getSealedEmailFolderEntity(
            id = mockFolderId,
            folderName = "folderName",
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
            onBlocking { createCustom(any()) } doReturn sealedEmailFolder
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { getCurrentSymmetricKeyId() } doReturn mockSymmetricKeyId
            on { decryptWithSymmetricKeyId(anyString(), any()) } doReturn mockCustomFolderName.toByteArray()
        }
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on { sealString(any(), any()) } doReturn sealedString
        }
    }

    private val useCase by before {
        CreateCustomEmailFolderUseCase(
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
    fun `execute() should return unsealed email folder when no error present`() =
        runTest {
            val input =
                CreateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    customFolderName = mockCustomFolderName,
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
            verify(mockSealingService).sealString(mockSymmetricKeyId, mockCustomFolderName.toByteArray())
            verify(mockEmailFolderService).createCustom(
                check { request ->
                    request.emailAddressId shouldBe mockEmailAddressId
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
    fun `execute() should seal custom folder name correctly`() =
        runTest {
            val input =
                CreateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    customFolderName = mockCustomFolderName,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.customFolderName shouldBe mockCustomFolderName

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(mockSymmetricKeyId, mockCustomFolderName.toByteArray())
            verify(mockEmailFolderService).createCustom(
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
    fun `execute() should use correct emailAddressId`() =
        runTest {
            val customEmailAddressId = "custom-email-address-id-12345"
            val input =
                CreateCustomEmailFolderUseCaseInput(
                    emailAddressId = customEmailAddressId,
                    mockCustomFolderName,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(mockSymmetricKeyId, mockCustomFolderName.toByteArray())
            verify(mockEmailFolderService).createCustom(
                check { request ->
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
                CreateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    mockCustomFolderName,
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
                onBlocking { createCustom(any()) } doThrow NotAuthorizedException("Mock")
            }

            val input =
                CreateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    mockCustomFolderName,
                )

            shouldThrow<SudoEmailClient.EmailFolderException.AuthenticationException> {
                useCase.execute(input)
            }

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(any(), any())
            verify(mockEmailFolderService).createCustom(any())
        }

    @Test
    fun `execute() should handle special characters in folder name`() =
        runTest {
            val specialFolderName = "My Special Folder! @#$%"
            val input =
                CreateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    customFolderName = specialFolderName,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(mockSymmetricKeyId, specialFolderName.toByteArray())
            verify(mockEmailFolderService).createCustom(any())
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should unseal folder correctly`() =
        runTest {
            val sealedFolderWithCustomName =
                EntityDataFactory.getSealedEmailFolderEntity(
                    id = "customFolderId",
                    emailAddressId = mockEmailAddressId,
                    folderName = "folderName",
                    sealedCustomFolderName =
                        SealedAttributeEntity(
                            algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                            keyId = mockSymmetricKeyId,
                            plainTextType = "string",
                            base64EncodedSealedData = mockSeal(mockCustomFolderName),
                        ),
                )

            mockEmailFolderService.stub {
                onBlocking { createCustom(any()) } doReturn sealedFolderWithCustomName
            }

            val input =
                CreateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    customFolderName = mockCustomFolderName,
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.id shouldBe "customFolderId"
            result.emailAddressId shouldBe mockEmailAddressId
            result.customFolderName shouldBe mockCustomFolderName

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(any(), any())
            verify(mockEmailFolderService).createCustom(any())
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should handle long folder names`() =
        runTest {
            val longFolderName = "A".repeat(100)
            val input =
                CreateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    customFolderName = longFolderName,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(mockSymmetricKeyId, longFolderName.toByteArray())
            verify(mockEmailFolderService).createCustom(any())
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should handle unicode characters in folder name`() =
        runTest {
            val unicodeFolderName = "My Folder 📁 with émojis 🎉"
            val input =
                CreateCustomEmailFolderUseCaseInput(
                    emailAddressId = mockEmailAddressId,
                    customFolderName = unicodeFolderName,
                )

            val result = useCase.execute(input)

            result shouldNotBe null

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService).sealString(mockSymmetricKeyId, unicodeFolderName.toByteArray())
            verify(mockEmailFolderService).createCustom(any())
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }
}
