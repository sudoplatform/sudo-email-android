/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amazonaws.util.Base64
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.graphql.type.BlockedAddressAction
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.UnsealedBlockedAddressStatus
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.matchers.beInstanceOf
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.getEmailAddressBlocklist]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetEmailAddressBlocklistTest : BaseTests() {
    private val owner = "mockOwner"
    private val mockEmailAddressId = "mockEmailAddressId"
    private val mockData =
        listOf(
            DataFactory.GetEmailAddressBlocklistQueryDataValues(
                sealedData = String(Base64.encode("dummySealedData1".toByteArray())),
                unsealedData = "dummyUnsealedData1".toByteArray(),
                hashedValue = "hashedValue1",
                action = BlockedAddressAction.DROP,
                emailAddressId = mockEmailAddressId,
            ),
            DataFactory.GetEmailAddressBlocklistQueryDataValues(
                sealedData = String(Base64.encode("dummySealedData2".toByteArray())),
                unsealedData = "dummyUnsealedData2".toByteArray(),
                hashedValue = "hashedValue2",
                action = BlockedAddressAction.SPAM,
            ),
        )

    private val queryResponse by before {
        DataFactory.getEmailAddressBlocklistQueryResponse(
            mockData,
        )
    }

    private val queryResponseWithEmptyList by before {
        DataFactory.getEmailAddressBlocklistQueryResponse(
            emptyList(),
        )
    }

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn owner
        }
    }

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                getEmailAddressBlocklistQuery(
                    any(),
                )
            } doAnswer {
                queryResponse
            }
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { symmetricKeyExists(any<String>()) } doReturn true
        }
    }

    private val mockS3Client by before {
        mock<S3Client>()
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on {
                unsealString(
                    any(),
                    any(),
                )
            } doReturnConsecutively
                listOf(
                    mockData[0].unsealedData,
                    mockData[1].unsealedData,
                )
        }
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>()
    }

    private val client by before {
        DefaultSudoEmailClient(
            mockContext,
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
            mockContext,
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
    fun `getEmailAddressBlocklist() should throw an error if response contains errors`() =
        runTest {
            val error =
                GraphQLResponse.Error(
                    "mock",
                    null,
                    null,
                    mapOf("errorType" to "SystemError"),
                )
            mockApiClient.stub {
                onBlocking {
                    getEmailAddressBlocklistQuery(
                        any(),
                    )
                } doAnswer {
                    GraphQLResponse(null, listOf(error))
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailBlocklistException.FailedException> {
                        client.getEmailAddressBlocklist()
                    }
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null

            verify(mockApiClient).getEmailAddressBlocklistQuery(
                check { input ->
                    input.owner shouldBe owner
                },
            )
            verify(mockUserClient).getSubject()
        }

    @Test
    fun `getEmailAddressBlocklist() should return an empty list if no addresses are returned`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    getEmailAddressBlocklistQuery(
                        any(),
                    )
                } doAnswer {
                    queryResponseWithEmptyList
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailAddressBlocklist()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 0

            verify(mockApiClient).getEmailAddressBlocklistQuery(
                check { input ->
                    input.owner shouldBe owner
                },
            )
            verify(mockUserClient).getSubject()
        }

    @Test
    fun `getEmailAddressBlocklist() returns array of unsealed values on success`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailAddressBlocklist()
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.mapIndexed { index, unsealedBlockedAddress ->
                unsealedBlockedAddress.hashedBlockedValue shouldBe mockData[index].hashedValue
                unsealedBlockedAddress.address shouldBe (mockData[index].unsealedData).decodeToString()
                unsealedBlockedAddress.status shouldBe UnsealedBlockedAddressStatus.Completed
                unsealedBlockedAddress.action.toString() shouldBe mockData[index].action.toString()
                unsealedBlockedAddress.emailAddressId shouldBe mockData[index].emailAddressId
            }

            verify(mockApiClient).getEmailAddressBlocklistQuery(
                check { input ->
                    input.owner shouldBe owner
                },
            )
            val argumentCaptor = argumentCaptor<ByteArray>()
            verify(mockSealingService, times(2)).unsealString(
                check {
                    it shouldBe "keyId"
                },
                argumentCaptor.capture(),
            )
            argumentCaptor.firstValue shouldBe Base64.decode(mockData[0].sealedData)
            argumentCaptor.secondValue shouldBe Base64.decode(mockData[1].sealedData)
            verify(mockUserClient).getSubject()
            verify(mockServiceKeyManager, times(2)).symmetricKeyExists(any<String>())
        }

    @Test
    fun `getEmailAddressBlocklist() returns with failed status and error type when necessary`() =
        runTest {
            mockServiceKeyManager.stub {
                on { symmetricKeyExists(any<String>()) } doReturnConsecutively listOf(false, true)
            }
            mockSealingService.stub {
                on {
                    unsealString(
                        any<String>(),
                        any<ByteArray>(),
                    )
                } doReturn mockData[1].unsealedData
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.getEmailAddressBlocklist()
                }
            deferredResult.start()
            val result = deferredResult.await()
            result shouldNotBe null

            result[0].address shouldBe ""
            result[0].hashedBlockedValue shouldBe mockData[0].hashedValue
            result[0].status should beInstanceOf<UnsealedBlockedAddressStatus.Failed>()

            result[1].address shouldBe (mockData[1].unsealedData).decodeToString()
            result[1].hashedBlockedValue shouldBe mockData[1].hashedValue
            result[1].action.toString() shouldBe mockData[1].action.toString()
            result[1].status shouldBe UnsealedBlockedAddressStatus.Completed

            verify(mockApiClient).getEmailAddressBlocklistQuery(
                check { input ->
                    input.owner shouldBe owner
                },
            )

            val argumentCaptor = argumentCaptor<ByteArray>()
            verify(mockSealingService).unsealString(
                check {
                    it shouldBe "keyId"
                },
                argumentCaptor.capture(),
            )
            argumentCaptor.firstValue shouldBe Base64.decode(mockData[1].sealedData)
            verify(mockUserClient).getSubject()
            verify(mockServiceKeyManager, times(2)).symmetricKeyExists(any<String>())
        }
}
