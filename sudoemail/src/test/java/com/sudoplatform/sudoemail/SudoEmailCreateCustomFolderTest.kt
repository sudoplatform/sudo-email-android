/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.inputs.CreateCustomEmailFolderInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test correct operation of [SudoEmailClient.createCustomEmailFolder]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailCreateCustomFolderTest : BaseTests() {
    private val mockCustomFolderName = "mockCustomFolderName"
    private val input by before {
        CreateCustomEmailFolderInput(
            "mockEmailAddressId",

            mockCustomFolderName,
        )
    }

    private val mutationResponse by before {
    }

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { encryptWithSymmetricKey(anyString(), any()) } doReturn ByteArray(42)
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { getCurrentSymmetricKeyId() } doReturn "symmetricKeyId"
            on { generateNewCurrentSymmetricKey() } doReturn "newSymmetricKeyId"
            on { decryptWithSymmetricKeyId(anyString(), any()) } doReturn mockCustomFolderName.toByteArray()
        }
    }

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                createCustomEmailFolderMutation(
                    any(),
                )
            } doAnswer {
                DataFactory.createCustomEmailFolderMutationResponse(mockSeal(mockCustomFolderName))
            }
        }
    }

    private val mockUploadResponse by before {
        "42"
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking {
                upload(
                    any(),
                    any(),
                    anyOrNull(),
                )
            } doReturn mockUploadResponse
        }
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on { sealString(any(), any()) } doReturn "sealString".toByteArray()
        }
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
    fun `createCustomEmailFolder() should throw an error if symmetricKeyId is not found`() = runTest {
        mockServiceKeyManager.stub {
            on { getCurrentSymmetricKeyId() } doReturn null
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailFolderException.UnknownException> {
                client.createCustomEmailFolder(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `createCustomEmailFolder() should throw an error if graphQl mutation fails`() = runTest {
        mockApiClient.stub {
            onBlocking {
                createCustomEmailFolderMutation(
                    any(),
                )
            }.thenThrow(UnknownError("ERROR"))
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailFolderException.UnknownException> {
                client.createCustomEmailFolder(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        verify(mockSealingService).sealString(any(), any())
        verify(mockApiClient).createCustomEmailFolderMutation(
            any(),
        )
    }

    @Test
    fun `createCustomEmailFolder() should return newly created folder`() = runTest {
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.createCustomEmailFolder(input)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result.id shouldBe "folderId"
        result.customFolderName shouldBe mockCustomFolderName

        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        verify(mockSealingService).sealString(any(), any())
        verify(mockApiClient).createCustomEmailFolderMutation(
            any(),
        )
        verify(mockServiceKeyManager).decryptWithSymmetricKeyId(anyString(), any<ByteArray>())
    }
}
