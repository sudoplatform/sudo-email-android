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
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import com.sudoplatform.sudoemail.graphql.type.UnblockEmailAddressesInput as UnblockEmailAddressesRequest

/**
 * Test the correct operation of [SudoEmailClient.unblockEmailAddressesByHashedValue]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailUnblockEmailAddressesByHashedValueTest : BaseTests() {
    private val owner = "mockOwner"
    private var hashedValues: List<String> = emptyList()
    private val input by before {
        UnblockEmailAddressesRequest.builder()
            .owner(owner)
            .unblockedAddresses(hashedValues)
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
            on { getSubject() } doReturn owner
        }
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<UnblockEmailAddressesMutation>()) } doReturn callbackHolder.mutationOperation
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>()
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
            mockContext,
            mockAppSyncClient,
            mockUserClient,
            mockLogger,
            mockServiceKeyManager,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
            "region",
            "emailBucket",
            "transientBucket",
            null,
            mockS3Client,
            mockS3Client,
        )
    }

    @Before
    fun init() {
        callbackHolder.callback = null
        hashedValues = listOf(
            "hashedValue${UUID.randomUUID()}",
            "hashedValue${UUID.randomUUID()}",
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockContext,
            mockUserClient,
            mockKeyManager,
            mockServiceKeyManager,
            mockAppSyncClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `unblockEmailAddresses() should throw an InvalidInputException if passed an empty array`() =
        runTest {
            callbackHolder.callback shouldBe null
            val hashedValues = emptyList<String>()

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                    client.unblockEmailAddressesByHashedValue(hashedValues)
                }
            }
            deferredResult.start()

            delay(100L)
        }

    @Test
    fun `unblockEmailAddresses() should return success when no errors present`() =
        runTest {
            callbackHolder.callback shouldBe null
            hashedValues.size shouldNotBe 0

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.unblockEmailAddressesByHashedValue(hashedValues)
            }
            deferredResult.start()

            delay(100L)
            callbackHolder.callback shouldNotBe null
            callbackHolder.callback?.onResponse(mutationResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            result.status shouldBe BatchOperationStatus.SUCCESS

            verify(mockAppSyncClient).mutate<
                UnblockEmailAddressesMutation.Data,
                UnblockEmailAddressesMutation,
                UnblockEmailAddressesMutation.Variables,
                >(
                org.mockito.kotlin.check {
                    it.variables().input().owner() shouldBe "mockOwner"
                    it.variables().input().unblockedAddresses().size shouldBe hashedValues.size
                },
            )
            verify(mockUserClient).getSubject()
        }

    @Test
    fun `unblockEmailAddresses() should return failure when api returns failed status`() =
        runTest {
            callbackHolder.callback shouldBe null
            hashedValues.size shouldNotBe 0

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

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.unblockEmailAddressesByHashedValue(hashedValues)
            }
            deferredResult.start()

            delay(100L)
            callbackHolder.callback shouldNotBe null
            callbackHolder.callback?.onResponse(failureResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            result.status shouldBe BatchOperationStatus.FAILURE

            verify(mockAppSyncClient).mutate<
                UnblockEmailAddressesMutation.Data,
                UnblockEmailAddressesMutation,
                UnblockEmailAddressesMutation.Variables,
                >(
                org.mockito.kotlin.check {
                    it.variables().input().owner() shouldBe "mockOwner"
                    it.variables().input().unblockedAddresses().size shouldBe hashedValues.size
                },
            )
            verify(mockUserClient).getSubject()
        }

    @Test
    fun `unblockEmailAddresses() should return proper lists on partial`() = runTest {
        callbackHolder.callback shouldBe null
        hashedValues.size shouldNotBe 0

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
                                    listOf(hashedValues[0]),
                                    listOf(hashedValues[1]),
                                ),
                            ),
                        ),
                    ),
                )
                .build()
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.unblockEmailAddressesByHashedValue(hashedValues)
        }
        deferredResult.start()

        delay(100L)
        callbackHolder.callback shouldNotBe null
        callbackHolder.callback?.onResponse(partialResponse)

        val result = deferredResult.await()
        result shouldNotBe null

        result.status shouldBe BatchOperationStatus.PARTIAL
        result.failureValues?.shouldContain(hashedValues[0])
        result.successValues?.shouldContain(hashedValues[1])

        verify(mockAppSyncClient).mutate<
            UnblockEmailAddressesMutation.Data,
            UnblockEmailAddressesMutation,
            UnblockEmailAddressesMutation.Variables,
            >(
            org.mockito.kotlin.check {
                it.variables().input().owner() shouldBe "mockOwner"
                it.variables().input().unblockedAddresses().size shouldBe hashedValues.size
            },
        )
        verify(mockUserClient).getSubject()
    }

    @Test
    fun `unblockEmailAddresses() should throw an error if response contains errors`() =
        runTest {
            callbackHolder.callback shouldBe null
            hashedValues.size shouldNotBe 0

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
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailBlocklistException.FailedException> {
                    client.unblockEmailAddressesByHashedValue(hashedValues)
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
                    it.variables().input().unblockedAddresses().size shouldBe hashedValues.size
                },
            )
            verify(mockUserClient).getSubject()
        }
}
