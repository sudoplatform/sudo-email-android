/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudoemail.graphql.UnblockEmailAddressesMutation
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.util.EmailAddressParser
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudoemail.util.StringHasher
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
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

    private val mutationSuccessResponse by before {
        JSONObject(
            """
                {
                    'unblockEmailAddresses': {
                        '__typename': 'UnblockAddressesResult',
                        'status': 'SUCCESS',
                        'failedAddresses': [],
                        'successAddresses': []
                    }
                }
            """.trimIndent(),
        )
    }

    private val mutationFailedResponse by before {
        JSONObject(
            """
                {
                    'unblockEmailAddresses': {
                        '__typename': 'UnblockAddressesResult',
                        'status': 'FAILED',
                        'failedAddresses': [],
                        'successAddresses': []
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
                mutate<String>(
                    argThat { this.query.equals(UnblockEmailAddressesMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(mutationSuccessResponse.toString(), null),
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
            on { getCurrentSymmetricKeyId() } doReturn "symmetricKeyId"
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
            mockContext,
            GraphQLClient(mockApiCategory),
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
            mockServiceKeyManager,
            mockApiCategory,
            mockS3Client,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `unblockEmailAddresses() should throw an InvalidInputException if passed an empty array`() =
        runTest {
            val addresses = emptyList<String>()

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                    client.unblockEmailAddresses(addresses)
                }
            }
            deferredResult.start()
            deferredResult.await()
        }

    @Test
    fun `unblockEmailAddresses() should throw an InvalidInputException if passed an array with duplicate emails`() =
        runTest {
            addresses.size shouldNotBe 0
            val uuid = UUID.randomUUID()
            val addresses = listOf(
                "spammyMcSpamface$uuid@spambot.com",
                "spammymcspamface$uuid@spambot.com",
            )

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                    client.unblockEmailAddresses(addresses)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockUserClient).getSubject()
        }

    @Test
    fun `unblockEmailAddresses() should return success when no errors present`() =
        runTest {
            addresses.size shouldNotBe 0

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.unblockEmailAddresses(addresses)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS

            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe UnblockEmailAddressesMutation.OPERATION_DOCUMENT
                    val input = it.variables["input"] as UnblockEmailAddressesRequest
                    input.owner shouldBe "mockOwner"
                    input.unblockedAddresses.size shouldBe addresses.size
                },
                any(),
                any(),
            )
            verify(mockUserClient).getSubject()
        }

    @Test
    fun `unblockEmailAddresses() should return failure when api returns failed status`() =
        runTest {
            addresses.size shouldNotBe 0

            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(UnblockEmailAddressesMutation.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(mutationFailedResponse.toString(), null),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.unblockEmailAddresses(addresses)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.FAILURE

            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe UnblockEmailAddressesMutation.OPERATION_DOCUMENT
                    val input = it.variables["input"] as UnblockEmailAddressesRequest
                    input.owner shouldBe "mockOwner"
                    input.unblockedAddresses.size shouldBe addresses.size
                },
                any(),
                any(),
            )
            verify(mockUserClient).getSubject()
        }

    @Test
    fun `unblockEmailAddresses() should return proper lists on partial`() = runTest {
        addresses.size shouldNotBe 0

        val expectedHashedValues = addresses.map {
            StringHasher.hashString("$owner|${EmailAddressParser.normalize(it)}")
        }

        val mutationPartialResponse by before {
            JSONObject(
                """
                {
                    'unblockEmailAddresses': {
                        '__typename': 'BlockEmailAddressesResult',
                        'status': 'PARTIAL',
                        'failedAddresses': ['${expectedHashedValues[0]}'],
                        'successAddresses': ['${expectedHashedValues[1]}']
                    }
                }
                """.trimIndent(),
            )
        }
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(UnblockEmailAddressesMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(mutationPartialResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
            }
        }
//
//        val partialResponse by before {
//            Response.builder<UnblockEmailAddressesMutation.Data>(UnblockEmailAddressesMutation(input))
//                .data(
//                    UnblockEmailAddressesMutation.Data(
//                        UnblockEmailAddressesMutation.UnblockEmailAddresses(
//                            "typename",
//                            UnblockEmailAddressesMutation.UnblockEmailAddresses.Fragments(
//                                UnblockAddressesResult(
//                                    "typename",
//                                    BlockEmailAddressesBulkUpdateStatus.PARTIAL,
//                                    listOf(expectedHashedValues[0]),
//                                    listOf(expectedHashedValues[1]),
//                                ),
//                            ),
//                        ),
//                    ),
//                )
//                .build()
//        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.unblockEmailAddresses(addresses)
        }
        deferredResult.start()
        val result = deferredResult.await()
        result shouldNotBe null

        result.status shouldBe BatchOperationStatus.PARTIAL
        result.failureValues?.shouldContain(addresses[0])
        result.successValues?.shouldContain(addresses[1])

        verify(mockApiCategory).mutate<String>(
            check {
                it.query shouldBe UnblockEmailAddressesMutation.OPERATION_DOCUMENT
                val input = it.variables["input"] as UnblockEmailAddressesRequest
                input.owner shouldBe "mockOwner"
                input.unblockedAddresses.size shouldBe addresses.size
            },
            any(),
            any(),
        )
        verify(mockUserClient).getSubject()
    }

    @Test
    fun `unblockEmailAddresses() should throw an error if response contains errors`() =
        runTest {
            addresses.size shouldNotBe 0

            val error = GraphQLResponse.Error(
                "mock",
                null,
                null,
                mapOf("errorType" to "SystemError"),
            )
            mockApiCategory.stub {
                on {
                    mutate<String>(
                        argThat { this.query.equals(UnblockEmailAddressesMutation.OPERATION_DOCUMENT) },
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
                    client.unblockEmailAddresses(addresses)
                }
            }
            deferredResult.start()
            val result = deferredResult.await()
            result shouldNotBe null

            verify(mockApiCategory).mutate<String>(
                check {
                    it.query shouldBe UnblockEmailAddressesMutation.OPERATION_DOCUMENT
                    val input = it.variables["input"] as UnblockEmailAddressesRequest
                    input.owner shouldBe "mockOwner"
                    input.unblockedAddresses.size shouldBe addresses.size
                },
                any(),
                any(),
            )
            verify(mockUserClient).getSubject()
        }
}
