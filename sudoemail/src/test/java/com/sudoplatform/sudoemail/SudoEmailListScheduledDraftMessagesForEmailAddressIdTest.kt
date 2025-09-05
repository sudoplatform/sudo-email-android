/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.apollographql.apollo.api.Optional
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.Owner
import com.sudoplatform.sudoemail.types.ScheduledDraftMessage
import com.sudoplatform.sudoemail.types.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.types.inputs.ListScheduledDraftMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.NotEqualStateFilter
import com.sudoplatform.sudoemail.types.inputs.ScheduledDraftMessageFilterInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Duration
import java.util.Date
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageFilterInput as ScheduledDraftMessageFilterGql
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageState as ScheduledDraftMessageStateGql
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageStateFilterInput as ScheduledDraftMessageStateFilterGql

/**
 * Test the correct operation of [SudoEmailClient.listScheduledDraftMessagesForEmailAddressId] using mocks
 * and spies.
 */
class SudoEmailListScheduledDraftMessagesForEmailAddressIdTest : BaseTests() {
    private val dummyDraftId = "dummyId"
    private val dummyEmailAddressId = "dummyEmailAddressId"
    private val sendAt = Date(Date().time + Duration.ofDays(1).toMillis())
    private val prefix = "dummyPrefix"

    private val expectedScheduledDraftMessageResult =
        ScheduledDraftMessage(
            id = dummyDraftId,
            emailAddressId = dummyEmailAddressId,
            state = ScheduledDraftMessageState.SCHEDULED,
            sendAt = sendAt,
            owner = "ownerId",
            owners = listOf(Owner("ownerId", "issuer")),
            updatedAt = Date(1),
            createdAt = Date(1),
        )

    private val input by before {
        ListScheduledDraftMessagesForEmailAddressIdInput(
            emailAddressId = dummyEmailAddressId,
        )
    }

    private val queryResponse by before {
        DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(
            listOf(
                DataFactory.getScheduledDraftMessage(
                    draftMessageKey = "$prefix/$dummyDraftId",
                    sendAtEpochMs = sendAt.time.toDouble(),
                    emailAddressId = dummyEmailAddressId,
                ),
            ),
        )
    }

    private val queryResponseWithNextToken by before {
        DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(
            listOf(
                DataFactory.getScheduledDraftMessage(
                    draftMessageKey = "$prefix/$dummyDraftId",
                    sendAtEpochMs = sendAt.time.toDouble(),
                    emailAddressId = dummyEmailAddressId,
                ),
            ),
            "dummyNextToken",
        )
    }

    private val queryResponseWithEmptyList by before {
        DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(
            emptyList(),
        )
    }

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockApiClient by before {
        mock<ApiClient>().stub {
            onBlocking {
                listScheduledDraftMessagesForEmailAddressIdQuery(
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
        DefaultServiceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger,
        )
    }

    private val mockS3Client by before {
        mock<S3Client>()
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        DefaultSealingService(
            mockServiceKeyManager,
            mockLogger,
        )
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
            mockApiClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should throw an error if graphQL mutation fails`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    listScheduledDraftMessagesForEmailAddressIdQuery(
                        any(),
                    )
                }.thenThrow(UnknownError("ERROR"))
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                        client.listScheduledDraftMessagesForEmailAddressId(input)
                    }
                }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(
                check { queryInput ->
                    queryInput.emailAddressId shouldBe dummyEmailAddressId
                    queryInput.nextToken shouldBe Optional.absent()
                    queryInput.limit shouldBe Optional.absent()
                    queryInput.filter shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should return results when no error present`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listScheduledDraftMessagesForEmailAddressId(
                        input,
                    )
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.nextToken shouldBe null
            result.items.size shouldBe 1
            result.items[0] shouldBe expectedScheduledDraftMessageResult

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(
                check { queryInput ->
                    queryInput.emailAddressId shouldBe dummyEmailAddressId
                    queryInput.nextToken shouldBe Optional.absent()
                    queryInput.limit shouldBe Optional.absent()
                    queryInput.filter shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId should handle empty response properly`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    listScheduledDraftMessagesForEmailAddressIdQuery(
                        any(),
                    )
                } doAnswer {
                    queryResponseWithEmptyList
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listScheduledDraftMessagesForEmailAddressId(
                        input,
                    )
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.nextToken shouldBe null
            result.items.size shouldBe 0

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(
                check { queryInput ->
                    queryInput.emailAddressId shouldBe dummyEmailAddressId
                    queryInput.nextToken shouldBe Optional.absent()
                    queryInput.limit shouldBe Optional.absent()
                    queryInput.filter shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId should handle pagination properly`() =
        runTest {
            mockApiClient.stub {
                onBlocking {
                    listScheduledDraftMessagesForEmailAddressIdQuery(
                        any(),
                    )
                } doAnswer {
                    queryResponseWithNextToken
                }
            }

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listScheduledDraftMessagesForEmailAddressId(
                        ListScheduledDraftMessagesForEmailAddressIdInput(
                            emailAddressId = dummyEmailAddressId,
                            limit = 1,
                            nextToken = "dummy",
                        ),
                    )
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.nextToken shouldBe "dummyNextToken"
            result.items.size shouldBe 1
            result.items[0] shouldBe expectedScheduledDraftMessageResult

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(
                check { queryInput ->
                    queryInput.emailAddressId shouldBe dummyEmailAddressId
                    queryInput.nextToken shouldBe Optional.present("dummy")
                    queryInput.limit shouldBe Optional.present(1)
                    queryInput.filter shouldBe Optional.absent()
                },
            )
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should handle filter arguments properly`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.listScheduledDraftMessagesForEmailAddressId(
                        ListScheduledDraftMessagesForEmailAddressIdInput(
                            emailAddressId = dummyEmailAddressId,
                            filter =
                                ScheduledDraftMessageFilterInput(
                                    state =
                                        NotEqualStateFilter(
                                            notEqual = ScheduledDraftMessageState.CANCELLED,
                                        ),
                                ),
                        ),
                    )
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.nextToken shouldBe null
            result.items.size shouldBe 1
            result.items[0] shouldBe expectedScheduledDraftMessageResult

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(
                check { queryInput ->
                    queryInput.emailAddressId shouldBe dummyEmailAddressId
                    queryInput.nextToken shouldBe Optional.absent()
                    queryInput.limit shouldBe Optional.absent()
                    queryInput.filter shouldBe
                        Optional.present(
                            ScheduledDraftMessageFilterGql(
                                state =
                                    Optional.present(
                                        ScheduledDraftMessageStateFilterGql(
                                            ne = Optional.present(ScheduledDraftMessageStateGql.CANCELLED),
                                        ),
                                    ),
                            ),
                        )
                },
            )
        }
}
