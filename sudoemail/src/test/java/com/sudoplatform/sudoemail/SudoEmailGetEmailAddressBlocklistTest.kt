/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.util.Base64
import com.apollographql.apollo.api.Response
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.GetEmailAddressBlocklistQuery
import com.sudoplatform.sudoemail.graphql.fragment.GetEmailAddressBlocklistResponse
import com.sudoplatform.sudoemail.graphql.fragment.SealedAttribute
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
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
    private val input by before {
        GetEmailAddressBlocklistRequest.builder()
            .owner(owner)
            .build()
    }
    private val mockData = listOf(
        mapOf(
            "sealedData" to String(Base64.encode("dummySealedData1".toByteArray())),
            "unsealedData" to "dummyUnsealedData1".toByteArray(),
            "hashedValue" to "hashedValue1",
        ),
        mapOf(
            "sealedData" to String(Base64.encode("dummySealedData2".toByteArray())),
            "unsealedData" to "dummyUnsealedData2".toByteArray(),
            "hashedValue" to "hashedValue2",
        ),
    )

    private val mockBlocklist by before {
        listOf(
            GetEmailAddressBlocklistResponse.BlockedAddress(
                "typename",
                GetEmailAddressBlocklistResponse.SealedValue(
                    "typename",
                    GetEmailAddressBlocklistResponse.SealedValue.Fragments(
                        SealedAttribute(
                            "typename",
                            "algorithm",
                            "keyId",
                            "string",
                            mockData[0]["sealedData"] as String,
                        ),
                    ),
                ),
                mockData[0]["hashedValue"] as String,
            ),
            GetEmailAddressBlocklistResponse.BlockedAddress(
                "typename",
                GetEmailAddressBlocklistResponse.SealedValue(
                    "typename",
                    GetEmailAddressBlocklistResponse.SealedValue.Fragments(
                        SealedAttribute(
                            "typename",
                            "algorithm",
                            "keyId",
                            "string",
                            mockData[1]["sealedData"] as String,
                        ),
                    ),
                ),
                mockData[1]["hashedValue"] as String,
            ),
        )
    }

    private val queryResult by before {
        GetEmailAddressBlocklistQuery.GetEmailAddressBlocklist(
            "typename",
            GetEmailAddressBlocklistQuery.GetEmailAddressBlocklist.Fragments(
                GetEmailAddressBlocklistResponse(
                    "typename",
                    mockBlocklist,
                ),
            ),
        )
    }

    private val queryResponse by before {
        Response.builder<GetEmailAddressBlocklistQuery.Data>(GetEmailAddressBlocklistQuery(input))
            .data(GetEmailAddressBlocklistQuery.Data(queryResult))
            .build()
    }

    private val callbackHolder = CallbackHolder<GetEmailAddressBlocklistQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn owner
        }
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<GetEmailAddressBlocklistQuery>()) } doReturn callbackHolder.queryOperation
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val mockDeviceKeyManager by before {
        mock<DeviceKeyManager>().stub {
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
            mockAppSyncClient,
            mockUserClient,
            mockLogger,
            mockDeviceKeyManager,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
            "region",
            "identityBucket",
            "transientBucket",
            mockS3Client,
            mockS3Client,
        )
    }

    @Before
    fun init() {
        callbackHolder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockContext,
            mockUserClient,
            mockKeyManager,
            mockDeviceKeyManager,
            mockAppSyncClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `getEmailAddressBlocklist() should throw an error if response contains errors`() =
        runBlocking<Unit> {
            callbackHolder shouldNotBe null

            val errorResponse by before {
                val error = com.apollographql.apollo.api.Error(
                    "mock",
                    emptyList(),
                    mapOf("errorType" to "SystemError"),
                )
                Response.builder<GetEmailAddressBlocklistQuery.Data>(
                    GetEmailAddressBlocklistQuery(
                        input,
                    ),
                )
                    .errors(listOf(error))
                    .build()
            }

            val deferredResult = async(Dispatchers.IO) {
                shouldThrow<SudoEmailClient.EmailBlocklistException.FailedException> {
                    client.getEmailAddressBlocklist()
                }
            }
            deferredResult.start()

            delay(100L)
            callbackHolder.callback shouldNotBe null
            callbackHolder.callback?.onResponse(errorResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            verify(mockAppSyncClient).query<
                GetEmailAddressBlocklistQuery.Data,
                GetEmailAddressBlocklistQuery,
                GetEmailAddressBlocklistQuery.Variables,
                >(
                check {
                    it.variables().input().owner() shouldBe owner
                },
            )
            verify(mockUserClient).getSubject()
        }

    @Test
    fun `getEmailAddressBlocklist() should return an empty list if no addresses are returned`() =
        runBlocking<Unit> {
            callbackHolder shouldNotBe null

            val emptyResponse by before {
                Response.builder<GetEmailAddressBlocklistQuery.Data>(
                    GetEmailAddressBlocklistQuery(
                        input,
                    ),
                ).data(
                    GetEmailAddressBlocklistQuery.Data(
                        GetEmailAddressBlocklistQuery.GetEmailAddressBlocklist(
                            "typename",
                            GetEmailAddressBlocklistQuery.GetEmailAddressBlocklist.Fragments(
                                GetEmailAddressBlocklistResponse(
                                    "typename",
                                    emptyList(),
                                ),
                            ),
                        ),
                    ),
                ).build()
            }

            val deferredResult = async(Dispatchers.IO) {
                client.getEmailAddressBlocklist()
            }
            deferredResult.start()

            delay(100L)
            callbackHolder.callback shouldNotBe null
            callbackHolder.callback?.onResponse(emptyResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            result.size shouldBe 0

            verify(mockAppSyncClient).query<
                GetEmailAddressBlocklistQuery.Data,
                GetEmailAddressBlocklistQuery,
                GetEmailAddressBlocklistQuery.Variables,
                >(
                check {
                    it.variables().input().owner() shouldBe owner
                },
            )
            verify(mockUserClient).getSubject()
        }

    @Test
    fun `getEmailAddressBlocklist() returns array of unsealed values on success`() =
        runBlocking<Unit> {
            callbackHolder shouldNotBe null

            val deferredResult = async(Dispatchers.IO) {
                client.getEmailAddressBlocklist()
            }
            deferredResult.start()

            delay(100L)
            callbackHolder.callback shouldNotBe null
            callbackHolder.callback?.onResponse(queryResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            result.size shouldBe mockBlocklist.size

            result.mapIndexed { index, unsealedBlockedAddress ->
                unsealedBlockedAddress.hashedBlockedValue shouldBe mockData[index]["hashedValue"]
                unsealedBlockedAddress.address shouldBe (mockData[index]["unsealedData"] as ByteArray).decodeToString()
                unsealedBlockedAddress.status shouldBe UnsealedBlockedAddressStatus.Completed
            }

            verify(mockAppSyncClient).query<
                GetEmailAddressBlocklistQuery.Data,
                GetEmailAddressBlocklistQuery,
                GetEmailAddressBlocklistQuery.Variables,
                >(
                check {
                    it.variables().input().owner() shouldBe owner
                },
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
            verify(mockDeviceKeyManager, times(2)).symmetricKeyExists(any<String>())
        }

    @Test
    fun `getEmailAddressBlocklist() returns with failed status and error type when necessary`() =
        runBlocking<Unit> {
            mockDeviceKeyManager.stub {
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

            callbackHolder shouldNotBe null

            val deferredResult = async(Dispatchers.IO) {
                client.getEmailAddressBlocklist()
            }
            deferredResult.start()

            delay(100L)
            callbackHolder.callback shouldNotBe null
            callbackHolder.callback?.onResponse(queryResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            result.size shouldBe mockBlocklist.size

            result[0].address shouldBe ""
            result[0].hashedBlockedValue shouldBe mockData[0]["hashedValue"]
            result[0].status should beInstanceOf<UnsealedBlockedAddressStatus.Failed>()

            result[1].address shouldBe (mockData[1]["unsealedData"] as ByteArray).decodeToString()
            result[1].hashedBlockedValue shouldBe mockData[1]["hashedValue"]
            result[1].status shouldBe UnsealedBlockedAddressStatus.Completed

            verify(mockAppSyncClient).query<
                GetEmailAddressBlocklistQuery.Data,
                GetEmailAddressBlocklistQuery,
                GetEmailAddressBlocklistQuery.Variables,
                >(
                check {
                    it.variables().input().owner() shouldBe owner
                },
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
            verify(mockDeviceKeyManager, times(2)).symmetricKeyExists(any<String>())
        }
}
