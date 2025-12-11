/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailFolder

import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amplifyframework.api.graphql.GraphQLResponse
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.data.DataFactory.getEmailFolder
import com.sudoplatform.sudoemail.graphql.DeleteCustomEmailFolderMutation
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddress
import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.CreateCustomEmailFolderRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.DeleteCustomEmailFolderRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.ListEmailFoldersForEmailAddressIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.UpdateCustomEmailFolderRequest
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
 * Test the correct operation of [GraphQLEmailFolderService]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class GraphQLEmailFolderServiceTest : BaseTests() {
    override val mockApiClient by before {
        mock<ApiClient>()
    }

    private val instanceUnderTest by before {
        GraphQLEmailFolderService(
            mockApiClient,
            mockLogger,
        )
    }

    private val mockSealedAttributeInput by before {
        SealedAttributeInput(
            algorithm = "algorithm",
            keyId = "keyId",
            plainTextType = "string",
            base64EncodedSealedData = "base64EncodedSealedData",
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockApiClient,
        )
    }

    /** Begin ListEmailFoldersForEmailAddressIdTests */

    @Test
    fun `listEmailFoldersForEmailAddressId() should return list of sealed email folders when successful`() =
        runTest {
            val items =
                listOf(
                    EmailAddress.Folder("__typename", getEmailFolder()),
                )
            val queryResponse = DataFactory.listEmailFoldersForEmailAddressIdQueryResponse(items)
            mockApiClient.stub {
                onBlocking {
                    listEmailFoldersForEmailAddressIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailFoldersForEmailAddressIdRequest(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = null,
                )

            val result = instanceUnderTest.listForEmailAddressId(request)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.items[0].id shouldBe mockFolderId
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.limit shouldBe Optional.absent()
                    input.nextToken shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `listEmailFoldersForEmailAddressId() should return empty list when no folders exist for email address`() =
        runTest {
            val queryResponse = DataFactory.listEmailFoldersForEmailAddressIdQueryResponse(emptyList())
            mockApiClient.stub {
                onBlocking {
                    listEmailFoldersForEmailAddressIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailFoldersForEmailAddressIdRequest(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = null,
                )

            val result = instanceUnderTest.listForEmailAddressId(request)

            result shouldNotBe null
            result.items.size shouldBe 0
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(any())
        }

    @Test
    fun `listEmailFoldersForEmailAddressId() should handle limit parameter correctly`() =
        runTest {
            val items =
                listOf(
                    EmailAddress.Folder("__typename", getEmailFolder()),
                )
            val queryResponse = DataFactory.listEmailFoldersForEmailAddressIdQueryResponse(items)
            mockApiClient.stub {
                onBlocking {
                    listEmailFoldersForEmailAddressIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailFoldersForEmailAddressIdRequest(
                    emailAddressId = mockEmailAddressId,
                    limit = 10,
                    nextToken = null,
                )

            val result = instanceUnderTest.listForEmailAddressId(request)

            result shouldNotBe null
            result.items.size shouldBe 1

            verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.limit shouldBe Optional.present(10)
                    input.nextToken shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `listEmailFoldersForEmailAddressId() should handle nextToken parameter correctly`() =
        runTest {
            val items =
                listOf(
                    EmailAddress.Folder("__typename", getEmailFolder()),
                )
            val queryResponse = DataFactory.listEmailFoldersForEmailAddressIdQueryResponse(items, "nextToken123")
            mockApiClient.stub {
                onBlocking {
                    listEmailFoldersForEmailAddressIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailFoldersForEmailAddressIdRequest(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = "previousToken456",
                )

            val result = instanceUnderTest.listForEmailAddressId(request)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.nextToken shouldBe "nextToken123"

            verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.limit shouldBe Optional.absent()
                    input.nextToken shouldBe Optional.present("previousToken456")
                },
            )
        }

    @Test
    fun `listEmailFoldersForEmailAddressId() should handle both limit and nextToken parameters`() =
        runTest {
            val items =
                listOf(
                    EmailAddress.Folder("__typename", getEmailFolder()),
                )
            val queryResponse = DataFactory.listEmailFoldersForEmailAddressIdQueryResponse(items, "newNextToken")
            mockApiClient.stub {
                onBlocking {
                    listEmailFoldersForEmailAddressIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailFoldersForEmailAddressIdRequest(
                    emailAddressId = mockEmailAddressId,
                    limit = 50,
                    nextToken = "currentToken",
                )

            val result = instanceUnderTest.listForEmailAddressId(request)

            result shouldNotBe null
            result.items.size shouldBe 1
            result.nextToken shouldBe "newNextToken"

            verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.limit shouldBe Optional.present(50)
                    input.nextToken shouldBe Optional.present("currentToken")
                },
            )
        }

    @Test
    fun `listEmailFoldersForEmailAddressId() should use correct emailAddressId in query input`() =
        runTest {
            val items =
                listOf(
                    EmailAddress.Folder("__typename", getEmailFolder()),
                )
            val queryResponse = DataFactory.listEmailFoldersForEmailAddressIdQueryResponse(items)
            mockApiClient.stub {
                onBlocking {
                    listEmailFoldersForEmailAddressIdQuery(any())
                } doReturn queryResponse
            }

            val customEmailAddressId = "custom-email-address-id-12345"
            val request =
                ListEmailFoldersForEmailAddressIdRequest(
                    emailAddressId = customEmailAddressId,
                    limit = null,
                    nextToken = null,
                )

            val result = instanceUnderTest.listForEmailAddressId(request)

            result shouldNotBe null

            verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(
                check { input ->
                    input.emailAddressId shouldBe customEmailAddressId
                },
            )
        }

    @Test
    fun `listEmailFoldersForEmailAddressId() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    listEmailFoldersForEmailAddressIdQuery(any())
                } doThrow NotAuthorizedException("Mock")
            }

            val request =
                ListEmailFoldersForEmailAddressIdRequest(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = null,
                )

            shouldThrow<SudoEmailClient.EmailFolderException.AuthenticationException> {
                instanceUnderTest.listForEmailAddressId(request)
            }

            verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(any())
        }

    @Test
    fun `listEmailFoldersForEmailAddressId() should throw when response has errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            mockApiClient.stub {
                onBlocking {
                    listEmailFoldersForEmailAddressIdQuery(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                ListEmailFoldersForEmailAddressIdRequest(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = null,
                )

            shouldThrow<SudoEmailClient.EmailFolderException> {
                instanceUnderTest.listForEmailAddressId(request)
            }

            verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(any())
        }

    @Test
    fun `listEmailFoldersForEmailAddressId() should handle multiple email folders correctly`() =
        runTest {
            val items =
                listOf(
                    EmailAddress.Folder("__typename", getEmailFolder(id = "folder1", folderName = "INBOX")),
                    EmailAddress.Folder("__typename", getEmailFolder(id = "folder2", folderName = "SENT")),
                    EmailAddress.Folder("__typename", getEmailFolder(id = "folder3", folderName = "TRASH")),
                )
            val queryResponse = DataFactory.listEmailFoldersForEmailAddressIdQueryResponse(items)
            mockApiClient.stub {
                onBlocking {
                    listEmailFoldersForEmailAddressIdQuery(any())
                } doReturn queryResponse
            }

            val request =
                ListEmailFoldersForEmailAddressIdRequest(
                    emailAddressId = mockEmailAddressId,
                    limit = null,
                    nextToken = null,
                )

            val result = instanceUnderTest.listForEmailAddressId(request)

            result shouldNotBe null
            result.items.size shouldBe 3
            result.items[0].id shouldBe "folder1"
            result.items[1].id shouldBe "folder2"
            result.items[2].id shouldBe "folder3"
            result.nextToken shouldBe null

            verify(mockApiClient).listEmailFoldersForEmailAddressIdQuery(any())
        }

    /** Begin CreateCustomEmailFolderTests */

    @Test
    fun `createCustomEmailFolder() should return email folder when successful`() =
        runTest {
            val mutationResponse = DataFactory.createCustomEmailFolderMutationResponse("CustomFolder")
            mockApiClient.stub {
                onBlocking {
                    createCustomEmailFolderMutation(any())
                } doReturn mutationResponse
            }

            val request =
                CreateCustomEmailFolderRequest(
                    emailAddressId = mockEmailAddressId,
                    customFolderName = mockSealedAttributeInput,
                )

            val result = instanceUnderTest.createCustom(request)

            result shouldNotBe null
            result.id shouldBe mockFolderId

            verify(mockApiClient).createCustomEmailFolderMutation(
                check { input ->
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.customFolderName shouldBe mockSealedAttributeInput
                },
            )
        }

    @Test
    fun `createCustomEmailFolder() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    createCustomEmailFolderMutation(any())
                } doThrow NotAuthorizedException("Mock")
            }

            val request =
                CreateCustomEmailFolderRequest(
                    emailAddressId = mockEmailAddressId,
                    customFolderName = mockSealedAttributeInput,
                )

            shouldThrow<SudoEmailClient.EmailFolderException.AuthenticationException> {
                instanceUnderTest.createCustom(request)
            }

            verify(mockApiClient).createCustomEmailFolderMutation(any())
        }

    @Test
    fun `createCustomEmailFolder() should throw when response has errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            mockApiClient.stub {
                onBlocking {
                    createCustomEmailFolderMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                CreateCustomEmailFolderRequest(
                    emailAddressId = mockEmailAddressId,
                    customFolderName = mockSealedAttributeInput,
                )

            shouldThrow<SudoEmailClient.EmailFolderException> {
                instanceUnderTest.createCustom(request)
            }

            verify(mockApiClient).createCustomEmailFolderMutation(any())
        }

    @Test
    fun `createCustomEmailFolder() should use correct emailAddressId in mutation input`() =
        runTest {
            val customEmailAddressId = "custom-email-address-id-12345"
            val mutationResponse = DataFactory.createCustomEmailFolderMutationResponse("MyFolder")
            mockApiClient.stub {
                onBlocking {
                    createCustomEmailFolderMutation(any())
                } doReturn mutationResponse
            }

            val request =
                CreateCustomEmailFolderRequest(
                    emailAddressId = customEmailAddressId,
                    customFolderName = mockSealedAttributeInput,
                )

            val result = instanceUnderTest.createCustom(request)

            result shouldNotBe null

            verify(mockApiClient).createCustomEmailFolderMutation(
                check { input ->
                    input.emailAddressId shouldBe customEmailAddressId
                    input.customFolderName shouldBe mockSealedAttributeInput
                },
            )
        }

    @Test
    fun `createCustomEmailFolder() should handle special characters in folder name`() =
        runTest {
            val specialFolderName = "My Special Folder! @#$"
            val mutationResponse = DataFactory.createCustomEmailFolderMutationResponse(specialFolderName)
            mockApiClient.stub {
                onBlocking {
                    createCustomEmailFolderMutation(any())
                } doReturn mutationResponse
            }

            val request =
                CreateCustomEmailFolderRequest(
                    emailAddressId = mockEmailAddressId,
                    customFolderName = mockSealedAttributeInput,
                )

            val result = instanceUnderTest.createCustom(request)

            result shouldNotBe null

            verify(mockApiClient).createCustomEmailFolderMutation(
                check { input ->
                    input.customFolderName shouldBe mockSealedAttributeInput
                },
            )
        }

    /** Begin DeleteCustomEmailFolderTests */

    @Test
    fun `deleteCustomEmailFolder() should return email folder when successful`() =
        runTest {
            val mutationResponse = DataFactory.deleteCustomEmailFolderMutationResponse("DeletedFolder")
            mockApiClient.stub {
                onBlocking {
                    deleteCustomEmailFolderMutation(any())
                } doReturn mutationResponse
            }

            val request =
                DeleteCustomEmailFolderRequest(
                    emailFolderId = mockFolderId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = instanceUnderTest.deleteCustom(request)

            result shouldNotBe null
            result?.id shouldBe mockFolderId

            verify(mockApiClient).deleteCustomEmailFolderMutation(
                check { input ->
                    input.emailFolderId shouldBe mockFolderId
                    input.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `deleteCustomEmailFolder() should return null when folder not found`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deleteCustomEmailFolderMutation(any())
                } doReturn
                    GraphQLResponse(
                        DeleteCustomEmailFolderMutation.Data(null),
                        null,
                    )
            }

            val request =
                DeleteCustomEmailFolderRequest(
                    emailFolderId = "nonExistentFolderId",
                    emailAddressId = mockEmailAddressId,
                )

            val result = instanceUnderTest.deleteCustom(request)

            result shouldBe null

            verify(mockApiClient).deleteCustomEmailFolderMutation(
                check { input ->
                    input.emailFolderId shouldBe "nonExistentFolderId"
                    input.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `deleteCustomEmailFolder() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deleteCustomEmailFolderMutation(any())
                } doThrow NotAuthorizedException("Mock")
            }

            val request =
                DeleteCustomEmailFolderRequest(
                    emailFolderId = mockFolderId,
                    emailAddressId = mockEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailFolderException.AuthenticationException> {
                instanceUnderTest.deleteCustom(request)
            }

            verify(mockApiClient).deleteCustomEmailFolderMutation(any())
        }

    @Test
    fun `deleteCustomEmailFolder() should throw when response has errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            mockApiClient.stub {
                onBlocking {
                    deleteCustomEmailFolderMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                DeleteCustomEmailFolderRequest(
                    emailFolderId = mockFolderId,
                    emailAddressId = mockEmailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailFolderException> {
                instanceUnderTest.deleteCustom(request)
            }

            verify(mockApiClient).deleteCustomEmailFolderMutation(any())
        }

    @Test
    fun `deleteCustomEmailFolder() should use correct emailFolderId in mutation input`() =
        runTest {
            val customEmailFolderId = "custom-folder-id-12345"
            val mutationResponse = DataFactory.deleteCustomEmailFolderMutationResponse("DeletedFolder")
            mockApiClient.stub {
                onBlocking {
                    deleteCustomEmailFolderMutation(any())
                } doReturn mutationResponse
            }

            val request =
                DeleteCustomEmailFolderRequest(
                    emailFolderId = customEmailFolderId,
                    emailAddressId = mockEmailAddressId,
                )

            val result = instanceUnderTest.deleteCustom(request)

            result shouldNotBe null

            verify(mockApiClient).deleteCustomEmailFolderMutation(
                check { input ->
                    input.emailFolderId shouldBe customEmailFolderId
                    input.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `deleteCustomEmailFolder() should use correct emailAddressId in mutation input`() =
        runTest {
            val customEmailAddressId = "custom-email-address-id-12345"
            val mutationResponse = DataFactory.deleteCustomEmailFolderMutationResponse("DeletedFolder")
            mockApiClient.stub {
                onBlocking {
                    deleteCustomEmailFolderMutation(any())
                } doReturn mutationResponse
            }

            val request =
                DeleteCustomEmailFolderRequest(
                    emailFolderId = mockFolderId,
                    emailAddressId = customEmailAddressId,
                )

            val result = instanceUnderTest.deleteCustom(request)

            result shouldNotBe null

            verify(mockApiClient).deleteCustomEmailFolderMutation(
                check { input ->
                    input.emailFolderId shouldBe mockFolderId
                    input.emailAddressId shouldBe customEmailAddressId
                },
            )
        }

    @Test
    fun `deleteCustomEmailFolder() should verify both parameters are passed correctly`() =
        runTest {
            val testEmailFolderId = "test-folder-123"
            val testEmailAddressId = "test-email-address-456"
            val mutationResponse = DataFactory.deleteCustomEmailFolderMutationResponse("DeletedFolder")
            mockApiClient.stub {
                onBlocking {
                    deleteCustomEmailFolderMutation(any())
                } doReturn mutationResponse
            }

            val request =
                DeleteCustomEmailFolderRequest(
                    emailFolderId = testEmailFolderId,
                    emailAddressId = testEmailAddressId,
                )

            val result = instanceUnderTest.deleteCustom(request)

            result shouldNotBe null
            result?.id shouldBe mockFolderId

            verify(mockApiClient).deleteCustomEmailFolderMutation(
                check { input ->
                    input.emailFolderId shouldBe testEmailFolderId
                    input.emailAddressId shouldBe testEmailAddressId
                },
            )
        }

    /** Begin UpdateCustomEmailFolderTests */

    @Test
    fun `updateCustomEmailFolder() should return email folder when successful`() =
        runTest {
            val emailFolder = getEmailFolder()
            val mutationResponse = DataFactory.updateCustomEmailFolderMutationResponse(emailFolder)
            mockApiClient.stub {
                onBlocking {
                    updateCustomEmailFolderMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateCustomEmailFolderRequest(
                    emailFolderId = mockFolderId,
                    emailAddressId = mockEmailAddressId,
                    customFolderName = mockSealedAttributeInput,
                )

            val result = instanceUnderTest.updateCustom(request)

            result shouldNotBe null
            result.id shouldBe mockFolderId

            verify(mockApiClient).updateCustomEmailFolderMutation(
                check { input ->
                    input.emailFolderId shouldBe mockFolderId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.values.customFolderName shouldBe Optional.present(mockSealedAttributeInput)
                },
            )
        }

    @Test
    fun `updateCustomEmailFolder() should handle null customFolderName`() =
        runTest {
            val emailFolder = getEmailFolder()
            val mutationResponse = DataFactory.updateCustomEmailFolderMutationResponse(emailFolder)
            mockApiClient.stub {
                onBlocking {
                    updateCustomEmailFolderMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateCustomEmailFolderRequest(
                    emailFolderId = mockFolderId,
                    emailAddressId = mockEmailAddressId,
                    customFolderName = null,
                )

            val result = instanceUnderTest.updateCustom(request)

            result shouldNotBe null
            result.id shouldBe mockFolderId

            verify(mockApiClient).updateCustomEmailFolderMutation(
                check { input ->
                    input.emailFolderId shouldBe mockFolderId
                    input.emailAddressId shouldBe mockEmailAddressId
                    input.values.customFolderName shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `updateCustomEmailFolder() should throw AuthenticationException when NotAuthorizedException occurs`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    updateCustomEmailFolderMutation(any())
                } doThrow NotAuthorizedException("Mock")
            }

            val request =
                UpdateCustomEmailFolderRequest(
                    emailFolderId = mockFolderId,
                    emailAddressId = mockEmailAddressId,
                    customFolderName = mockSealedAttributeInput,
                )

            shouldThrow<SudoEmailClient.EmailFolderException.AuthenticationException> {
                instanceUnderTest.updateCustom(request)
            }

            verify(mockApiClient).updateCustomEmailFolderMutation(any())
        }

    @Test
    fun `updateCustomEmailFolder() should throw when response has errors`() =
        runTest {
            val testError =
                GraphQLResponse.Error(
                    "Test generated error",
                    emptyList(),
                    emptyList(),
                    mapOf("errorType" to "ServiceError"),
                )
            mockApiClient.stub {
                onBlocking {
                    updateCustomEmailFolderMutation(any())
                } doReturn GraphQLResponse(null, listOf(testError))
            }

            val request =
                UpdateCustomEmailFolderRequest(
                    emailFolderId = mockFolderId,
                    emailAddressId = mockEmailAddressId,
                    customFolderName = mockSealedAttributeInput,
                )

            shouldThrow<SudoEmailClient.EmailFolderException> {
                instanceUnderTest.updateCustom(request)
            }

            verify(mockApiClient).updateCustomEmailFolderMutation(any())
        }

    @Test
    fun `updateCustomEmailFolder() should use correct emailFolderId in mutation input`() =
        runTest {
            val customEmailFolderId = "custom-folder-id-12345"
            val emailFolder = getEmailFolder(id = customEmailFolderId)
            val mutationResponse = DataFactory.updateCustomEmailFolderMutationResponse(emailFolder)
            mockApiClient.stub {
                onBlocking {
                    updateCustomEmailFolderMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateCustomEmailFolderRequest(
                    emailFolderId = customEmailFolderId,
                    emailAddressId = mockEmailAddressId,
                    customFolderName = mockSealedAttributeInput,
                )

            val result = instanceUnderTest.updateCustom(request)

            result shouldNotBe null

            verify(mockApiClient).updateCustomEmailFolderMutation(
                check { input ->
                    input.emailFolderId shouldBe customEmailFolderId
                    input.emailAddressId shouldBe mockEmailAddressId
                },
            )
        }

    @Test
    fun `updateCustomEmailFolder() should use correct emailAddressId in mutation input`() =
        runTest {
            val customEmailAddressId = "custom-email-address-id-12345"
            val emailFolder = getEmailFolder()
            val mutationResponse = DataFactory.updateCustomEmailFolderMutationResponse(emailFolder)
            mockApiClient.stub {
                onBlocking {
                    updateCustomEmailFolderMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateCustomEmailFolderRequest(
                    emailFolderId = mockFolderId,
                    emailAddressId = customEmailAddressId,
                    customFolderName = mockSealedAttributeInput,
                )

            val result = instanceUnderTest.updateCustom(request)

            result shouldNotBe null

            verify(mockApiClient).updateCustomEmailFolderMutation(
                check { input ->
                    input.emailFolderId shouldBe mockFolderId
                    input.emailAddressId shouldBe customEmailAddressId
                },
            )
        }

    @Test
    fun `updateCustomEmailFolder() should verify all parameters are passed correctly`() =
        runTest {
            val testEmailFolderId = "test-folder-123"
            val testEmailAddressId = "test-email-address-456"
            val emailFolder = getEmailFolder(id = testEmailFolderId)
            val mutationResponse = DataFactory.updateCustomEmailFolderMutationResponse(emailFolder)
            mockApiClient.stub {
                onBlocking {
                    updateCustomEmailFolderMutation(any())
                } doReturn mutationResponse
            }

            val request =
                UpdateCustomEmailFolderRequest(
                    emailFolderId = testEmailFolderId,
                    emailAddressId = testEmailAddressId,
                    customFolderName = mockSealedAttributeInput,
                )

            val result = instanceUnderTest.updateCustom(request)

            result shouldNotBe null
            result.id shouldBe testEmailFolderId

            verify(mockApiClient).updateCustomEmailFolderMutation(
                check { input ->
                    input.emailFolderId shouldBe testEmailFolderId
                    input.emailAddressId shouldBe testEmailAddressId
                    input.values.customFolderName shouldBe Optional.present(mockSealedAttributeInput)
                },
            )
        }

    /** End UpdateCustomEmailFolderTests */
}
