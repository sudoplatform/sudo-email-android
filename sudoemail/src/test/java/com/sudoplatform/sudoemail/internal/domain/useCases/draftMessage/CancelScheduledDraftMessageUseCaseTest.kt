/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.amazonaws.auth.CognitoCredentialsProvider
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudouser.SudoUserClient
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

/**
 * Test the correct operation of [CancelScheduledDraftMessageUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class CancelScheduledDraftMessageUseCaseTest : BaseTests() {
    private val emailAddressId = mockEmailAddressId
    private val draftId = "draftId"
    private val identityId = "identityId"

    private val mockEmailAddress by before {
        EntityDataFactory.getSealedEmailAddressEntity(
            id = emailAddressId,
        )
    }

    private val mockDraftEmailMessageService by before {
        mock<DraftEmailMessageService>().stub {
            onBlocking { cancelScheduledDraftMessage(any()) } doReturn draftId
        }
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking { get(any()) } doReturn mockEmailAddress
        }
    }

    private val mockCredentialsProvider by before {
        mock<CognitoCredentialsProvider>().stub {
            on { identityId } doReturn identityId
        }
    }

    private val mockSudoUserClient by before {
        mock<SudoUserClient>().stub {
            on { getCredentialsProvider() } doReturn mockCredentialsProvider
        }
    }

    private val useCase by before {
        CancelScheduledDraftMessageUseCase(
            draftEmailMessageService = mockDraftEmailMessageService,
            emailAddressService = mockEmailAddressService,
            sudoUserClient = mockSudoUserClient,
            logger = mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockDraftEmailMessageService,
            mockEmailAddressService,
            mockSudoUserClient,
        )
    }

    @Test
    fun `execute() should return draft ID on success`() =
        runTest {
            val input =
                CancelScheduledDraftMessageUseCaseInput(
                    draftId = draftId,
                    emailAddressId = emailAddressId,
                )

            val result = useCase.execute(input)

            result shouldBe draftId

            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe emailAddressId
                },
            )
            verify(mockDraftEmailMessageService).cancelScheduledDraftMessage(
                check {
                    it.draftMessageKey.contains(emailAddressId) shouldBe true
                    it.draftMessageKey.contains(draftId) shouldBe true
                    it.emailAddressId shouldBe emailAddressId
                },
            )
            verify(mockSudoUserClient).getCredentialsProvider()
            verify(mockCredentialsProvider).identityId
        }

    @Test
    fun `execute() should throw EmailAddressNotFoundException when email address does not exist`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { get(any()) } doReturn null
            }

            val input =
                CancelScheduledDraftMessageUseCaseInput(
                    draftId = draftId,
                    emailAddressId = emailAddressId,
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
    fun `execute() should construct correct S3 key for draft message`() =
        runTest {
            val input =
                CancelScheduledDraftMessageUseCaseInput(
                    draftId = draftId,
                    emailAddressId = emailAddressId,
                )

            useCase.execute(input)

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).cancelScheduledDraftMessage(
                check { request ->
                    request.draftMessageKey.contains(emailAddressId) shouldBe true
                    request.draftMessageKey.contains(draftId) shouldBe true
                    request.draftMessageKey.contains("/draft/") shouldBe true
                },
            )
            verify(mockSudoUserClient).getCredentialsProvider()
            verify(mockCredentialsProvider).identityId
        }

    @Test
    fun `execute() should propagate EmailMessageException from service`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { cancelScheduledDraftMessage(any()) } doThrow
                    SudoEmailClient.EmailMessageException.FailedException("Cancel failed")
            }

            val input =
                CancelScheduledDraftMessageUseCaseInput(
                    draftId = draftId,
                    emailAddressId = emailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).cancelScheduledDraftMessage(any())
            verify(mockSudoUserClient).getCredentialsProvider()
            verify(mockCredentialsProvider).identityId
        }

    @Test
    fun `execute() should interpret generic exceptions as EmailMessageException`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { cancelScheduledDraftMessage(any()) } doThrow RuntimeException("Unexpected error")
            }

            val input =
                CancelScheduledDraftMessageUseCaseInput(
                    draftId = draftId,
                    emailAddressId = emailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailMessageException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).cancelScheduledDraftMessage(any())
            verify(mockSudoUserClient).getCredentialsProvider()
            verify(mockCredentialsProvider).identityId
        }

    @Test
    fun `execute() should handle InvalidArgumentException from service`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { cancelScheduledDraftMessage(any()) } doThrow
                    SudoEmailClient.EmailMessageException.InvalidArgumentException("Invalid draft ID")
            }

            val input =
                CancelScheduledDraftMessageUseCaseInput(
                    draftId = draftId,
                    emailAddressId = emailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).cancelScheduledDraftMessage(any())
            verify(mockSudoUserClient).getCredentialsProvider()
            verify(mockCredentialsProvider).identityId
        }
}
