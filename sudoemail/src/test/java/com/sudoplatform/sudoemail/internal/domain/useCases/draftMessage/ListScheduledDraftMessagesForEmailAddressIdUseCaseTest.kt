/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.EqualStateFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ListScheduledDraftMessagesOutput
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduledDraftMessageEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduledDraftMessageFilterInputEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduledDraftMessageStateEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.types.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.types.inputs.EqualStateFilter
import com.sudoplatform.sudoemail.types.inputs.ScheduledDraftMessageFilterInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the correct operation of [ListScheduledDraftMessagesForEmailAddressIdUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class ListScheduledDraftMessagesForEmailAddressIdUseCaseTest : BaseTests() {
    private val emailAddressId = mockEmailAddressId
    private val draftId1 = "draftId1"
    private val draftId2 = "draftId2"
    private val nextTokenValue = "nextToken123"

    private val mockEmailAddress by before {
        EntityDataFactory.getSealedEmailAddressEntity(id = emailAddressId)
    }

    private val scheduledMessage1 by before {
        ScheduledDraftMessageEntity(
            id = draftId1,
            emailAddressId = emailAddressId,
            owner = mockOwner,
            owners = emptyList(),
            sendAt = Date(System.currentTimeMillis() + 86400000),
            state = ScheduledDraftMessageStateEntity.SCHEDULED,
            createdAt = Date(),
            updatedAt = Date(),
        )
    }

    private val scheduledMessage2 by before {
        ScheduledDraftMessageEntity(
            id = draftId2,
            emailAddressId = emailAddressId,
            owner = mockOwner,
            owners = emptyList(),
            sendAt = Date(System.currentTimeMillis() + 172800000),
            state = ScheduledDraftMessageStateEntity.SCHEDULED,
            createdAt = Date(),
            updatedAt = Date(),
        )
    }

    private val mockDraftEmailMessageService by before {
        mock<DraftEmailMessageService>().stub {
            onBlocking { listScheduledDraftMessagesForEmailAddressId(any()) } doReturn
                ListScheduledDraftMessagesOutput(
                    items = listOf(scheduledMessage1, scheduledMessage2),
                    nextToken = null,
                )
        }
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking { get(any()) } doReturn mockEmailAddress
        }
    }

    private val useCase by before {
        ListScheduledDraftMessagesForEmailAddressIdUseCase(
            draftEmailMessageService = mockDraftEmailMessageService,
            emailAddressService = mockEmailAddressService,
            logger = mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockDraftEmailMessageService,
            mockEmailAddressService,
        )
    }

    @Test
    fun `execute() should return list of scheduled draft messages successfully`() =
        runTest {
            val input =
                ListScheduledDraftMessagesForEmailAddressIdUseCaseInput(
                    emailAddressId = emailAddressId,
                    limit = null,
                    nextToken = null,
                    filter = null,
                )

            val result = useCase.execute(input)

            result.items.size shouldBe 2
            result.items[0].id shouldBe draftId1
            result.items[0].emailAddressId shouldBe emailAddressId
            result.items[1].id shouldBe draftId2
            result.items[1].emailAddressId shouldBe emailAddressId
            result.nextToken shouldBe null

            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe emailAddressId
                },
            )
            verify(mockDraftEmailMessageService).listScheduledDraftMessagesForEmailAddressId(
                check {
                    it.emailAddressId shouldBe emailAddressId
                    it.limit shouldBe null
                    it.nextToken shouldBe null
                    it.filter shouldBe null
                },
            )
        }

    @Test
    fun `execute() should throw EmailAddressNotFoundException when email address does not exist`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { get(any()) } doReturn null
            }

            val input =
                ListScheduledDraftMessagesForEmailAddressIdUseCaseInput(
                    emailAddressId = emailAddressId,
                    limit = null,
                    nextToken = null,
                    filter = null,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.EMAIL_ADDRESS_NOT_FOUND_MSG

            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `execute() should return empty list when no scheduled messages exist`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressId(any()) } doReturn
                    ListScheduledDraftMessagesOutput(
                        items = emptyList(),
                        nextToken = null,
                    )
            }

            val input =
                ListScheduledDraftMessagesForEmailAddressIdUseCaseInput(
                    emailAddressId = emailAddressId,
                    limit = null,
                    nextToken = null,
                    filter = null,
                )

            val result = useCase.execute(input)

            result.items shouldBe emptyList()
            result.nextToken shouldBe null

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listScheduledDraftMessagesForEmailAddressId(any())
        }

    @Test
    fun `execute() should handle pagination with limit and nextToken`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressId(any()) } doReturn
                    ListScheduledDraftMessagesOutput(
                        items = listOf(scheduledMessage1),
                        nextToken = nextTokenValue,
                    )
            }

            val input =
                ListScheduledDraftMessagesForEmailAddressIdUseCaseInput(
                    emailAddressId = emailAddressId,
                    limit = 10,
                    nextToken = "previousToken",
                    filter = null,
                )

            val result = useCase.execute(input)

            result.items.size shouldBe 1
            result.nextToken shouldBe nextTokenValue

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listScheduledDraftMessagesForEmailAddressId(
                check {
                    it.emailAddressId shouldBe emailAddressId
                    it.limit shouldBe 10
                    it.nextToken shouldBe "previousToken"
                },
            )
        }

    @Test
    fun `execute() should pass filter to service when provided`() =
        runTest {
            val filter =
                ScheduledDraftMessageFilterInput(
                    state = EqualStateFilter(ScheduledDraftMessageState.SCHEDULED),
                )

            val input =
                ListScheduledDraftMessagesForEmailAddressIdUseCaseInput(
                    emailAddressId = emailAddressId,
                    limit = null,
                    nextToken = null,
                    filter = filter,
                )

            useCase.execute(input)

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listScheduledDraftMessagesForEmailAddressId(
                check {
                    it.emailAddressId shouldBe emailAddressId
                    it.filter shouldBe
                        ScheduledDraftMessageFilterInputEntity(
                            EqualStateFilterEntity(
                                ScheduledDraftMessageStateEntity.SCHEDULED,
                            ),
                        )
                },
            )
        }

    @Test
    fun `execute() should not pass filter when null`() =
        runTest {
            val input =
                ListScheduledDraftMessagesForEmailAddressIdUseCaseInput(
                    emailAddressId = emailAddressId,
                    limit = null,
                    nextToken = null,
                    filter = null,
                )

            useCase.execute(input)

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listScheduledDraftMessagesForEmailAddressId(
                check {
                    it.filter shouldBe null
                },
            )
        }

    @Test
    fun `execute() should verify email address exists before listing`() =
        runTest {
            val input =
                ListScheduledDraftMessagesForEmailAddressIdUseCaseInput(
                    emailAddressId = emailAddressId,
                    limit = null,
                    nextToken = null,
                    filter = null,
                )

            useCase.execute(input)

            // Verify email address is checked first
            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe emailAddressId
                },
            )
            // Then listing happens
            verify(mockDraftEmailMessageService).listScheduledDraftMessagesForEmailAddressId(any())
        }

    @Test
    fun `execute() should propagate EmailMessageException from service`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressId(any()) } doThrow
                    SudoEmailClient.EmailMessageException.FailedException("Failed")
            }

            val input =
                ListScheduledDraftMessagesForEmailAddressIdUseCaseInput(
                    emailAddressId = emailAddressId,
                    limit = null,
                    nextToken = null,
                    filter = null,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listScheduledDraftMessagesForEmailAddressId(any())
        }

    @Test
    fun `execute() should interpret generic exceptions as EmailMessageException`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressId(any()) } doThrow RuntimeException("Unexpected error")
            }

            val input =
                ListScheduledDraftMessagesForEmailAddressIdUseCaseInput(
                    emailAddressId = emailAddressId,
                    limit = null,
                    nextToken = null,
                    filter = null,
                )

            shouldThrow<SudoEmailClient.EmailMessageException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listScheduledDraftMessagesForEmailAddressId(any())
        }

    @Test
    fun `execute() should handle different email address IDs correctly`() =
        runTest {
            val customEmailAddressId = "customEmailAddress789"
            val customEmailAddress = EntityDataFactory.getSealedEmailAddressEntity(id = customEmailAddressId)

            mockEmailAddressService.stub {
                onBlocking { get(check { it.id == customEmailAddressId }) } doReturn customEmailAddress
            }

            val input =
                ListScheduledDraftMessagesForEmailAddressIdUseCaseInput(
                    emailAddressId = customEmailAddressId,
                    limit = null,
                    nextToken = null,
                    filter = null,
                )

            useCase.execute(input)

            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe customEmailAddressId
                },
            )
            verify(mockDraftEmailMessageService).listScheduledDraftMessagesForEmailAddressId(
                check {
                    it.emailAddressId shouldBe customEmailAddressId
                },
            )
        }

    @Test
    fun `execute() should transform entity results to API types`() =
        runTest {
            val input =
                ListScheduledDraftMessagesForEmailAddressIdUseCaseInput(
                    emailAddressId = emailAddressId,
                    limit = null,
                    nextToken = null,
                    filter = null,
                )

            val result = useCase.execute(input)

            // Verify entity was transformed to API type
            result.items[0].id shouldBe draftId1
            result.items[0].emailAddressId shouldBe emailAddressId
            result.items[1].id shouldBe draftId2

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listScheduledDraftMessagesForEmailAddressId(any())
        }

    @Test
    fun `execute() should pass all parameters correctly to service`() =
        runTest {
            val customLimit = 25
            val customToken = "customToken"
            val filter =
                ScheduledDraftMessageFilterInput(
                    state = EqualStateFilter(ScheduledDraftMessageState.FAILED),
                )

            val input =
                ListScheduledDraftMessagesForEmailAddressIdUseCaseInput(
                    emailAddressId = emailAddressId,
                    limit = customLimit,
                    nextToken = customToken,
                    filter = filter,
                )

            useCase.execute(input)

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listScheduledDraftMessagesForEmailAddressId(
                check {
                    it.emailAddressId shouldBe emailAddressId
                    it.limit shouldBe customLimit
                    it.nextToken shouldBe customToken
                    it.filter shouldBe
                        ScheduledDraftMessageFilterInputEntity(
                            EqualStateFilterEntity(
                                ScheduledDraftMessageStateEntity.FAILED,
                            ),
                        )
                },
            )
        }

    @Test
    fun `execute() should preserve nextToken in response`() =
        runTest {
            val customNextToken = "customNextToken456"

            mockDraftEmailMessageService.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressId(any()) } doReturn
                    ListScheduledDraftMessagesOutput(
                        items = listOf(scheduledMessage1),
                        nextToken = customNextToken,
                    )
            }

            val input =
                ListScheduledDraftMessagesForEmailAddressIdUseCaseInput(
                    emailAddressId = emailAddressId,
                    limit = null,
                    nextToken = null,
                    filter = null,
                )

            val result = useCase.execute(input)

            result.nextToken shouldBe customNextToken

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listScheduledDraftMessagesForEmailAddressId(any())
        }
}
