/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.UnblockEmailAddressesMutation
import com.sudoplatform.sudoemail.graphql.fragment.UnblockAddressesResult
import com.sudoplatform.sudoemail.graphql.type.BlockEmailAddressesBulkUpdateStatus
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.keys.KeyPair
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.sealing.SealingService
import com.sudoplatform.sudoemail.types.BatchOperationResult
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.inputs.UnblockEmailAddressesInput
import com.sudoplatform.sudoemail.util.EmailAddressParser
import com.sudoplatform.sudoemail.util.StringHasher
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.fail
import io.kotlintest.matchers.collections.shouldContain
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
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import com.sudoplatform.sudoemail.graphql.type.UnblockEmailAddressesInput as UnblockEmailAddressesRequest

/**
 * Test the correct operation of [SudoEmailClient.unblockEmailAddresses]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailUnblockAddressesTest : BaseTests() {
    private val owner = "mockOwner"
    private var addresses: List<String> = emptyList()
    private val input by before {
        UnblockEmailAddressesRequest.builder()
            .owner(owner)
            .unblockedAddresses(addresses)
            .build()
    }

    private val mutationResult by before {
        UnblockEmailAddressesMutation.UnblockEmailAddresses(
            "typename",
            UnblockEmailAddressesMutation.UnblockEmailAddresses.Fragments(
                UnblockAddressesResult(
                    "typename",
                    BlockEmailAddressesBulkUpdateStatus.SUCCESS,
                    emptyList(),
                    emptyList(),
                ),
            ),
        )
    }

    private val mutationResponse by before {
        Response.builder<UnblockEmailAddressesMutation.Data>(UnblockEmailAddressesMutation(input))
            .data(UnblockEmailAddressesMutation.Data(mutationResult))
            .build()
    }

    private val callbackHolder = CallbackHolder<UnblockEmailAddressesMutation.Data>()

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
            on { mutate(any<UnblockEmailAddressesMutation>()) } doReturn callbackHolder.mutationOperation
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
        mock<S3Client>().stub {
            onBlocking { upload(any(), anyString(), anyOrNull()) } doReturn "42"
        }
    }

    private val mockSealingService by before {
        mock<SealingService>()
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
            "emailBucket",
            "transientBucket",
            mockS3Client,
            mockS3Client,
        )
    }

    @Before
    fun init() {
        callbackHolder.callback = null
        addresses = listOf(
            "spammyMcSpamface${UUID.randomUUID()}@spambot.com",
            "spammyMcSpamface${UUID.randomUUID()}@spambot.com",
        )
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
    fun `unblockEmailAddresses() should throw an InvalidInputException if passed an empty array`() =
        runBlocking<Unit> {
            callbackHolder.callback shouldBe null
            val addresses = emptyList<String>()
            val input = UnblockEmailAddressesInput(owner, addresses)

            val deferredResult = async(Dispatchers.IO) {
                shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                    client.unblockEmailAddresses(input)
                }
            }
            deferredResult.start()

            delay(100L)
        }

    @Test
    fun `unblockEmailAddresses() should throw an InvalidInputException if passed an array with duplicate emails`() =
        runBlocking<Unit> {
            callbackHolder.callback shouldBe null
            addresses.size shouldNotBe 0
            val uuid = UUID.randomUUID()
            val addresses = listOf(
                "spammyMcSpamface$uuid@spambot.com",
                "spammymcspamface$uuid@spambot.com",
            )
            val input = UnblockEmailAddressesInput(owner, addresses)

            val deferredResult = async(Dispatchers.IO) {
                shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                    client.unblockEmailAddresses(input)
                }
            }
            deferredResult.start()

            delay(100L)
        }

    @Test
    fun `unblockEmailAddresses() should return success when no errors present`() =
        runBlocking<Unit> {
            callbackHolder.callback shouldBe null
            addresses.size shouldNotBe 0

            val input = UnblockEmailAddressesInput(owner, addresses)

            val deferredResult = async(Dispatchers.IO) {
                client.unblockEmailAddresses(input)
            }
            deferredResult.start()

            delay(100L)
            callbackHolder.callback shouldNotBe null
            callbackHolder.callback?.onResponse(mutationResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            when (result) {
                is BatchOperationResult.SuccessOrFailureResult -> {
                    result.status shouldBe BatchOperationStatus.SUCCESS
                }

                else -> {
                    fail("Unexpected BatchOperationResult")
                }
            }

            verify(mockAppSyncClient).mutate<
                UnblockEmailAddressesMutation.Data,
                UnblockEmailAddressesMutation,
                UnblockEmailAddressesMutation.Variables,
                >(
                org.mockito.kotlin.check {
                    it.variables().input().owner() shouldBe "mockOwner"
                    it.variables().input().unblockedAddresses().size shouldBe addresses.size
                },
            )
        }

    @Test
    fun `unblockEmailAddresses() should return failure when api returns failed status`() =
        runBlocking<Unit> {
            callbackHolder.callback shouldBe null
            addresses.size shouldNotBe 0

            val failureResponse by before {
                Response.builder<UnblockEmailAddressesMutation.Data>(
                    UnblockEmailAddressesMutation(
                        input,
                    ),
                )
                    .data(
                        UnblockEmailAddressesMutation.Data(
                            UnblockEmailAddressesMutation.UnblockEmailAddresses(
                                "typename",
                                UnblockEmailAddressesMutation.UnblockEmailAddresses.Fragments(
                                    UnblockAddressesResult(
                                        "typename",
                                        BlockEmailAddressesBulkUpdateStatus.FAILED,
                                        emptyList(),
                                        emptyList(),
                                    ),
                                ),
                            ),
                        ),
                    )
                    .build()
            }

            val input = UnblockEmailAddressesInput(owner, addresses)

            val deferredResult = async(Dispatchers.IO) {
                client.unblockEmailAddresses(input)
            }
            deferredResult.start()

            delay(100L)
            callbackHolder.callback shouldNotBe null
            callbackHolder.callback?.onResponse(failureResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            when (result) {
                is BatchOperationResult.SuccessOrFailureResult -> {
                    result.status shouldBe BatchOperationStatus.FAILURE
                }

                else -> {
                    fail("Unexpected BatchOperationResult")
                }
            }

            verify(mockAppSyncClient).mutate<
                UnblockEmailAddressesMutation.Data,
                UnblockEmailAddressesMutation,
                UnblockEmailAddressesMutation.Variables,
                >(
                org.mockito.kotlin.check {
                    it.variables().input().owner() shouldBe "mockOwner"
                    it.variables().input().unblockedAddresses().size shouldBe addresses.size
                },
            )
        }

    @Test
    fun `unblockEmailAddresses() should return proper lists on partial`() = runBlocking<Unit> {
        callbackHolder.callback shouldBe null
        addresses.size shouldNotBe 0

        val expectedHashedValues = addresses.map {
            StringHasher.hashString("$owner|${EmailAddressParser.normalize(it)}")
        }

        val partialResponse by before {
            Response.builder<UnblockEmailAddressesMutation.Data>(UnblockEmailAddressesMutation(input))
                .data(
                    UnblockEmailAddressesMutation.Data(
                        UnblockEmailAddressesMutation.UnblockEmailAddresses(
                            "typename",
                            UnblockEmailAddressesMutation.UnblockEmailAddresses.Fragments(
                                UnblockAddressesResult(
                                    "typename",
                                    BlockEmailAddressesBulkUpdateStatus.PARTIAL,
                                    listOf(expectedHashedValues[0]),
                                    listOf(expectedHashedValues[1]),
                                ),
                            ),
                        ),
                    ),
                )
                .build()
        }

        val input = UnblockEmailAddressesInput(owner, addresses)

        val deferredResult = async(Dispatchers.IO) {
            client.unblockEmailAddresses(input)
        }
        deferredResult.start()

        delay(100L)
        callbackHolder.callback shouldNotBe null
        callbackHolder.callback?.onResponse(partialResponse)

        val result = deferredResult.await()
        result shouldNotBe null

        when (result) {
            is BatchOperationResult.PartialResult -> {
                result.status shouldBe BatchOperationStatus.PARTIAL
                result.failureValues shouldContain addresses[0]
                result.successValues shouldContain addresses[1]
            }

            else -> {
                fail("Unexpected BatchOperationResult")
            }
        }

        verify(mockAppSyncClient).mutate<
            UnblockEmailAddressesMutation.Data,
            UnblockEmailAddressesMutation,
            UnblockEmailAddressesMutation.Variables,
            >(
            org.mockito.kotlin.check {
                it.variables().input().owner() shouldBe "mockOwner"
                it.variables().input().unblockedAddresses().size shouldBe addresses.size
            },
        )
    }

    @Test
    fun `unblockEmailAddresses() should throw an error if response contains errors`() =
        runBlocking<Unit> {
            callbackHolder.callback shouldBe null
            addresses.size shouldNotBe 0

            val errorResponse by before {
                val error = com.apollographql.apollo.api.Error(
                    "mock",
                    emptyList(),
                    mapOf("errorType" to "SystemError"),
                )
                Response.builder<UnblockEmailAddressesMutation.Data>(
                    UnblockEmailAddressesMutation(
                        input,
                    ),
                )
                    .errors(listOf(error))
                    .build()
            }

            val input = UnblockEmailAddressesInput(owner, addresses)
            val deferredResult = async(Dispatchers.IO) {
                shouldThrow<SudoEmailClient.EmailBlocklistException.FailedException> {
                    client.unblockEmailAddresses(input)
                }
            }
            deferredResult.start()

            delay(100L)
            callbackHolder.callback shouldNotBe null
            callbackHolder.callback?.onResponse(errorResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            verify(mockAppSyncClient).mutate<
                UnblockEmailAddressesMutation.Data,
                UnblockEmailAddressesMutation,
                UnblockEmailAddressesMutation.Variables,
                >(
                org.mockito.kotlin.check {
                    it.variables().input().owner() shouldBe "mockOwner"
                    it.variables().input().unblockedAddresses().size shouldBe addresses.size
                },
            )
        }
}
