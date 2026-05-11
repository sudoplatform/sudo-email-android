/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.EmailMessageBodyCache
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.subscription.EmailMessageSubscriber
import com.sudoplatform.sudoemail.subscription.SubscriptionService
import com.sudoplatform.sudoemail.types.EmailMessage
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.subscribeToEmailMessages] using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailSubscribeTest : BaseTests() {
    private val mockEmailMessageSubscriber by before {
        mock<EmailMessageSubscriber>()
    }

    private val mockServiceKeyManager by before {
        DefaultServiceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger,
        )
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>()
    }

    private val mockEmailMessageBodyCache by before {
        mock<EmailMessageBodyCache>()
    }

    private val mockSubscriptions by before {
        mock<SubscriptionService>()
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
            emailMessageBodyCache = mockEmailMessageBodyCache,
            subscriptions = mockSubscriptions,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockKeyManager,
            mockApiClient,
            mockS3Client,
        )
    }

    @Test
    fun `subscribeToEmailMessages() should throw when not authenticated`() =
        runTest {
            mockSubscriptions.stub {
                onBlocking {
                    subscribeEmailMessages(any(), any())
                } doThrow SudoEmailClient.EmailMessageException.AuthenticationException()
            }

            shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
                client.subscribeToEmailMessages("id", mockEmailMessageSubscriber)
            }

            shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
                client.subscribeToEmailMessages(
                    "id",
                    mockEmailMessageSubscriber,
                )
            }

            verify(mockSubscriptions, times(2)).subscribeEmailMessages(any(), any())
        }

    @Test
    fun `subscribeToEmailMessages() should call cache deleteMessage on DELETED event`() =
        runTest {
            val subscriberCaptor = argumentCaptor<EmailMessageSubscriber>()

            client.subscribeToEmailMessages("test-sub-id", mockEmailMessageSubscriber)

            verify(mockSubscriptions).subscribeEmailMessages(any(), subscriberCaptor.capture())

            // Simulate a DELETED event
            val mockEmailMessage =
                mock<EmailMessage>().stub {
                    on { id } doAnswer { "deleted-message-id" }
                }

            subscriberCaptor.firstValue.emailMessageChanged(
                mockEmailMessage,
                EmailMessageSubscriber.ChangeType.DELETED,
            )

            // Give the coroutine time to execute
            Thread.sleep(100)

            verify(mockEmailMessageBodyCache).deleteMessage("deleted-message-id")
            verify(mockEmailMessageSubscriber).emailMessageChanged(mockEmailMessage, EmailMessageSubscriber.ChangeType.DELETED)
        }

    @Test
    fun `subscribeToEmailMessages() should not call cache deleteMessage on CREATED event`() =
        runTest {
            val subscriberCaptor = argumentCaptor<EmailMessageSubscriber>()

            client.subscribeToEmailMessages("test-sub-id", mockEmailMessageSubscriber)

            verify(mockSubscriptions).subscribeEmailMessages(any(), subscriberCaptor.capture())

            // Simulate a CREATED event
            val mockEmailMessage =
                mock<EmailMessage>().stub {
                    on { id } doAnswer { "created-message-id" }
                }

            subscriberCaptor.firstValue.emailMessageChanged(
                mockEmailMessage,
                EmailMessageSubscriber.ChangeType.CREATED,
            )

            // Give the coroutine time (if any) to execute
            Thread.sleep(100)

            verify(mockEmailMessageBodyCache, never()).deleteMessage(any())
            verify(mockEmailMessageSubscriber).emailMessageChanged(mockEmailMessage, EmailMessageSubscriber.ChangeType.CREATED)
        }
}
