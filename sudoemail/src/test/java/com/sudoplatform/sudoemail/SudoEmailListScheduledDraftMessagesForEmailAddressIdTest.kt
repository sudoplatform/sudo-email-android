/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage.ListScheduledDraftMessagesForEmailAddressIdUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.ListOutput
import com.sudoplatform.sudoemail.types.Owner
import com.sudoplatform.sudoemail.types.ScheduledDraftMessage
import com.sudoplatform.sudoemail.types.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.types.inputs.EqualStateFilter
import com.sudoplatform.sudoemail.types.inputs.ListScheduledDraftMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.types.inputs.ScheduledDraftMessageFilterInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Duration
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.listScheduledDraftMessagesForEmailAddressId] using mocks
 * and spies.
 */
class SudoEmailListScheduledDraftMessagesForEmailAddressIdTest : BaseTests() {
    private val dummyDraftId1 = "dummyId1"
    private val dummyDraftId2 = "dummyId2"
    private val dummyEmailAddressId = "dummyEmailAddressId"
    private val sendAt = Date(Date().time + Duration.ofDays(1).toMillis())
    private val prefix = "dummyPrefix"
    private val nextToken = "dummyNextToken"

    private val input by before {
        ListScheduledDraftMessagesForEmailAddressIdInput(
            emailAddressId = dummyEmailAddressId,
        )
    }

    private val mockScheduledDraftMessage1 by before {
        ScheduledDraftMessage(
            id = dummyDraftId1,
            emailAddressId = dummyEmailAddressId,
            state = ScheduledDraftMessageState.SCHEDULED,
            sendAt = sendAt,
            owner = mockOwner,
            owners = listOf(Owner(mockOwner, "issuer")),
            updatedAt = Date(1),
            createdAt = Date(1),
        )
    }

    private val mockScheduledDraftMessage2 by before {
        ScheduledDraftMessage(
            id = dummyDraftId2,
            emailAddressId = dummyEmailAddressId,
            state = ScheduledDraftMessageState.SCHEDULED,
            sendAt = sendAt,
            owner = mockOwner,
            owners = listOf(Owner(mockOwner, "issuer")),
            updatedAt = Date(1),
            createdAt = Date(1),
        )
    }

    private val listOutputWithOneItem by before {
        ListOutput(
            items = listOf(mockScheduledDraftMessage1),
            nextToken = null,
        )
    }

    private val listOutputWithTwoItems by before {
        ListOutput(
            items =
                listOf(
                    mockScheduledDraftMessage1,
                    mockScheduledDraftMessage2,
                ),
            nextToken = null,
        )
    }

    private val listOutputWithNoItems by before {
        ListOutput<ScheduledDraftMessage>(
            items = emptyList(),
            nextToken = null,
        )
    }

    private val listOutputWithNextToken by before {
        ListOutput(
            items =
                listOf(
                    mockScheduledDraftMessage1,
                    mockScheduledDraftMessage2,
                ),
            nextToken = nextToken,
        )
    }

