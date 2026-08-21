/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudoemail.graphql.OnEmailMessageCreatedSubscription
import com.sudoplatform.sudoemail.graphql.OnEmailMessageDeletedSubscription
import com.sudoplatform.sudoemail.graphql.OnEmailMessageUpdatedSubscription
import com.sudoplatform.sudoemail.graphql.fragment.SealedEmailMessage
import com.sudoplatform.sudoemail.graphql.type.EmailMessageDirection
import com.sudoplatform.sudoemail.graphql.type.EmailMessageEncryptionStatus
import com.sudoplatform.sudoemail.graphql.type.EmailMessageState
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.subscription.EmailMessageSubscriber
import com.sudoplatform.sudoemail.subscription.SubscriptionService
import com.sudoplatform.sudoemail.types.EmailMessage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

/**
 * Test that [SubscriptionService] contains exceptions thrown during subscription event
 * processing (e.g. unsealing failures) without crashing the host process.
 *
 * Regression test for PEMC-1825: "Any throwable in SubscriptionService's notification coroutine is fatal
 * to the host app"
 */
@RunWith(RobolectricTestRunner::class)
class SubscriptionServiceExceptionHandlingTest : BaseTests() {
    private val mockDeviceKeyManager by before {
        mock<DeviceKeyManager>()
    }

    private val mockEmailMessageSubscriber by before {
        mock<EmailMessageSubscriber>()
    }

    private val subscriptionService by before {
        SubscriptionService(
            apiClient = mockApiClient,
            deviceKeyManager = mockDeviceKeyManager,
            userClient = mockUserClient,
            logger = mockLogger,
        )
    }

    @After
    fun cleanup() {
        try {
            subscriptionService.close()
        } catch (_: Exception) {
            // Already closed
        }
    }

    private fun buildSealedEmailMessage(): SealedEmailMessage =
        SealedEmailMessage(
            id = "test-message-id",
            owner = "test-owner",
            owners = listOf(SealedEmailMessage.Owner(id = "test-owner", issuer = "sudoplatform")),
            emailAddressId = "test-email-address-id",
            version = 1,
            createdAtEpochMs = 1000000.0,
            updatedAtEpochMs = 1000000.0,
            sortDateEpochMs = 1000000.0,
            folderId = "test-folder-id",
            previousFolderId = null,
            direction = EmailMessageDirection.INBOUND,
            seen = false,
            repliedTo = false,
            forwarded = false,
            state = EmailMessageState.RECEIVED,
            clientRefId = null,
            rfc822Header =
                SealedEmailMessage.Rfc822Header(
                    algorithm = "RSAEncryptionOAEPAESCBC",
                    keyId = "test-key-id",
                    plainTextType = "json",
                    base64EncodedSealedData = "dGVzdC1zZWFsZWQtZGF0YQ==",
                ),
            rfc822DataAttributes =
                SealedEmailMessage.Rfc822DataAttributes(
                    bucket = "test-bucket",
                    key = "test-key",
                ),
            size = 1024.0,
            encryptionStatus = EmailMessageEncryptionStatus.UNENCRYPTED,
            emailMaskId = null,
        )

