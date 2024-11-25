/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudoemail.graphql.DeleteCustomEmailFolderMutation
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudoemail.types.inputs.DeleteCustomEmailFolderInput
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
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test correct operation of [SudoEmailClient.deleteCustomEmailFolder]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailDeleteCustomEmailFolderTest : BaseTests() {
    private val mockCustomFolderName = "mockCustomFolderName"
    private val mockCustomFolderId = "mockCustomFolderId"
    private val mockEmailAddressId = "mockEmailAddressId"
    private val input by before {
        DeleteCustomEmailFolderInput(
            mockCustomFolderId,
            mockEmailAddressId,
        )
    }

    private val mutationResponse by before {
        JSONObject(
            """
                {
                    'deleteCustomEmailFolder': {
                        '__typename': 'EmailFolder',
                        'id': 'folderId',
                        'owner': 'owner',
                        'owners': [{
                            '__typename': 'Owner',
                            'id': 'ownerId',
                            'issuer': 'issuer'
                        }],
                        'version': '1',
                        'createdAtEpochMs': 1.0,
                        'updatedAtEpochMs': 1.0,
                        'emailAddressId': 'emailAddressId',
                        'folderName': 'folderName',
                        'size': 0.0,
                        'unseenCount': 0.0,
                        'ttl': 1.0,
                        'customFolderName': {
                            '__typename': 'SealedAttribute',
                            'algorithm': '${SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING}',
                            'keyId': 'keyId',
                            'plainTextType': 'plainText',
                            'base64EncodedSealedData': '${mockSeal(mockCustomFolderName)}'
                        }
                    }
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

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { decryptWithSymmetricKeyId(anyString(), any()) } doReturn mockCustomFolderName.toByteArray()
        }
    }

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(DeleteCustomEmailFolderMutation.OPERATION_DOCUMENT) },
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

    private val mockS3Client by before {
        mock<S3Client>()
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        mock<SealingService>()
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
            mockServiceKeyManager,
            mockApiCategory,
            mockS3Client,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `deleteCustomEmailFolder() should throw an error if graphQl mutation fails`() = runTest {
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(DeleteCustomEmailFolderMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            }.thenThrow(UnknownError("ERROR"))
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailFolderException.UnknownException> {
                client.deleteCustomEmailFolder(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).mutate<String>(
            argThat { this.query.equals(DeleteCustomEmailFolderMutation.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `deleteCustomEmailFolder() should return deleted folder`() = runTest {
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.deleteCustomEmailFolder(input)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldNotBe null
        result!!.id shouldBe "folderId"
        result.customFolderName shouldBe mockCustomFolderName

        verify(mockApiCategory).mutate<String>(
            argThat { this.query.equals(DeleteCustomEmailFolderMutation.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
        verify(mockServiceKeyManager).decryptWithSymmetricKeyId(anyString(), any<ByteArray>())
    }

    @Test
    fun `deleteCustomEmailFolder() should return null if folder not found`() = runTest {
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(DeleteCustomEmailFolderMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(null, null),
                )
                mock<GraphQLOperation<String>>()
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.deleteCustomEmailFolder(input)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldBe null

        verify(mockApiCategory).mutate<String>(
            argThat { this.query.equals(DeleteCustomEmailFolderMutation.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }
}