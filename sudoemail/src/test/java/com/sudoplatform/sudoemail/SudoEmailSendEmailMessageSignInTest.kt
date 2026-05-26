/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.SendEmailMessageResultEntity
import com.sudoplatform.sudoemail.internal.domain.useCases.UseCaseFactory
import com.sudoplatform.sudoemail.internal.domain.useCases.emailMessage.SendEmailMessageUseCase
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.EmailAttachment
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.InternetMessageFormatHeader
import com.sudoplatform.sudoemail.types.inputs.SendEmailMessageInput
import com.sudoplatform.sudouser.SudoPlatformSignInCallback
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the correct operation of [DefaultSudoEmailClient.sendEmailMessage]
 * with sign-in callback integration using mocks and spies.
 *
 * Tests cover:
 * - No callback scenario (backward compatibility)
 * - Callback set with user signed in (callback not invoked)
 * - Callback set with user not signed in (callback invoked)
 * - Callback exception propagation
 *
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailSendEmailMessageSignInTest : BaseTests() {
    private val headers =
        InternetMessageFormatHeader(
            EmailMessage.EmailAddress("from@bar.com"),
            listOf(EmailMessage.EmailAddress("to@bar.com")),
            listOf(EmailMessage.EmailAddress("cc@bar.com")),
            listOf(EmailMessage.EmailAddress("bcc@bar.com")),
            listOf(EmailMessage.EmailAddress("replyTo@bar.com")),
            "email message subject",
        )

    private val input =
        SendEmailMessageInput(
            senderEmailAddressId = mockEmailAddressId,
            emailMessageHeader = headers,
            body = "email message body",
            attachments =
                listOf(
                    EmailAttachment(
                        "fileName.pdf",
                        "contentId",
                        "mimeType",
                        false,
                        ByteArray(1),
                    ),
                ),
            inlineAttachment =
                listOf(
                    EmailAttachment(
                        "fileName.jpg",
                        "contentId",
                        "mimeType",
                        true,
                        ByteArray(1),
                    ),
                ),
        )

    private val sendResult by before {
        SendEmailMessageResultEntity(
            id = "emailMessageId",
            createdAt = Date(1L),
        )
    }

    private val mockUseCase by before {
        mock<SendEmailMessageUseCase>().stub {
            onBlocking { execute(any()) } doReturn sendResult
        }
    }

    private val mockUseCaseFactory by before {
        mock<UseCaseFactory>().stub {
            on { createSendEmailMessageUseCase() } doReturn mockUseCase
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
        verifyNoMoreInteractions(
            mockUseCaseFactory,
            mockUseCase,
        )
    }

    @Test
    fun `sendEmailMessage should succeed without sign-in check when no callback is set`() =
        runTest {
            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.sendEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id shouldBe "emailMessageId"

            // Verify the operation proceeded without sign-in check
            verify(mockUseCaseFactory).createSendEmailMessageUseCase()
            verify(mockUseCase).execute(any())
            // Note: mockUserClient.isSignedIn() should NOT be called
        }

    @Test
    fun `sendEmailMessage should succeed without invoking callback when user is signed in`() =
        runTest {
            var callbackInvoked = false

            // Setup: Mock user client to return signed in status
            mockUserClient.stub {
                onBlocking { isSignedIn() } doReturn true
            }

            // Set callback
            client.setSignInCallback(
                object : SudoPlatformSignInCallback {
                    override suspend fun signIn() {
                        callbackInvoked = true
                    }
                },
            )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.sendEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id shouldBe "emailMessageId"

            // Verify callback was NOT invoked since user is signed in
            callbackInvoked shouldBe false
            verify(mockUserClient).isSignedIn()
            verify(mockUseCaseFactory).createSendEmailMessageUseCase()
            verify(mockUseCase).execute(any())
        }

    @Test
    fun `sendEmailMessage should invoke callback when user is not signed in`() =
        runTest {
            var callbackInvoked = false

            // Setup: Mock user client to return not signed in
            mockUserClient.stub {
                onBlocking { isSignedIn() } doReturn false
            }

            // Set callback
            client.setSignInCallback(
                object : SudoPlatformSignInCallback {
                    override suspend fun signIn() {
                        callbackInvoked = true
                    }
                },
            )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    client.sendEmailMessage(input)
                }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.id shouldBe "emailMessageId"

            // Verify callback WAS invoked since user is not signed in
            callbackInvoked shouldBe true
            verify(mockUserClient).isSignedIn()
            verify(mockUseCaseFactory).createSendEmailMessageUseCase()
            verify(mockUseCase).execute(any())
        }

    @Test
    fun `sendEmailMessage should propagate callback exception and not execute operation`() =
        runTest {
            val testException = RuntimeException("Sign-in failed")

            // Setup: Mock user client to return not signed in
            mockUserClient.stub {
                onBlocking { isSignedIn() } doReturn false
            }

            // Set callback that throws exception
            client.setSignInCallback(
                object : SudoPlatformSignInCallback {
                    override suspend fun signIn(): Unit = throw testException
                },
            )

            val deferredResult =
                async(StandardTestDispatcher(testScheduler)) {
                    shouldThrow<RuntimeException> {
                        client.sendEmailMessage(input)
                    }
                }
            deferredResult.start()
            val thrownException = deferredResult.await()

            // Verify the correct exception was thrown
            thrownException shouldBe testException
            verify(mockUserClient).isSignedIn()
            // Note: sendEmailMessage use case should NOT be called because callback threw
        }
}
