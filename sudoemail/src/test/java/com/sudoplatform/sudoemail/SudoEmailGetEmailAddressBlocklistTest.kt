/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amazonaws.util.Base64
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudoemail.graphql.GetEmailAddressBlocklistQuery
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.BlockedEmailAddressAction
import com.sudoplatform.sudoemail.types.UnsealedBlockedAddressStatus
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.matchers.beInstanceOf
import io.kotlintest.should
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
import com.sudoplatform.sudoemail.graphql.type.GetEmailAddressBlocklistInput as GetEmailAddressBlocklistRequest

/**
 * Test the correct operation of [SudoEmailClient.getEmailAddressBlocklist]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetEmailAddressBlocklistTest : BaseTests() {
    private val owner = "mockOwner"
    private val mockEmailAddressId = "mockEmailAddressId"
    private val mockData = listOf(
        mapOf(
            "sealedData" to String(Base64.encode("dummySealedData1".toByteArray())),
            "unsealedData" to "dummyUnsealedData1".toByteArray(),
            "hashedValue" to "hashedValue1",
            "action" to BlockedEmailAddressAction.DROP,
            "emailAddressId" to mockEmailAddressId,
        ),
        mapOf(
            "sealedData" to String(Base64.encode("dummySealedData2".toByteArray())),
            "unsealedData" to "dummyUnsealedData2".toByteArray(),
            "hashedValue" to "hashedValue2",
            "action" to BlockedEmailAddressAction.SPAM,
        ),
    )

    private val queryResponse by before {
        JSONObject(
            """
            {
                'getEmailAddressBlocklist': {
                    '__typename': 'typename',
                    'blockedAddresses': [{
                        '__typename': 'BlockedAddress',
                        'sealedValue': {
                            '__typename': 'SealedAttribute',
                            'algorithm': 'algorithm',
                            'keyId': 'keyId',
                            'plainTextType': 'string',
                            'base64EncodedSealedData': '${mockData[0]["sealedData"] as String}'
                        },
                        'hashedBlockedValue': '${mockData[0]["hashedValue"] as String}',
                        'action': '${mockData[0]["action"]}',
                        'emailAddressId': '${mockData[0]["emailAddressId"]}'
                    },
                    {
                        '__typename': 'BlockedAddress',
                        'sealedValue': {
                            '__typename': 'SealedAttribute',
                            'algorithm': 'algorithm',
                            'keyId': 'keyId',
                            'plainTextType': 'string',
                            'base64EncodedSealedData': '${mockData[1]["sealedData"] as String}'
                        },
                        'hashedBlockedValue': '${mockData[1]["hashedValue"] as String}',
                        'action': '${mockData[1]["action"]}'
                    }]
                }
            }
            """.trimIndent(),
        )
    }

    private val queryResponseWithEmptyList by before {
        JSONObject(
            """
            {
                'getEmailAddressBlocklist': {
                    '__typename': 'typename',
                    'blockedAddresses': []
                }
            }
            """.trimIndent(),
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

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            on {
                query<String>(
                    argThat { this.query.equals(GetEmailAddressBlocklistQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(queryResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
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
            } doReturnConsecutively listOf(
                mockData[0]["unsealedData"] as ByteArray,
                mockData[1]["unsealedData"] as ByteArray,
            )
        }
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>()
    }

    private val client by before {
        DefaultSudoEmailClient(
            mockContext,
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
            mockContext,
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
    fun `getEmailAddressBlocklist() should throw an error if response contains errors`() =
        runTest {
            val error = GraphQLResponse.Error(
                "mock",
                null,
                null,
                mapOf("errorType" to "SystemError"),
            )
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(GetEmailAddressBlocklistQuery.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(null, listOf(error)),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailBlocklistException.FailedException> {
                    client.getEmailAddressBlocklist()
                }
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailAddressBlocklistQuery.OPERATION_DOCUMENT
                    val input = it.variables["input"] as GetEmailAddressBlocklistRequest
                    input.owner shouldBe owner
                },
                any(),
                any(),
            )
            verify(mockUserClient).getSubject()
        }

    @Test
    fun `getEmailAddressBlocklist() should return an empty list if no addresses are returned`() =
        runTest {
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(GetEmailAddressBlocklistQuery.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(queryResponseWithEmptyList.toString(), null),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.getEmailAddressBlocklist()
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.size shouldBe 0

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailAddressBlocklistQuery.OPERATION_DOCUMENT
                    val input = it.variables["input"] as GetEmailAddressBlocklistRequest
                    input.owner shouldBe owner
                },
                any(),
                any(),
            )
            verify(mockUserClient).getSubject()
        }

    @Test
    fun `getEmailAddressBlocklist() returns array of unsealed values on success`() =
        runTest {
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.getEmailAddressBlocklist()
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.mapIndexed { index, unsealedBlockedAddress ->
                unsealedBlockedAddress.hashedBlockedValue shouldBe mockData[index]["hashedValue"]
                unsealedBlockedAddress.address shouldBe (mockData[index]["unsealedData"] as ByteArray).decodeToString()
                unsealedBlockedAddress.status shouldBe UnsealedBlockedAddressStatus.Completed
                unsealedBlockedAddress.action shouldBe mockData[index]["action"]
                unsealedBlockedAddress.emailAddressId shouldBe mockData[index]["emailAddressId"]
            }

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailAddressBlocklistQuery.OPERATION_DOCUMENT
                    val input = it.variables["input"] as GetEmailAddressBlocklistRequest
                    input.owner shouldBe owner
                },
                any(),
                any(),
            )
            val argumentCaptor = argumentCaptor<ByteArray>()
            verify(mockSealingService, times(2)).unsealString(
                check {
                    it shouldBe "keyId"
                },
                argumentCaptor.capture(),
            )
            argumentCaptor.firstValue shouldBe Base64.decode(mockData[0]["sealedData"] as String)
            argumentCaptor.secondValue shouldBe Base64.decode(mockData[1]["sealedData"] as String)
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
                } doReturn mockData[1]["unsealedData"] as ByteArray
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.getEmailAddressBlocklist()
            }
            deferredResult.start()
            val result = deferredResult.await()
            result shouldNotBe null

            result[0].address shouldBe ""
            result[0].hashedBlockedValue shouldBe mockData[0]["hashedValue"]
            result[0].status should beInstanceOf<UnsealedBlockedAddressStatus.Failed>()

            result[1].address shouldBe (mockData[1]["unsealedData"] as ByteArray).decodeToString()
            result[1].hashedBlockedValue shouldBe mockData[1]["hashedValue"]
            result[1].action shouldBe mockData[1]["action"]
            result[1].status shouldBe UnsealedBlockedAddressStatus.Completed

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailAddressBlocklistQuery.OPERATION_DOCUMENT
                    val input = it.variables["input"] as GetEmailAddressBlocklistRequest
                    input.owner shouldBe owner
                },
                any(),
                any(),
            )

            val argumentCaptor = argumentCaptor<ByteArray>()
            verify(mockSealingService).unsealString(
                check {
                    it shouldBe "keyId"
                },
                argumentCaptor.capture(),
            )
            argumentCaptor.firstValue shouldBe Base64.decode(mockData[1]["sealedData"] as String)
            verify(mockUserClient).getSubject()
            verify(mockServiceKeyManager, times(2)).symmetricKeyExists(any<String>())
        }
}
