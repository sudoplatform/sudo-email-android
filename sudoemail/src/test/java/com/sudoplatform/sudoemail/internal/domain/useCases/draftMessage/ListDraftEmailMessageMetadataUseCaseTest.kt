/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageMetadataEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ListDraftEmailMessageMetadataOutput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ListEmailAddressesOutput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ListEmailAddressesRequest
import io.kotlintest.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the correct operation of [ListDraftEmailMessageMetadataUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class ListDraftEmailMessageMetadataUseCaseTest : BaseTests() {
    private val emailAddressId1 = "emailAddressId1"
    private val emailAddressId2 = "emailAddressId2"
    private val emailAddressId3 = "emailAddressId3"

    private val mockEmailAddress1 by before {
        EntityDataFactory.getSealedEmailAddressEntity(id = emailAddressId1)
    }

    private val mockEmailAddress2 by before {
        EntityDataFactory.getSealedEmailAddressEntity(id = emailAddressId2)
    }

    private val mockEmailAddress3 by before {
        EntityDataFactory.getSealedEmailAddressEntity(id = emailAddressId3)
    }

    private val draftMetadata1 by before {
        listOf(
            DraftEmailMessageMetadataEntity(
                id = "draft1-1",
                emailAddressId = emailAddressId1,
                updatedAt = Date(1000L),
            ),
            DraftEmailMessageMetadataEntity(
                id = "draft1-2",
                emailAddressId = emailAddressId1,
                updatedAt = Date(2000L),
            ),
        )
    }

    private val draftMetadata2 by before {
        listOf(
            DraftEmailMessageMetadataEntity(
                id = "draft2-1",
                emailAddressId = emailAddressId2,
                updatedAt = Date(3000L),
            ),
        )
    }

    private val draftMetadata3 by before {
        emptyList<DraftEmailMessageMetadataEntity>()
    }

    private val mockDraftEmailMessageService by before {
        mock<DraftEmailMessageService>().stub {
            onBlocking { listMetadataForEmailAddressId(emailAddressId1) } doReturn
                ListDraftEmailMessageMetadataOutput(draftMetadata1, nextToken = null)
            onBlocking { listMetadataForEmailAddressId(emailAddressId2) } doReturn
                ListDraftEmailMessageMetadataOutput(draftMetadata2, nextToken = null)
            onBlocking { listMetadataForEmailAddressId(emailAddressId3) } doReturn
                ListDraftEmailMessageMetadataOutput(draftMetadata3, nextToken = null)
        }
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking { list(any()) } doReturn
                ListEmailAddressesOutput(
                    items = listOf(mockEmailAddress1, mockEmailAddress2, mockEmailAddress3),
                    nextToken = null,
                )
        }
    }

    private val useCase by before {
        ListDraftEmailMessageMetadataUseCase(
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
    fun `execute() should return all draft metadata from all email addresses`() =
        runTest {
            val result = useCase.execute()

            result.size shouldBe 3
            result shouldBe draftMetadata1 + draftMetadata2 + draftMetadata3
            result[0].id shouldBe "draft1-1"
            result[0].emailAddressId shouldBe emailAddressId1
            result[1].id shouldBe "draft1-2"
            result[1].emailAddressId shouldBe emailAddressId1
            result[2].id shouldBe "draft2-1"
            result[2].emailAddressId shouldBe emailAddressId2

            verify(mockEmailAddressService).list(
                check {
                    it.nextToken shouldBe null
                    it.limit shouldBe 10
                },
            )
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId1)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId2)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId3)
        }

    @Test
    fun `execute() should return empty list when no email addresses exist`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailAddressesOutput(
                        items = emptyList(),
                        nextToken = null,
                    )
            }

            val result = useCase.execute()

            result shouldBe emptyList()

            verify(mockEmailAddressService).list(any())
        }

    @Test
    fun `execute() should return empty list when no drafts exist for any email address`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { listMetadataForEmailAddressId(any(), anyOrNull(), anyOrNull()) } doReturn
                    ListDraftEmailMessageMetadataOutput(emptyList(), null)
            }

            val result = useCase.execute()

            result shouldBe emptyList()

            verify(mockEmailAddressService).list(any())
            verify(mockDraftEmailMessageService, times(3)).listMetadataForEmailAddressId(any(), anyOrNull(), anyOrNull())
        }

    @Test
    fun `execute() should handle pagination of email addresses`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { list(ListEmailAddressesRequest(nextToken = null, limit = null)) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(mockEmailAddress1),
                        nextToken = "token1",
                    )
                onBlocking { list(ListEmailAddressesRequest(nextToken = "token1", limit = null)) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(mockEmailAddress2),
                        nextToken = "token2",
                    )
                onBlocking { list(ListEmailAddressesRequest(nextToken = "token2", limit = null)) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(mockEmailAddress3),
                        nextToken = null,
                    )
            }

            val result = useCase.execute()

            result.size shouldBe 3
            result shouldBe draftMetadata1 + draftMetadata2 + draftMetadata3

            verify(mockEmailAddressService, times(1)).list(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId1)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId2)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId3)
        }

    @Test
    fun `execute() should handle single email address`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(mockEmailAddress1),
                        nextToken = null,
                    )
            }

            val result = useCase.execute()

            result shouldBe draftMetadata1
            result.size shouldBe 2

            verify(mockEmailAddressService).list(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId1)
        }

    @Test
    fun `execute() should aggregate drafts from multiple pages of email addresses`() =
        runTest {
            val emailAddress4 = EntityDataFactory.getSealedEmailAddressEntity(id = "emailAddressId4")
            val emailAddress5 = EntityDataFactory.getSealedEmailAddressEntity(id = "emailAddressId5")

            val draftMetadata4 =
                listOf(
                    DraftEmailMessageMetadataEntity(
                        id = "draft4-1",
                        emailAddressId = "emailAddressId4",
                        updatedAt = Date(4000L),
                    ),
                )

            val draftMetadata5 =
                listOf(
                    DraftEmailMessageMetadataEntity(
                        id = "draft5-1",
                        emailAddressId = "emailAddressId5",
                        updatedAt = Date(5000L),
                    ),
                    DraftEmailMessageMetadataEntity(
                        id = "draft5-2",
                        emailAddressId = "emailAddressId5",
                        updatedAt = Date(6000L),
                    ),
                )

            mockEmailAddressService.stub {
                onBlocking { list(ListEmailAddressesRequest(nextToken = null, limit = 10)) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(mockEmailAddress1, mockEmailAddress2),
                        nextToken = "page2",
                    )
                onBlocking { list(ListEmailAddressesRequest(nextToken = "page2", limit = 10)) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(mockEmailAddress3, emailAddress4, emailAddress5),
                        nextToken = null,
                    )
            }

            mockDraftEmailMessageService.stub {
                onBlocking { listMetadataForEmailAddressId(emailAddressId1) } doReturn
                    ListDraftEmailMessageMetadataOutput(draftMetadata1, nextToken = null)
                onBlocking { listMetadataForEmailAddressId(emailAddressId2) } doReturn
                    ListDraftEmailMessageMetadataOutput(draftMetadata2, nextToken = null)
                onBlocking { listMetadataForEmailAddressId(emailAddressId3) } doReturn
                    ListDraftEmailMessageMetadataOutput(draftMetadata3, nextToken = null)
                onBlocking { listMetadataForEmailAddressId("emailAddressId4") } doReturn
                    ListDraftEmailMessageMetadataOutput(draftMetadata4, nextToken = null)
                onBlocking { listMetadataForEmailAddressId("emailAddressId5") } doReturn
                    ListDraftEmailMessageMetadataOutput(draftMetadata5, nextToken = null)
            }

            val result = useCase.execute()

            result shouldBe draftMetadata1 + draftMetadata2 + draftMetadata3 + draftMetadata4 + draftMetadata5
            result.size shouldBe 6

            verify(mockEmailAddressService, times(2)).list(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId1)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId2)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId3)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId("emailAddressId4")
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId("emailAddressId5")
        }

    @Test
    fun `execute() should use limit of 10 when fetching email addresses`() =
        runTest {
            useCase.execute()

            verify(mockEmailAddressService).list(
                check {
                    it.limit shouldBe 10
                },
            )
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId1)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId2)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId3)
        }

    @Test
    fun `execute() should preserve order of draft metadata`() =
        runTest {
            val result = useCase.execute()

            // First all drafts from emailAddressId1, then emailAddressId2, then emailAddressId3
            result[0].emailAddressId shouldBe emailAddressId1
            result[1].emailAddressId shouldBe emailAddressId1
            result[2].emailAddressId shouldBe emailAddressId2

            verify(mockEmailAddressService).list(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId1)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId2)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId3)
        }

    @Test
    fun `execute() should handle large number of email addresses`() =
        runTest {
            val manyEmailAddresses =
                (1..50).map {
                    EntityDataFactory.getSealedEmailAddressEntity(id = "emailAddress$it")
                }

            mockEmailAddressService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailAddressesOutput(
                        items = manyEmailAddresses,
                        nextToken = null,
                    )
            }

            mockDraftEmailMessageService.stub {
                onBlocking { listMetadataForEmailAddressId(any(), anyOrNull(), anyOrNull()) } doReturn
                    ListDraftEmailMessageMetadataOutput(emptyList(), null)
            }

            val result = useCase.execute()

            result shouldBe emptyList()

            verify(mockEmailAddressService).list(any())
            verify(mockDraftEmailMessageService, times(50)).listMetadataForEmailAddressId(any(), anyOrNull(), anyOrNull())
        }

    @Test
    fun `execute() should handle mix of email addresses with and without drafts`() =
        runTest {
            // emailAddress1 has drafts, emailAddress2 has drafts, emailAddress3 has no drafts
            val result = useCase.execute()

            result.size shouldBe 3
            result.count { it.emailAddressId == emailAddressId1 } shouldBe 2
            result.count { it.emailAddressId == emailAddressId2 } shouldBe 1
            result.count { it.emailAddressId == emailAddressId3 } shouldBe 0

            verify(mockEmailAddressService).list(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId1)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId2)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId3)
        }
}
