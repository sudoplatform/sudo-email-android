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
import com.sudoplatform.sudoemail.graphql.fragment.SealedAttribute
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.keys.KeyPair
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.sealing.SealingService
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
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
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
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
    private val encodedData = String(Base64.encode("dummyEncodedData".toByteArray()))

    private val queryResult by before {
        GetEmailAddressBlocklistQuery.GetEmailAddressBlocklist(
            "typename",
            listOf(
                GetEmailAddressBlocklistQuery.SealedBlockedAddress(
                    "typename",
                    GetEmailAddressBlocklistQuery.SealedBlockedAddress.Fragments(
                        SealedAttribute(
                            "typename",
                            "algorithm",
                            "keyId",
                            "string",
                            encodedData,
                        ),
                    ),
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
            on { getSubject() } doReturn "subject"
            on { getRefreshToken() } doReturn "refreshToken"
        }
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<GetEmailAddressBlocklistQuery>()) } doReturn callbackHolder.queryOperation
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { getPassword(ArgumentMatchers.anyString()) } doReturn ByteArray(42)
            on { getPublicKeyData(ArgumentMatchers.anyString()) } doReturn ByteArray(42)
            on { getPrivateKeyData(ArgumentMatchers.anyString()) } doReturn ByteArray(42)
        }
    }

    private val mockDeviceKeyManager by before {
        mock<DeviceKeyManager>().stub {
            on { generateKeyPair() } doReturn KeyPair(
                keyId = "keyId",
                keyRingId = "keyRingId",
                publicKey = ByteArray(42),
                privateKey = ByteArray(42),
            )
            on { getCurrentSymmetricKeyId() } doReturn "symmetricKeyId"
        }
    }

    private val mockS3Client by before {
        mock<S3Client>()
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on { unsealString(any(), any()) } doReturn "unsealedString".toByteArray()
        }
    }

    private val client by before {
        DefaultSudoEmailClient(
            mockContext,
            mockAppSyncClient,
            mockUserClient,
            mockLogger,
            mockDeviceKeyManager,
            mockSealingService,
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
            mockAppSyncClient,
            mockS3Client,
            mockDeviceKeyManager,
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
                    client.getEmailAddressBlocklist(owner)
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
                            emptyList(),
                        ),
                    ),
                ).build()
            }

            val deferredResult = async(Dispatchers.IO) {
                client.getEmailAddressBlocklist(owner)
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
        }

    @Test
    fun `getEmailAddressBlocklist() returns array of unsealed values on success`() =
        runBlocking<Unit> {
            callbackHolder shouldNotBe null

            val deferredResult = async(Dispatchers.IO) {
                client.getEmailAddressBlocklist(owner)
            }
            deferredResult.start()

            delay(100L)
            callbackHolder.callback shouldNotBe null
            callbackHolder.callback?.onResponse(queryResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            result.size shouldBe 1
            result.first() shouldBe "unsealedString"

            verify(mockAppSyncClient).query<
                GetEmailAddressBlocklistQuery.Data,
                GetEmailAddressBlocklistQuery,
                GetEmailAddressBlocklistQuery.Variables,
                >(
                check {
                    it.variables().input().owner() shouldBe owner
                },
            )
            verify(mockSealingService).unsealString(
                check {
                    it shouldBe "keyId"
                },
                check {
                    it shouldBe Base64.decode(encodedData)
                },
            )
        }
}
