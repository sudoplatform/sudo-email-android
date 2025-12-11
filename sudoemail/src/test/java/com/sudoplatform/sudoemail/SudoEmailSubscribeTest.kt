/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.subscription.EmailMessageSubscriber
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
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
    override val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "subject"
        }
    }

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
        verifyNoMoreInteractions(
            mockUserClient,
            mockKeyManager,
            mockApiClient,
            mockS3Client,
        )
    }

    @Test
    fun `subscribeToEmailMessages() should throw when not authenticated`() =
        runTest {
            mockUserClient.stub {
                on { getSubject() } doReturn null
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

            verify(mockUserClient, times(2)).getSubject()
        }
}
