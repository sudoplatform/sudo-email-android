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
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.matchers.string.shouldMatch
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.createDraftEmailMessage]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailCreateDraftEmailMessageTest : BaseTests() {
    private val uuidRegex = Regex("^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}\$")

    private val input by before {
        CreateDraftEmailMessageInput(
            "rfc822data".toByteArray(),
            "senderEmailAddressId",
        )
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
        }
    }

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                getEmailAddressQuery(
                    any(),
                )
            } doAnswer {
                DataFactory.getEmailAddressQueryResponse()
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
    fun `createDraftEmailMessage() should log and throw an error if send address not found`() = runTest {
        val error = GraphQLResponse.Error(
            "mock",
            null,
            null,
            mapOf("errorType" to "AddressNotFound"),
        )
        mockApiClient.stub {
            onBlocking {
                getEmailAddressQuery(
                    any(),
                )
            } doAnswer {
                GraphQLResponse(null, listOf(error))
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                client.createDraftEmailMessage(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiClient).getEmailAddressQuery(
            any(),
        )
    }

    @Test
    fun `createDraftEmailMessage() should log and throw an error if s3 upload errors`() = runTest {
        val error = CancellationException("Unknown exception")

        mockS3Client.stub {
            onBlocking {
                upload(
                    any(),
                    any(),
                    anyOrNull(),
                )
            } doThrow error
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<CancellationException> {
                client.createDraftEmailMessage(input)
            }
        }

        deferredResult.start()
        deferredResult.await()

        verify(mockApiClient).getEmailAddressQuery(
            any(),
        )
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        verify(mockSealingService).sealString(
            check {
                it shouldBe "symmetricKeyId"
            },
            check {
                it shouldBe "rfc822data".toByteArray()
            },
        )
        verify(mockS3Client).upload(
            check {
                it shouldNotBe null
            },
            check {
                it shouldContain input.senderEmailAddressId
            },
            check {
                it shouldNotBe null
            },
        )
    }

    @Test
    fun `createDraftEmailMessage() should return uuid on success`() = runTest {
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.createDraftEmailMessage(input)
        }
        deferredResult.start()
        val result = deferredResult.await()
        result shouldMatch uuidRegex

        verify(mockApiClient).getEmailAddressQuery(
            any(),
        )
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        verify(mockSealingService).sealString("symmetricKeyId", input.rfc822Data)
        verify(mockS3Client).upload(
            check {
                it shouldNotBe null
            },
            check {
                it shouldContain input.senderEmailAddressId
            },
            check {
                it shouldNotBe null
            },
        )
    }
}
