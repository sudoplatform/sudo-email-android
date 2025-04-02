/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.apollographql.apollo3.api.Optional
import com.sudoplatform.sudoemail.graphql.DeleteMessagesByFolderIdMutation
import com.sudoplatform.sudoemail.graphql.type.DeleteMessagesByFolderIdInput
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.inputs.DeleteMessagesForFolderIdInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.deleteMessagesForFolderId]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailDeleteMessagesForFolderIdTest : BaseTests() {

    private val mockFolderId = "mockFolderId"
    private val mockEmailAddressId = "mockEmailAddressId"
    private val mutationResponse by before {
        JSONObject(
            """
                {
                    'deleteMessagesByFolderId': '$mockFolderId'
                }
            """.trimIndent(),
        )
    }

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(DeleteMessagesByFolderIdMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(mutationResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
            }
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val mockServiceKeyManager by before {
        DefaultServiceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger,
        )
    }

    private val mockS3Client by before {
        mock<S3Client>()
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        DefaultSealingService(
            mockServiceKeyManager,
            mockLogger,
        )
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>()
    }

    private val client by before {
        DefaultSudoEmailClient(
            context,
            GraphQLClient(mockApiCategory),
            mockUserClient,
            mockLogger,
            mockServiceKeyManager,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
            "region",
            "identityBucket",
            "transientBucket",
            null,
            mockS3Client,
            mockS3Client,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockUserClient,
            mockKeyManager,
            mockApiCategory,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `deleteMessagesForFolderId() should return folder id on success`() = runTest {
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.deleteMessagesForFolderId(
                DeleteMessagesForFolderIdInput(
                    emailAddressId = mockEmailAddressId,
                    emailFolderId = mockFolderId,
                ),
            )
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldNotBe null
        result shouldBe mockFolderId

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe DeleteMessagesByFolderIdMutation.OPERATION_DOCUMENT
                val mutationInput = it.variables["input"] as DeleteMessagesByFolderIdInput
                mutationInput.folderId shouldBe mockFolderId
                mutationInput.emailAddressId shouldBe mockEmailAddressId
                mutationInput.hardDelete shouldBe Optional.absent()
            },
            any(),
            any(),
        )
    }

    @Test
    fun `deleteMessagesForFolderId() should pass hardDelete parameter properly`() = runTest {
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.deleteMessagesForFolderId(
                DeleteMessagesForFolderIdInput(
                    emailAddressId = mockEmailAddressId,
                    emailFolderId = mockFolderId,
                    hardDelete = false,
                ),
            )
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldNotBe null
        result shouldBe mockFolderId

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe DeleteMessagesByFolderIdMutation.OPERATION_DOCUMENT
                val mutationInput = it.variables["input"] as DeleteMessagesByFolderIdInput
                mutationInput.folderId shouldBe mockFolderId
                mutationInput.emailAddressId shouldBe mockEmailAddressId
                mutationInput.hardDelete shouldBe Optional.present(false)
            },
            any(),
            any(),
        )
    }

    @Test
    fun `deleteMessagesForFolderId() should throw when response has errors`() = runTest {
        val testError = GraphQLResponse.Error(
            "Test generated error",
            emptyList(),
            emptyList(),
            mapOf("errorType" to "EmailAddressNotFound"),
        )
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(DeleteMessagesByFolderIdMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            }.thenAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(null, listOf(testError)),
                )
                mock<GraphQLOperation<String>>()
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailFolderException.FailedException> {
                client.deleteMessagesForFolderId(
                    DeleteMessagesForFolderIdInput(
                        emailAddressId = mockEmailAddressId,
                        emailFolderId = mockFolderId,
                    ),
                )
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe DeleteMessagesByFolderIdMutation.OPERATION_DOCUMENT
                val mutationInput = it.variables["input"] as DeleteMessagesByFolderIdInput
                mutationInput.folderId shouldBe mockFolderId
                mutationInput.emailAddressId shouldBe mockEmailAddressId
                mutationInput.hardDelete shouldBe Optional.absent()
            },
            any(),
            any(),
        )
    }
}
