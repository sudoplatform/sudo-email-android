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
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageMetadataEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ListDraftEmailMessageMetadataOutput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the correct operation of [ListDraftEmailMessageMetadataForEmailAddressIdUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class ListDraftEmailMessageMetadataForEmailAddressIdUseCaseTest : BaseTests() {
    private val emailAddressId = mockEmailAddressId
    private val draftId1 = "draftId1"
    private val draftId2 = "draftId2"
    private val draftId3 = "draftId3"
    private val date1 = Date(1000L)
    private val date2 = Date(2000L)
    private val date3 = Date(3000L)

    private val mockEmailAddress by before {
        EntityDataFactory.getSealedEmailAddressEntity(
            id = emailAddressId,
        )
    }

    private val draftMetadataList by before {
        listOf(
            DraftEmailMessageMetadataEntity(
                id = draftId1,
                emailAddressId = emailAddressId,
                updatedAt = date1,
            ),
            DraftEmailMessageMetadataEntity(
                id = draftId2,
                emailAddressId = emailAddressId,
                updatedAt = date2,
            ),
            DraftEmailMessageMetadataEntity(
                id = draftId3,
                emailAddressId = emailAddressId,
                updatedAt = date3,
            ),
        )
    }

    private val mockDraftEmailMessageService by before {
        mock<DraftEmailMessageService>().stub {
            onBlocking { listMetadataForEmailAddressId(any(), anyOrNull(), anyOrNull()) } doReturn
                ListDraftEmailMessageMetadataOutput(draftMetadataList, null)
        }
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking { get(any()) } doReturn mockEmailAddress
        }
    }

    private val useCase by before {
        ListDraftEmailMessageMetadataForEmailAddressIdUseCase(
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
    fun `execute() should return list of draft metadata successfully`() =
        runTest {
            val result = useCase.execute(emailAddressId).items

            result shouldBe draftMetadataList
            result.size shouldBe 3
            result[0].id shouldBe draftId1
            result[0].emailAddressId shouldBe emailAddressId
            result[0].updatedAt shouldBe date1
            result[1].id shouldBe draftId2
            result[1].emailAddressId shouldBe emailAddressId
            result[1].updatedAt shouldBe date2
            result[2].id shouldBe draftId3
            result[2].emailAddressId shouldBe emailAddressId
            result[2].updatedAt shouldBe date3

            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe emailAddressId
                },
            )
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId, null, null)
        }

    @Test
    fun `execute() should return empty list when no drafts exist`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { listMetadataForEmailAddressId(any(), anyOrNull(), anyOrNull()) } doReturn
                    ListDraftEmailMessageMetadataOutput(emptyList(), null)
            }

            val result = useCase.execute(emailAddressId).items

            result shouldBe emptyList()

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId, null, null)
        }

    @Test
    fun `execute() should throw EmailAddressNotFoundException when email address does not exist`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { get(any()) } doReturn null
            }

            val exception =
                shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                    useCase.execute(emailAddressId)
                }

            exception.message shouldBe StringConstants.EMAIL_ADDRESS_NOT_FOUND_MSG

            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `execute() should handle single draft metadata`() =
        runTest {
            val singleMetadata =
                listOf(
                    DraftEmailMessageMetadataEntity(
                        id = draftId1,
                        emailAddressId = emailAddressId,
                        updatedAt = date1,
                    ),
                )

            mockDraftEmailMessageService.stub {
                onBlocking { listMetadataForEmailAddressId(any(), anyOrNull(), anyOrNull()) } doReturn
                    ListDraftEmailMessageMetadataOutput(singleMetadata, null)
            }

            val result = useCase.execute(emailAddressId).items

            result.size shouldBe 1
            result[0].id shouldBe draftId1
            result[0].emailAddressId shouldBe emailAddressId
            result[0].updatedAt shouldBe date1

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId, null, null)
        }

    @Test
    fun `execute() should verify email address exists before listing drafts`() =
        runTest {
            useCase.execute(emailAddressId)

            // Verify email address is checked first
            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe emailAddressId
                },
            )
            // Then drafts are listed
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId, null, null)
        }

    @Test
    fun `execute() should pass correct email address ID to service`() =
        runTest {
            val testEmailAddressId = "testEmailAddress123"
            mockEmailAddressService.stub {
                onBlocking { get(any()) } doReturn
                    EntityDataFactory.getSealedEmailAddressEntity(
                        id = testEmailAddressId,
                    )
            }

            useCase.execute(testEmailAddressId)

            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe testEmailAddressId
                },
            )
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(testEmailAddressId, null, null)
        }

    @Test
    fun `execute() should preserve order of draft metadata from service`() =
        runTest {
            // Create metadata in a specific order
            val orderedMetadata =
                listOf(
                    DraftEmailMessageMetadataEntity(
                        id = "draft3",
                        emailAddressId = emailAddressId,
                        updatedAt = date3,
                    ),
                    DraftEmailMessageMetadataEntity(
                        id = "draft1",
                        emailAddressId = emailAddressId,
                        updatedAt = date1,
                    ),
                    DraftEmailMessageMetadataEntity(
                        id = "draft2",
                        emailAddressId = emailAddressId,
                        updatedAt = date2,
                    ),
                )

            mockDraftEmailMessageService.stub {
                onBlocking { listMetadataForEmailAddressId(any(), anyOrNull(), anyOrNull()) } doReturn
                    ListDraftEmailMessageMetadataOutput(orderedMetadata, null)
            }

            val result = useCase.execute(emailAddressId).items

            result.size shouldBe 3
            // Order should be preserved from service
            result[0].id shouldBe "draft3"
            result[1].id shouldBe "draft1"
            result[2].id shouldBe "draft2"

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId, null, null)
        }

    @Test
    fun `execute() should handle large number of drafts with limit`() =
        runTest {
            val largeDraftList =
                (1..100).map { index ->
                    DraftEmailMessageMetadataEntity(
                        id = "draft$index",
                        emailAddressId = emailAddressId,
                        updatedAt = Date(index * 1000L),
                    )
                }

            mockDraftEmailMessageService.stub {
                onBlocking { listMetadataForEmailAddressId(any(), anyOrNull(), anyOrNull()) } doReturn
                    ListDraftEmailMessageMetadataOutput(largeDraftList, null)
            }

            val result = useCase.execute(emailAddressId, 1000).items

            result.size shouldBe 100
            result[0].id shouldBe "draft1"
            result[99].id shouldBe "draft100"

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId, 1000, null)
        }

    @Test
    fun `execute() should pass limit parameter to service`() =
        runTest {
            val limit = 5

            mockDraftEmailMessageService.stub {
                onBlocking { listMetadataForEmailAddressId(emailAddressId, limit, null) } doReturn
                    ListDraftEmailMessageMetadataOutput(draftMetadataList, null)
            }

            val result = useCase.execute(emailAddressId, limit).items

            result shouldBe draftMetadataList

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(
                check { it shouldBe emailAddressId },
                check { it shouldBe limit },
                isNull(),
            )
        }

    @Test
    fun `execute() should pass nextToken parameter to service`() =
        runTest {
            val nextToken = "token123"

            val result = useCase.execute(emailAddressId, null, nextToken).items

            result shouldBe draftMetadataList

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(
                check { it shouldBe emailAddressId },
                isNull(),
                check { it shouldBe nextToken },
            )
        }

    @Test
    fun `execute() should pass both limit and nextToken parameters to service`() =
        runTest {
            val limit = 5
            val nextToken = "token456"

            val result = useCase.execute(emailAddressId, limit, nextToken).items

            result shouldBe draftMetadataList

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(
                check { it shouldBe emailAddressId },
                check { it shouldBe limit },
                check { it shouldBe nextToken },
            )
        }

    @Test
    fun `execute() should pass null for limit and nextToken when not provided`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { listMetadataForEmailAddressId(emailAddressId, null, null) } doReturn
                    ListDraftEmailMessageMetadataOutput(draftMetadataList, null)
            }

            val result = useCase.execute(emailAddressId).items

            result shouldBe draftMetadataList

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(
                check { it shouldBe emailAddressId },
                isNull(),
                isNull(),
            )
        }

    @Test
    fun `execute() should return nextToken from service`() =
        runTest {
            val expectedNextToken = "nextToken789"

            mockDraftEmailMessageService.stub {
                onBlocking { listMetadataForEmailAddressId(any(), anyOrNull(), anyOrNull()) } doReturn
                    ListDraftEmailMessageMetadataOutput(draftMetadataList, expectedNextToken)
            }

            val result = useCase.execute(emailAddressId)

            result.items shouldBe draftMetadataList
            result.nextToken shouldBe expectedNextToken

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId, null, null)
        }

    @Test
    fun `execute() should handle pagination with limit returning nextToken`() =
        runTest {
            val limit = 2
            val expectedNextToken = "paginationToken"
            val limitedResults = draftMetadataList.take(2)

            mockDraftEmailMessageService.stub {
                onBlocking { listMetadataForEmailAddressId(any(), anyOrNull(), anyOrNull()) } doReturn
                    ListDraftEmailMessageMetadataOutput(limitedResults, expectedNextToken)
            }

            val result = useCase.execute(emailAddressId, limit)

            result.items.size shouldBe 2
            result.nextToken shouldBe expectedNextToken

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId, limit, null)
        }

    @Test
    fun `execute() should handle pagination with nextToken for subsequent page`() =
        runTest {
            val limit = 2
            val nextToken = "existingToken"
            val subsequentResults = listOf(draftMetadataList[2])

            mockDraftEmailMessageService.stub {
                onBlocking { listMetadataForEmailAddressId(any(), anyOrNull(), anyOrNull()) } doReturn
                    ListDraftEmailMessageMetadataOutput(subsequentResults, null)
            }

            val result = useCase.execute(emailAddressId, limit, nextToken)

            result.items.size shouldBe 1
            result.items[0].id shouldBe draftId3
            result.nextToken shouldBe null

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId, limit, nextToken)
        }
}
