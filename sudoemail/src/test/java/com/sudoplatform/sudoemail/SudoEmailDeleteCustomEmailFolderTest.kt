/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.inputs.DeleteCustomEmailFolderInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
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

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                deleteCustomEmailFolderMutation(
                    any(),
                )
            } doAnswer {
                DataFactory.deleteCustomEmailFolderMutationResponse(mockSeal(mockCustomFolderName))
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
            mockApiClient,
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
            mockApiClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `deleteCustomEmailFolder() should throw an error if graphQl mutation fails`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deleteCustomEmailFolderMutation(
                        any(),
                    )
                }.thenThrow(UnknownError("ERROR"))
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailFolderException.UnknownException> {
                        client.deleteCustomEmailFolder(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).deleteCustomEmailFolderMutation(
                any(),
            )
        }

    @Test
    fun `deleteCustomEmailFolder() should return deleted folder`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteCustomEmailFolder(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result!!.id shouldBe "folderId"
            result.customFolderName shouldBe mockCustomFolderName

            verify(mockApiClient).deleteCustomEmailFolderMutation(
                any(),
            )
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(anyString(), any<ByteArray>())
        }

    @Test
    fun `deleteCustomEmailFolder() should return null if folder not found`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    deleteCustomEmailFolderMutation(
                        any(),
                    )
                } doAnswer {
                    GraphQLResponse(null, null)
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteCustomEmailFolder(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldBe null

            verify(mockApiClient).deleteCustomEmailFolderMutation(
                any(),
            )
        }
}
