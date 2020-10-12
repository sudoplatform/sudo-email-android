/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.keys.DefaultPublicKeyService
import com.sudoplatform.sudoemail.subscription.EmailMessageSubscriber
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudoprofiles.S3Client
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.subscribeToEmailMessages] using mocks and spies.
 *
 * @since 2020-08-25
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailSubscribeTest : BaseTests() {

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "subject"
        }
    }

    private val mockSudoClient by before {
        mock<SudoProfilesClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>()
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val mockDeviceKeyManager by before {
        DefaultDeviceKeyManager(
            mockContext,
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger
        )
    }

    private val publicKeyService by before {
        DefaultPublicKeyService(
            mockDeviceKeyManager,
            mockAppSyncClient,
            mockLogger
        )
    }

    private val mockS3Client by before {
        mock<S3Client>()
    }

    private val client by before {
        DefaultSudoEmailClient(
            mockContext,
            mockAppSyncClient,
            mockUserClient,
            mockSudoClient,
            mockLogger,
            mockDeviceKeyManager,
            publicKeyService,
            "region",
            "identityBucket",
            "transientBucket",
            mockS3Client,
            mockS3Client
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockContext, mockUserClient, mockSudoClient, mockKeyManager, mockAppSyncClient, mockS3Client)
    }

    @Test
    fun `subscribeToEmailMessages() should throw when not authenticated`() = runBlocking<Unit> {

        mockUserClient.stub {
            on { getSubject() } doReturn null
        }

        shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
            client.subscribeToEmailMessages("id", mock<EmailMessageSubscriber>())
        }

        shouldThrow<SudoEmailClient.EmailMessageException.AuthenticationException> {
            client.subscribeToEmailMessages(
                "id",
                onEmailMessageCreated = {},
                onEmailMessageDeleted = {}
            )
        }

        verify(mockUserClient, times(2)).getSubject()
    }
}