    @Test
    fun `onSubscription created callback should not crash when transform throws NullPointerException`() =
        runTest {
            // Simulate the corrupted AndroidKeyStore scenario from Crashlytics
            mockDeviceKeyManager.stub {
                on { decryptWithKeyPairId(any(), any(), any()) }.thenThrow(
                    NullPointerException("invalid null input"),
                )
            }

            mockUserClient.stub {
                onBlocking { getSubject() } doReturn "test-subject"
            }

            val onSubscriptionCaptor =
                argumentCaptor<Consumer<GraphQLResponse<OnEmailMessageCreatedSubscription.Data>>>()

            mockApiClient.stub {
                onBlocking {
                    onEmailMessageCreatedSubscription(
                        any(),
                        any(),
                        onSubscriptionCaptor.capture(),
                        any(),
                        any(),
                    )
                } doReturn mock<GraphQLOperation<OnEmailMessageCreatedSubscription.Data>>()

                onBlocking {
                    onEmailMessageDeletedSubscription(any(), any(), any(), any(), any())
                } doReturn mock<GraphQLOperation<OnEmailMessageDeletedSubscription.Data>>()

                onBlocking {
                    onEmailMessageUpdatedSubscription(any(), any(), any(), any(), any())
                } doReturn mock<GraphQLOperation<OnEmailMessageUpdatedSubscription.Data>>()
            }

            // Subscribe
            subscriptionService.subscribeEmailMessages("test-id", mockEmailMessageSubscriber)

            // Build a fake response that will trigger the transform
            val sealedMessage = buildSealedEmailMessage()
            val subscriptionData =
                OnEmailMessageCreatedSubscription.Data(
                    onEmailMessageCreated =
                        OnEmailMessageCreatedSubscription.OnEmailMessageCreated(
                            __typename = "SealedEmailMessage",
                            sealedEmailMessage = sealedMessage,
                        ),
                )
            val fakeResponse = GraphQLResponse(subscriptionData, null)

            // Invoke the captured onSubscription callback - this should NOT crash
            onSubscriptionCaptor.firstValue.accept(fakeResponse)

            // Give the coroutine time to execute
            Thread.sleep(200)

            // Verify the subscriber was never notified (because the transform failed before reaching it)
            verify(mockEmailMessageSubscriber, never()).emailMessageChanged(
                any<EmailMessage>(),
                any(),
            )
        }

    @Test
    fun `onSubscription deleted callback should not crash when transform throws`() =
        runTest {
            mockDeviceKeyManager.stub {
                on { decryptWithKeyPairId(any(), any(), any()) }.thenThrow(
                    NullPointerException("invalid null input"),
                )
            }

            mockUserClient.stub {
                onBlocking { getSubject() } doReturn "test-subject"
            }

            val onSubscriptionCaptor =
                argumentCaptor<Consumer<GraphQLResponse<OnEmailMessageDeletedSubscription.Data>>>()

            mockApiClient.stub {
                onBlocking {
                    onEmailMessageCreatedSubscription(any(), any(), any(), any(), any())
                } doReturn mock<GraphQLOperation<OnEmailMessageCreatedSubscription.Data>>()

                onBlocking {
                    onEmailMessageDeletedSubscription(
                        any(),
                        any(),
                        onSubscriptionCaptor.capture(),
                        any(),
                        any(),
                    )
                } doReturn mock<GraphQLOperation<OnEmailMessageDeletedSubscription.Data>>()

                onBlocking {
                    onEmailMessageUpdatedSubscription(any(), any(), any(), any(), any())
                } doReturn mock<GraphQLOperation<OnEmailMessageUpdatedSubscription.Data>>()
            }

            subscriptionService.subscribeEmailMessages("test-id", mockEmailMessageSubscriber)

            val sealedMessage = buildSealedEmailMessage()
            val subscriptionData =
                OnEmailMessageDeletedSubscription.Data(
                    onEmailMessageDeleted =
                        OnEmailMessageDeletedSubscription.OnEmailMessageDeleted(
                            __typename = "SealedEmailMessage",
                            sealedEmailMessage = sealedMessage,
                        ),
                )
            val fakeResponse = GraphQLResponse(subscriptionData, null)

            onSubscriptionCaptor.firstValue.accept(fakeResponse)

            Thread.sleep(200)

            verify(mockEmailMessageSubscriber, never()).emailMessageChanged(
                any<EmailMessage>(),
                any(),
            )
        }

