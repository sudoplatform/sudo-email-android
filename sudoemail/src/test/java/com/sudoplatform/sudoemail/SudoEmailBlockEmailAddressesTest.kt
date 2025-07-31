/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.graphql.type.BlockEmailAddressesBulkUpdateStatus
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.BlockedEmailAddressAction
import com.sudoplatform.sudoemail.util.EmailAddressParser
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudoemail.util.StringHasher
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Test the correct operation of [SudoEmailClient.blockEmailAddresses]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailBlockEmailAddressesTest : BaseTests() {
    private val owner = "mockOwner"
    private var addresses: List<String> = emptyList()

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
                blockEmailAddressesMutation(
                    any(),
                )
            } doAnswer {
                DataFactory.blockEmailAddressMutationResponse()
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
        mock<SealingService>().stub {
            on { sealString(any(), any()) } doReturn "sealString".toByteArray()
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
            mockApiClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `blockEmailAddresses() should throw an InvalidInputException if passed an empty array`() =
        runTest {
            val addresses = emptyList<String>()

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailBlocklistException.InvalidInputException> {
                    client.blockEmailAddresses(addresses)
                }
            }
            deferredResult.start()
            deferredResult.await()
        }

    @Test
    fun `blockEmailAddresses() should de-duplicate an array with duplicate emails`() =
        runTest {
            addresses.size shouldNotBe 0
            val uuid = UUID.randomUUID()
            val addresses = listOf(
                "spammyMcSpamface$uuid@spambot.com",
                "spammymcspamface$uuid@spambot.com",
            )
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.blockEmailAddresses(addresses)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS

            verify(mockApiClient).blockEmailAddressesMutation(
                check { input ->
                    input.owner shouldBe "mockOwner"
                    input.blockedAddresses.size shouldBe addresses.size - 1
                    val inputAction = input.blockedAddresses.first().action.getOrNull()
                    inputAction.toString() shouldBe BlockedEmailAddressAction.DROP.toString()
                },
            )

            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService, times(addresses.size - 1)).sealString(any(), any())
            verify(mockUserClient).getSubject()
        }

    @Test
    fun `blockEmailAddresses() should return success when no errors present`() = runTest {
        addresses.size shouldNotBe 0

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.blockEmailAddresses(addresses)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldNotBe null
        result.status shouldBe BatchOperationStatus.SUCCESS

        verify(mockApiClient).blockEmailAddressesMutation(
            check { input ->
                input.owner shouldBe "mockOwner"
                input.blockedAddresses.size shouldBe addresses.size
                val inputAction = input.blockedAddresses.first().action.getOrNull()
                val expectedHash = StringHasher.hashString("$owner|${EmailAddressParser.normalize(addresses.first())}")
                input.blockedAddresses.find { blockedAddress ->
                    blockedAddress.hashedBlockedValue == expectedHash
                } shouldNotBe null
                inputAction.toString() shouldBe BlockedEmailAddressAction.DROP.toString()
            },
        )
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        verify(mockSealingService, times(2)).sealString(any(), any())
        verify(mockUserClient).getSubject()
    }

    @Test
    fun `blockEmailAddresses() should accept and pass action parameter`() = runTest {
        addresses.size shouldNotBe 0

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.blockEmailAddresses(addresses, BlockedEmailAddressAction.SPAM)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldNotBe null
        result.status shouldBe BatchOperationStatus.SUCCESS

        verify(mockApiClient).blockEmailAddressesMutation(
            check { input ->
                input.owner shouldBe "mockOwner"
                input.blockedAddresses.size shouldBe addresses.size
                val inputAction = input.blockedAddresses.first().action.getOrNull()
                inputAction.toString() shouldBe BlockedEmailAddressAction.SPAM.toString()
            },
        )
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        verify(mockSealingService, times(2)).sealString(any(), any())
        verify(mockUserClient).getSubject()
    }

    @Test
    fun `blockEmailAddresses() should accept and pass emailAddressId parameter`() = runTest {
        addresses.size shouldNotBe 0
        val mockEmailAddressId = "mockEmailAddressId"
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.blockEmailAddresses(addresses, emailAddressId = mockEmailAddressId)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldNotBe null
        result.status shouldBe BatchOperationStatus.SUCCESS

        verify(mockApiClient).blockEmailAddressesMutation(
            check { input ->
                input.owner shouldBe "mockOwner"
                input.blockedAddresses.size shouldBe addresses.size
                val inputAction = input.blockedAddresses.first().action.getOrNull()
                inputAction.toString() shouldBe BlockedEmailAddressAction.DROP.toString()
                val expectedHash = StringHasher.hashString("$mockEmailAddressId|${EmailAddressParser.normalize(addresses.first())}")
                input.blockedAddresses.find { blockedAddress ->
                    blockedAddress.hashedBlockedValue == expectedHash
                } shouldNotBe null
                input.emailAddressId.getOrNull() shouldBe mockEmailAddressId
            },
        )
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        verify(mockSealingService, times(addresses.size)).sealString(any(), any())
        verify(mockUserClient).getSubject()
    }

    @Test
    fun `blockEmailAddresses() should return failure when api returns failed status`() =
        runTest {
            addresses.size shouldNotBe 0

            mockApiClient.stub {
                onBlocking {
                    blockEmailAddressesMutation(
                        any(),
                    )
                } doAnswer {
                    DataFactory.blockEmailAddressMutationResponse(
                        status = BlockEmailAddressesBulkUpdateStatus.FAILED,
                    )
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.blockEmailAddresses(addresses)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.FAILURE

            verify(mockApiClient).blockEmailAddressesMutation(
                check { input ->
                    input.owner shouldBe "mockOwner"
                    input.blockedAddresses.size shouldBe addresses.size
                    val inputAction = input.blockedAddresses.first().action.getOrNull()
                    inputAction.toString() shouldBe BlockedEmailAddressAction.DROP.toString()
                },
            )
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService, times(2)).sealString(any(), any())
            verify(mockUserClient).getSubject()
        }

    @Test
    fun `blockEmailAddresses() should return proper lists on partial`() = runTest {
        addresses.size shouldNotBe 0

        val expectedHashedValues = addresses.map {
            StringHasher.hashString("$owner|${EmailAddressParser.normalize(it)}")
        }

        mockApiClient.stub {
            onBlocking {
                blockEmailAddressesMutation(
                    any(),
                )
            } doAnswer {
                DataFactory.blockEmailAddressMutationResponse(
                    status = BlockEmailAddressesBulkUpdateStatus.PARTIAL,
                    failedAddresses = listOf(expectedHashedValues[0]),
                    successAddresses = listOf(expectedHashedValues[1]),
                )
            }
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.blockEmailAddresses(addresses)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldNotBe null
        result.status shouldBe BatchOperationStatus.PARTIAL
        result.failureValues?.shouldContain(addresses[0])
        result.successValues?.shouldContain(addresses[1])

        verify(mockApiClient).blockEmailAddressesMutation(
            check { input ->
                input.owner shouldBe "mockOwner"
                input.blockedAddresses.size shouldBe addresses.size
                val inputAction = input.blockedAddresses.first().action.getOrNull()
                inputAction.toString() shouldBe BlockedEmailAddressAction.DROP.toString()
            },
        )
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        verify(mockSealingService, times(2)).sealString(any(), any())
        verify(mockUserClient).getSubject()
    }

    @Test
    fun `blockEmailAddresses() should throw an error if response contains errors`() =
        runTest {
            addresses.size shouldNotBe 0

            val error = GraphQLResponse.Error(
                "mock",
                null,
                null,
                mapOf("errorType" to "SystemError"),
            )
            mockApiClient.stub {
                onBlocking {
                    blockEmailAddressesMutation(
                        any(),
                    )
                } doAnswer {
                    GraphQLResponse(null, listOf(error))
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailBlocklistException.FailedException> {
                    client.blockEmailAddresses(addresses)
                }
            }
            deferredResult.start()
            val result = deferredResult.await()
            result shouldNotBe null

            verify(mockApiClient).blockEmailAddressesMutation(
                check { input ->
                    input.owner shouldBe owner
                    input.blockedAddresses.size shouldBe addresses.size
                    val inputAction = input.blockedAddresses.first().action.getOrNull()
                    inputAction.toString() shouldBe BlockedEmailAddressAction.DROP.toString()
                },
            )
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSealingService, times(2)).sealString(any(), any())
            verify(mockUserClient).getSubject()
        }
}
