/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
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
 * Test the correct operation of [ListDraftEmailMessagesForEmailAddressIdUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class ListDraftEmailMessagesForEmailAddressIdUseCaseTest : BaseTests() {
    private val emailAddressId = mockEmailAddressId
    private val draftId1 = "draftId1"
    private val draftId2 = "draftId2"
    private val draftId3 = "draftId3"
    private val unsealedRfc822Data = DataFactory.unsealedHeaderDetailsString.toByteArray()

    private val mockEmailAddress by before {
        EntityDataFactory.getSealedEmailAddressEntity(
            id = emailAddressId,
        )
    }

    private val draftMessagesMetadata by before {
        listOf(
            EntityDataFactory.getDraftEmailMessageMetadataEntity(
                id = draftId1,
                emailAddressId = emailAddressId,
            ),
            EntityDataFactory.getDraftEmailMessageMetadataEntity(
                id = draftId2,
                emailAddressId = emailAddressId,
            ),
            EntityDataFactory.getDraftEmailMessageMetadataEntity(
                id = draftId3,
                emailAddressId = emailAddressId,
            ),
        )
    }

    private val mockDraftEmailMessageService by before {
        mock<DraftEmailMessageService>().stub {
            onBlocking { listMetadataForEmailAddressId(any()) } doReturn draftMessagesMetadata
        }
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking { get(any()) } doReturn mockEmailAddress
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
        ListDraftEmailMessagesForEmailAddressIdUseCase(
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
    fun `execute() should return list of unsealed draft messages successfully`() =
        runTest {
            val result = useCase.execute(emailAddressId)

            result.size shouldBe 3
            result[0].id shouldBe draftId1
            result[0].emailAddressId shouldBe emailAddressId
            result[0].rfc822Data shouldBe unsealedRfc822Data
            result[1].id shouldBe draftId2
            result[1].emailAddressId shouldBe emailAddressId
            result[1].rfc822Data shouldBe unsealedRfc822Data
            result[2].id shouldBe draftId3
            result[2].emailAddressId shouldBe emailAddressId
            result[2].rfc822Data shouldBe unsealedRfc822Data

            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe emailAddressId
                },
            )
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId)
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId1
                    it.emailAddressId shouldBe emailAddressId
                },
            )
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId2
                    it.emailAddressId shouldBe emailAddressId
                },
            )
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId3
                    it.emailAddressId shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `execute() should return empty list when no drafts exist`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { listMetadataForEmailAddressId(any()) } doReturn emptyList()
            }

            val result = useCase.execute(emailAddressId)

            result shouldBe emptyList()

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId)
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
    fun `execute() should handle single draft message`() =
        runTest {
            val singleDraft =
                listOf(
                    EntityDataFactory.getDraftEmailMessageMetadataEntity(
                        id = draftId1,
                        emailAddressId = emailAddressId,
                    ),
                )

            mockDraftEmailMessageService.stub {
                onBlocking { listMetadataForEmailAddressId(any()) } doReturn singleDraft
            }

            val result = useCase.execute(emailAddressId)

            result.size shouldBe 1
            result[0].id shouldBe draftId1
            result[0].emailAddressId shouldBe emailAddressId
            result[0].rfc822Data shouldBe unsealedRfc822Data

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId)
            verify(mockGetDraftEmailMessageUseCase, times(singleDraft.size)).execute(
                check {
                    it.draftId shouldBe draftId1
                    it.emailAddressId shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `execute() should propagate errors from RetrieveDraftEmailMessageUseCase`() =
        runTest {
            mockGetDraftEmailMessageUseCase.stub {
                onBlocking { execute(any()) } doThrow RuntimeException("Error")
            }

            shouldThrow<RuntimeException> {
                useCase.execute(emailAddressId)
            }

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).listMetadataForEmailAddressId(emailAddressId)
            verify(mockGetDraftEmailMessageUseCase).execute(
                check {
                    it.draftId shouldBe draftId1
                    it.emailAddressId shouldBe emailAddressId
                },
            )
        }
}
