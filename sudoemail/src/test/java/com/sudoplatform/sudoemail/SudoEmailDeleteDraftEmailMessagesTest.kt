/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amazonaws.auth.CognitoCredentialsProvider
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.s3.S3Exception
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.DeleteEmailMessageSuccessResult
import com.sudoplatform.sudoemail.types.EmailMessageOperationFailureResult
import com.sudoplatform.sudoemail.types.inputs.DeleteDraftEmailMessagesInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.inspectors.forAtLeastOne
import io.kotlintest.matchers.collections.shouldBeEmpty
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.deleteDraftEmailMessages]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailDeleteDraftEmailMessagesTest : BaseTests() {
    private val draftIds = listOf("draftId1", "draftId2")
    private val emailAddressId = "emailAddressId"

    private val input by before {
        DeleteDraftEmailMessagesInput(draftIds, emailAddressId)
    }

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockCognitoCredentialsProvider by before {
        mock<CognitoCredentialsProvider>().stub {
            on {
                identityId
            } doReturn "dummyIdentityId"
        }
    }
    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on {
                getCredentialsProvider()
            } doReturn mockCognitoCredentialsProvider
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { encryptWithSymmetricKey(anyString(), any()) } doReturn ByteArray(42)
        }
    }

    private val mockServiceKeyManager by before {
        DefaultServiceKeyManager("keyRingServiceName", mockUserClient, mockKeyManager, mockLogger)
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
            onBlocking {
                cancelScheduledDraftMessageMutation(
                    any(),
                )
            } doAnswer {
                DataFactory.cancelScheduledDraftMessageResponse()
            }
        }
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking {
                delete(any())
            } doReturn Unit
        }
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        DefaultSealingService(mockServiceKeyManager, mockLogger)
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
            mockApiClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `deleteDraftEmailMessages() should throw an error if email address not found`() =
        runTest {
            val error =
                GraphQLResponse.Error(
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
                onBlocking {
                    cancelScheduledDraftMessageMutation(
                        any(),
                    )
                } doAnswer {
                    DataFactory.cancelScheduledDraftMessageResponse()
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                        client.deleteDraftEmailMessages(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `deleteDraftEmailMessages() should return success result if all operations succeeded`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteDraftEmailMessages(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            advanceUntilIdle()
            delay(3000)
            advanceUntilIdle()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS

            verify(mockApiClient, times(1 + draftIds.size)).getEmailAddressQuery(
                any(),
            )
            // S3 client delete method is called once per draft id
            val s3Captor = argumentCaptor<String>()
            verify(mockS3Client, times(2)).delete(s3Captor.capture())
            s3Captor.allValues shouldContain "email/$emailAddressId/draft/${draftIds[0]}"
            s3Captor.allValues shouldContain "email/$emailAddressId/draft/${draftIds[1]}"
            verify(mockApiClient, times(draftIds.size)).cancelScheduledDraftMessageMutation(
                any(),
            )
            verify(mockUserClient, times(draftIds.size)).getCredentialsProvider()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `deleteDraftEmailMessages() should return partial result if some operations failed`() =
        runTest {
            // Throw an exception from internal S3 client to provoke failure
            whenever(
                mockS3Client.delete(
                    argThat {
                        equals("email/$emailAddressId/draft/${draftIds[1]}")
                    },
                ),
            ).thenThrow(S3Exception.DeleteException("S3 delete failed"))

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteDraftEmailMessages(input)
                }
            deferredResult.start()
            val result = deferredResult.await()
            // Wait a little longer to allow the async operations to complete
            advanceUntilIdle()
            delay(3000)
            advanceUntilIdle()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.PARTIAL
            result.successValues?.shouldContain(
                DeleteEmailMessageSuccessResult(draftIds[0]),
            )
            result.failureValues?.shouldHaveSize(1)
            result.failureValues?.first() shouldBe
                EmailMessageOperationFailureResult(
                    draftIds[1],
                    "S3 delete failed",
                )

            verify(mockApiClient, times(2)).getEmailAddressQuery(
                any(),
            )
            // S3 client delete method is called once per draft id
            val s3Captor = argumentCaptor<String>()
            verify(mockS3Client, times(2)).delete(s3Captor.capture())
            s3Captor.allValues shouldContain "email/$emailAddressId/draft/${draftIds[0]}"
            s3Captor.allValues shouldContain "email/$emailAddressId/draft/${draftIds[1]}"
            verify(mockApiClient).cancelScheduledDraftMessageMutation(
                any(),
            )
            verify(mockUserClient).getCredentialsProvider()
        }

    @Test
    fun `deleteDraftEmailMessages() should return failure result if all operations failed`() =
        runTest {
            // Throw an exception from internal S3 client to provoke failure
            whenever(mockS3Client.delete(any()))
                .thenThrow(S3Exception.DeleteException("S3 delete failed"))

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.deleteDraftEmailMessages(input)
                }

            deferredResult.start()
            val result = deferredResult.await()
            result shouldNotBe null

            result.status shouldBe BatchOperationStatus.FAILURE
            result.successValues?.shouldBeEmpty()
            result.failureValues?.shouldHaveSize(2)
            result.failureValues?.forAtLeastOne {
                it.id shouldBe draftIds[0]
                it.errorType shouldBe "S3 delete failed"
            }
            result.failureValues?.forAtLeastOne {
                it.id shouldBe draftIds[1]
                it.errorType shouldBe "S3 delete failed"
            }

            verify(mockApiClient).getEmailAddressQuery(
                any(),
            )
            // S3 client delete method is called once per draft id
            val s3Captor = argumentCaptor<String>()
            verify(mockS3Client, times(2)).delete(s3Captor.capture())
            s3Captor.allValues shouldContain "email/$emailAddressId/draft/${draftIds[0]}"
            s3Captor.allValues shouldContain "email/$emailAddressId/draft/${draftIds[1]}"
        }
}