    @Test
    fun `onSubscription updated callback should not crash when transform throws`() =
        runTest {
            mockDeviceKeyManager.stub {
                on { decryptWithKeyPairId(any(), any(), any()) }.thenThrow(
                    NullPointerException("invalid null input"),
                )
            }

            mockUserClient.stub {
                onBlocking { getSubject() } doReturn "test-subject"
            }

            val onSubscriptionCaptor =
                argumentCaptor<Consumer<GraphQLResponse<OnEmailMessageUpdatedSubscription.Data>>>()

            mockApiClient.stub {
                onBlocking {
                    onEmailMessageCreatedSubscription(any(), any(), any(), any(), any())
                } doReturn mock<GraphQLOperation<OnEmailMessageCreatedSubscription.Data>>()

                onBlocking {
                    onEmailMessageDeletedSubscription(any(), any(), any(), any(), any())
                } doReturn mock<GraphQLOperation<OnEmailMessageDeletedSubscription.Data>>()

                onBlocking {
                    onEmailMessageUpdatedSubscription(
                        any(),
                        any(),
                        onSubscriptionCaptor.capture(),
                        any(),
                        any(),
                    )
                } doReturn mock<GraphQLOperation<OnEmailMessageUpdatedSubscription.Data>>()
            }

            subscriptionService.subscribeEmailMessages("test-id", mockEmailMessageSubscriber)

            val sealedMessage = buildSealedEmailMessage()
            val subscriptionData =
                OnEmailMessageUpdatedSubscription.Data(
                    onEmailMessageUpdated =
                        OnEmailMessageUpdatedSubscription.OnEmailMessageUpdated(
                            __typename = "SealedEmailMessage",
                            sealedEmailMessage = sealedMessage,
                        ),
                )
            val fakeResponse = GraphQLResponse(subscriptionData, null)

            onSubscriptionCaptor.firstValue.accept(fakeResponse)

            Thread.sleep(200)

            verify(mockEmailMessageSubscriber, never()).emailMessageChanged(
                any<EmailMessage>(),
                any(),
            )
        }

    @Test
    fun `close() cancels scope and coroutines do not process events after closing`() =
        runTest {
            mockDeviceKeyManager.stub {
                on { decryptWithKeyPairId(any(), any(), any()) }.thenThrow(
                    NullPointerException("invalid null input"),
                )
            }

            mockUserClient.stub {
                onBlocking { getSubject() } doReturn "test-subject"
            }

            val onSubscriptionCaptor =
                argumentCaptor<Consumer<GraphQLResponse<OnEmailMessageCreatedSubscription.Data>>>()

            mockApiClient.stub {
                onBlocking {
                    onEmailMessageCreatedSubscription(
                        any(),
                        any(),
                        onSubscriptionCaptor.capture(),
                        any(),
                        any(),
                    )
                } doReturn mock<GraphQLOperation<OnEmailMessageCreatedSubscription.Data>>()

                onBlocking {
                    onEmailMessageDeletedSubscription(any(), any(), any(), any(), any())
                } doReturn mock<GraphQLOperation<OnEmailMessageDeletedSubscription.Data>>()

                onBlocking {
                    onEmailMessageUpdatedSubscription(any(), any(), any(), any(), any())
                } doReturn mock<GraphQLOperation<OnEmailMessageUpdatedSubscription.Data>>()
            }

            subscriptionService.subscribeEmailMessages("test-id", mockEmailMessageSubscriber)

            // Close the service - this cancels the scope
            subscriptionService.close()

            // Try to invoke the callback after close - the coroutine should not process it
            val sealedMessage = buildSealedEmailMessage()
            val subscriptionData =
                OnEmailMessageCreatedSubscription.Data(
                    onEmailMessageCreated =
                        OnEmailMessageCreatedSubscription.OnEmailMessageCreated(
                            __typename = "SealedEmailMessage",
                            sealedEmailMessage = sealedMessage,
                        ),
                )
            val fakeResponse = GraphQLResponse(subscriptionData, null)

            onSubscriptionCaptor.firstValue.accept(fakeResponse)

            Thread.sleep(200)

            // Verify no processing happened after close
            verify(mockEmailMessageSubscriber, never()).emailMessageChanged(
                any<EmailMessage>(),
                any(),
            )
        }
}