    private val mockUseCase by before {
        mock<ListScheduledDraftMessagesForEmailAddressIdUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn listOutputWithOneItem
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createListScheduledDraftMessagesForEmailAddressIdUseCase() } doReturn mockUseCase
        }
    }

    private val mockServiceKeyManager by before {
        DefaultServiceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger,
        )
    }

    private val client by before {
        DefaultSudoEmailClient(
            context = mockContext,
            serviceKeyManager = mockServiceKeyManager,
            apiClient = mockApiClient,
            sudoUserClient = mockUserClient,
            logger = mockLogger,
            region = "region",
            emailBucket = "identityBucket",
            transientBucket = "transientBucket",
            notificationHandler = null,
            s3TransientClient = mockS3Client,
            s3EmailClient = mockS3Client,
            useCaseFactory = mockUseCaseFactory,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockUseCaseFactory,
            mockUseCase,
        )
    }

    @org.junit.Test
    fun `listScheduledDraftMessagesForEmailAddressId() should return single scheduled message successfully`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute(any()) } doReturn listOutputWithOneItem
            }

            val result = client.listScheduledDraftMessagesForEmailAddressId(input)

            result.items.size shouldBe listOutputWithOneItem.items.size
            result.items[0].id shouldBe dummyDraftId1
            result.items[0].emailAddressId shouldBe dummyEmailAddressId
            result.items[0].state shouldBe ScheduledDraftMessageState.SCHEDULED
            result.nextToken shouldBe null

            verify(mockUseCaseFactory).createListScheduledDraftMessagesForEmailAddressIdUseCase()
            verify(mockUseCase).execute(
                check { useCaseInput ->
                    useCaseInput.emailAddressId shouldBe dummyEmailAddressId
                    useCaseInput.limit shouldBe null
                    useCaseInput.nextToken shouldBe null
                    useCaseInput.filter shouldBe null
                },
            )
        }

    @org.junit.Test
    fun `listScheduledDraftMessagesForEmailAddressId() should return multiple scheduled messages successfully`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute(any()) } doReturn listOutputWithTwoItems
            }

            val result = client.listScheduledDraftMessagesForEmailAddressId(input)

            result.items.size shouldBe listOutputWithTwoItems.items.size
            result.items[0].id shouldBe dummyDraftId1
            result.items[1].id shouldBe dummyDraftId2
            result.nextToken shouldBe null

            verify(mockUseCaseFactory).createListScheduledDraftMessagesForEmailAddressIdUseCase()
            verify(mockUseCase).execute(any())
        }

    @org.junit.Test
    fun `listScheduledDraftMessagesForEmailAddressId() should return empty list when no messages exist`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute(any()) } doReturn listOutputWithNoItems
            }

            val result = client.listScheduledDraftMessagesForEmailAddressId(input)

            result.items shouldBe emptyList()
            result.nextToken shouldBe null

            verify(mockUseCaseFactory).createListScheduledDraftMessagesForEmailAddressIdUseCase()
            verify(mockUseCase).execute(any())
        }

    @org.junit.Test
    fun `listScheduledDraftMessagesForEmailAddressId() should handle pagination with nextToken`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute(any()) } doReturn listOutputWithNextToken
            }

            val result = client.listScheduledDraftMessagesForEmailAddressId(input)

            result.items.size shouldBe listOutputWithNextToken.items.size
            result.nextToken shouldBe nextToken

            verify(mockUseCaseFactory).createListScheduledDraftMessagesForEmailAddressIdUseCase()
            verify(mockUseCase).execute(any())
        }

    @org.junit.Test
    fun `listScheduledDraftMessagesForEmailAddressId() should pass limit parameter to use case`() =
        runTest {
            val inputWithLimit =
                ListScheduledDraftMessagesForEmailAddressIdInput(
                    emailAddressId = dummyEmailAddressId,
                    limit = 10,
                )

            client.listScheduledDraftMessagesForEmailAddressId(inputWithLimit)

            verify(mockUseCaseFactory).createListScheduledDraftMessagesForEmailAddressIdUseCase()
            verify(mockUseCase).execute(
                check {
                    it.emailAddressId shouldBe dummyEmailAddressId
                    it.limit shouldBe 10
                },
            )
        }

    @org.junit.Test
    fun `listScheduledDraftMessagesForEmailAddressId() should pass nextToken parameter to use case`() =
        runTest {
            val previousToken = "previousToken"
            val inputWithToken =
                ListScheduledDraftMessagesForEmailAddressIdInput(
                    emailAddressId = dummyEmailAddressId,
                    nextToken = previousToken,
                )

            client.listScheduledDraftMessagesForEmailAddressId(inputWithToken)

            verify(mockUseCaseFactory).createListScheduledDraftMessagesForEmailAddressIdUseCase()
            verify(mockUseCase).execute(
                check {
                    it.emailAddressId shouldBe dummyEmailAddressId
                    it.nextToken shouldBe previousToken
                },
            )
        }

    @org.junit.Test
    fun `listScheduledDraftMessagesForEmailAddressId() should pass filter parameter to use case`() =
        runTest {
            val filter =
                ScheduledDraftMessageFilterInput(
                    state =
                        EqualStateFilter(
                            ScheduledDraftMessageState.SCHEDULED,
                        ),
                )
            val inputWithFilter =
                ListScheduledDraftMessagesForEmailAddressIdInput(
                    emailAddressId = dummyEmailAddressId,
                    filter = filter,
                )

            client.listScheduledDraftMessagesForEmailAddressId(inputWithFilter)

            verify(mockUseCaseFactory).createListScheduledDraftMessagesForEmailAddressIdUseCase()
            verify(mockUseCase).execute(
                check {
                    it.emailAddressId shouldBe dummyEmailAddressId
                    it.filter shouldBe filter
                },
            )
        }

    @org.junit.Test
    fun `listScheduledDraftMessagesForEmailAddressId() should pass all parameters together`() =
        runTest {
            val filter =
                ScheduledDraftMessageFilterInput(
                    state =
                        EqualStateFilter(
                            ScheduledDraftMessageState.FAILED,
                        ),
                )
            val fullInput =
                ListScheduledDraftMessagesForEmailAddressIdInput(
                    emailAddressId = dummyEmailAddressId,
                    limit = 25,
                    nextToken = "token123",
                    filter = filter,
                )

            client.listScheduledDraftMessagesForEmailAddressId(fullInput)

            verify(mockUseCaseFactory).createListScheduledDraftMessagesForEmailAddressIdUseCase()
            verify(mockUseCase).execute(
                check {
                    it.emailAddressId shouldBe dummyEmailAddressId
                    it.limit shouldBe 25
                    it.nextToken shouldBe "token123"
                    it.filter shouldBe filter
                },
            )
        }

    @org.junit.Test
    fun `listScheduledDraftMessagesForEmailAddressId() should propagate exceptions from use case`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute(any()) } doThrow SudoEmailClient.EmailMessageException.FailedException("Failed")
            }

            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.listScheduledDraftMessagesForEmailAddressId(input)
            }

            verify(mockUseCaseFactory).createListScheduledDraftMessagesForEmailAddressIdUseCase()
            verify(mockUseCase).execute(any())
        }

    @org.junit.Test
    fun `listScheduledDraftMessagesForEmailAddressId() should handle different email address IDs`() =
        runTest {
            val customEmailAddressId = "customEmailAddress123"
            val customInput =
                ListScheduledDraftMessagesForEmailAddressIdInput(
                    emailAddressId = customEmailAddressId,
                )

            client.listScheduledDraftMessagesForEmailAddressId(customInput)

            verify(mockUseCaseFactory).createListScheduledDraftMessagesForEmailAddressIdUseCase()
            verify(mockUseCase).execute(
                check {
                    it.emailAddressId shouldBe customEmailAddressId
                },
            )
        }

    @org.junit.Test
    fun `listScheduledDraftMessagesForEmailAddressId() should preserve message state in results`() =
        runTest {
            val sentMessage =
                ScheduledDraftMessage(
                    id = "sentId",
                    emailAddressId = dummyEmailAddressId,
                    state = ScheduledDraftMessageState.SENT,
                    sendAt = sendAt,
                    owner = mockOwner,
                    owners = listOf(Owner(mockOwner, "issuer")),
                    updatedAt = Date(1),
                    createdAt = Date(1),
                )

            mockUseCase.stub {
                onBlocking { execute(any()) } doReturn ListOutput(listOf(sentMessage), null)
            }

            val result = client.listScheduledDraftMessagesForEmailAddressId(input)

            result.items[0].state shouldBe ScheduledDraftMessageState.SENT

            verify(mockUseCaseFactory).createListScheduledDraftMessagesForEmailAddressIdUseCase()
            verify(mockUseCase).execute(any())
        }

    @org.junit.Test
    fun `listScheduledDraftMessagesForEmailAddressId() should preserve all message fields in results`() =
        runTest {
            mockUseCase.stub {
                onBlocking { execute(any()) } doReturn listOutputWithOneItem
            }

            val result = client.listScheduledDraftMessagesForEmailAddressId(input)

            val message = result.items[0]
            message.id shouldBe dummyDraftId1
            message.emailAddressId shouldBe dummyEmailAddressId
            message.state shouldBe ScheduledDraftMessageState.SCHEDULED
            message.sendAt shouldBe sendAt
            message.owner shouldBe mockOwner
            message.owners.size shouldBe 1
            message.createdAt shouldBe Date(1)
            message.updatedAt shouldBe Date(1)

            verify(mockUseCaseFactory).createListScheduledDraftMessagesForEmailAddressIdUseCase()
            verify(mockUseCase).execute(any())
        }
}
