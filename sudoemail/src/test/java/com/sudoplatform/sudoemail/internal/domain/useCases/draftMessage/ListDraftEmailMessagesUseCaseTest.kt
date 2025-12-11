/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ListEmailAddressesOutput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.ListEmailAddressesRequest
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [ListDraftEmailMessagesUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class ListDraftEmailMessagesUseCaseTest : BaseTests() {
    private val emailAddressId1 = "emailAddressId1"
    private val emailAddressId2 = "emailAddressId2"
    private val emailAddressId3 = "emailAddressId3"
    private val draftId1 = "draftId1"
    private val draftId2 = "draftId2"
    private val draftId3 = "draftId3"

    private val unsealedRfc822Data = DataFactory.unsealedHeaderDetailsString.toByteArray()

    private val mockEmailAddress1 by before {
        EntityDataFactory.getSealedEmailAddressEntity(id = emailAddressId1)
    }

    private val mockEmailAddress2 by before {
        EntityDataFactory.getSealedEmailAddressEntity(id = emailAddressId2)
    }

    private val mockEmailAddress3 by before {
        EntityDataFactory.getSealedEmailAddressEntity(id = emailAddressId3)
    }

    private val draftsForAddress1 by before {
        listOf(
            EntityDataFactory.getDraftEmailMessageMetadataEntity(
                id = draftId1,
                emailAddressId = emailAddressId1,
            ),
            EntityDataFactory.getDraftEmailMessageMetadataEntity(
                id = draftId2,
                emailAddressId = emailAddressId1,
            ),
        )
    }

    private val draftsForAddress2 by before {
        listOf(
            EntityDataFactory.getDraftEmailMessageMetadataEntity(
                id = draftId3,
                emailAddressId = emailAddressId2,
            ),
        )
    }

    private val mockDraftEmailMessageService by before {
        mock<DraftEmailMessageService>().stub {
            onBlocking { listMetadataForEmailAddressId(emailAddressId1) } doReturn draftsForAddress1
            onBlocking { listMetadataForEmailAddressId(emailAddressId2) } doReturn draftsForAddress2
            onBlocking { listMetadataForEmailAddressId(emailAddressId3) } doReturn emptyList()
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

    private val mockSealingService by before {
        mock<SealingService>()
    }

    private val mockEmailMessageDataProcessor by before {
        mock<EmailMessageDataProcessor>()
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>()
    }

    private val mockGetDraftEmailMessageUseCase by before {
        mock<GetDraftEmailMessageUseCase>().stub {
            onBlocking { execute(any()) } doAnswer { invocation ->
                val input = invocation.getArgument<GetDraftEmailMessageUseCaseInput>(0)
                EntityDataFactory.getDraftEmailMessageWithContentEntity(
                    id = input.draftId,
                    emailAddressId = input.emailAddressId,
                    rfc822Data = unsealedRfc822Data,
                )
            }
        }
    }

    private val useCase by before {
        ListDraftEmailMessagesUseCase(
            draftEmailMessageService = mockDraftEmailMessageService,
            emailAddressService = mockEmailAddressService,
            sealingService = mockSealingService,
            emailMessageDataProcessor = mockEmailMessageDataProcessor,
            emailCryptoService = mockEmailCryptoService,
            logger = mockLogger,
            getDraftEmailMessageUseCase = mockGetDraftEmailMessageUseCase,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockDraftEmailMessageService,
            mockEmailAddressService,
            mockSealingService,
            mockEmailMessageDataProcessor,
            mockEmailCryptoService,
            mockGetDraftEmailMessageUseCase,
        )
    }

    @Test
    fun `execute() should return all draft messages from all email addresses`() =
        runTest {
            val result = useCase.execute()

            result.size shouldBe 3
            result[0].id shouldBe draftId1
            result[0].emailAddressId shouldBe emailAddressId1
            result[1].id shouldBe draftId2
            result[1].emailAddressId shouldBe emailAddressId1
            result[2].id shouldBe draftId3
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
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId1
                    it.emailAddressId shouldBe emailAddressId1
                },
            )
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId2
                    it.emailAddressId shouldBe emailAddressId1
                },
            )
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId3
                    it.emailAddressId shouldBe emailAddressId2
                },
            )
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
                onBlocking { listMetadataForEmailAddressId(any()) } doReturn emptyList()
            }

            val result = useCase.execute()

            result shouldBe emptyList()

            verify(mockEmailAddressService).list(any())
            verify(mockDraftEmailMessageService, times(3)).listMetadataForEmailAddressId(any())
        }

    @Test
    fun `execute() should handle pagination of email addresses`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { list(ListEmailAddressesRequest(nextToken = null, limit = 10)) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(mockEmailAddress1),
                        nextToken = "token1",
                    )
                onBlocking { list(ListEmailAddressesRequest(nextToken = "token1", limit = 10)) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(mockEmailAddress2),
                        nextToken = "token2",
                    )
                onBlocking { list(ListEmailAddressesRequest(nextToken = "token2", limit = 10)) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(mockEmailAddress3),
                        nextToken = null,
                    )
            }

            val result = useCase.execute()

            result.size shouldBe 3

            verify(mockEmailAddressService, times(3)).list(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId1)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId2)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId3)
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId1
                    it.emailAddressId shouldBe emailAddressId1
                },
            )
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId2
                    it.emailAddressId shouldBe emailAddressId1
                },
            )
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId3
                    it.emailAddressId shouldBe emailAddressId2
                },
            )
        }

    @Test
    fun `execute() should handle single email address with multiple drafts`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailAddressesOutput(
                        items = listOf(mockEmailAddress1),
                        nextToken = null,
                    )
            }

            val result = useCase.execute()

            result.size shouldBe 2
            result[0].id shouldBe draftId1
            result[1].id shouldBe draftId2

            verify(mockEmailAddressService).list(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId1)
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId1
                    it.emailAddressId shouldBe emailAddressId1
                },
            )
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId2
                    it.emailAddressId shouldBe emailAddressId1
                },
            )
        }

    @Test
    fun `execute() should pass limit of 10 when fetching email addresses`() =
        runTest {
            useCase.execute()

            verify(mockEmailAddressService).list(
                check {
                    it.limit shouldBe 10
                },
            )

            verify(mockEmailAddressService, times(1)).list(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId1)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId2)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId3)
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId1
                    it.emailAddressId shouldBe emailAddressId1
                },
            )
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId2
                    it.emailAddressId shouldBe emailAddressId1
                },
            )
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId3
                    it.emailAddressId shouldBe emailAddressId2
                },
            )
        }

    @Test
    fun `execute() should propagate RetrieveDraftEmailMessageUseCase exceptions`() =
        runTest {
            mockGetDraftEmailMessageUseCase.stub {
                onBlocking { execute(any()) } doThrow RuntimeException("Error")
            }

            shouldThrow<RuntimeException> {
                useCase.execute()
            }

            verify(mockEmailAddressService).list(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId1)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId2)
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId3)
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId1
                    it.emailAddressId shouldBe emailAddressId1
                },
            )
        }
}
