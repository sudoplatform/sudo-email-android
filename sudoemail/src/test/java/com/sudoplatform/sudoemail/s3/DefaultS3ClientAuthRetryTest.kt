/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.s3

import android.content.Context
import com.amazonaws.auth.CognitoCredentialsProvider
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.services.cognitoidentity.model.NotAuthorizedException
import com.amazonaws.services.s3.model.ObjectMetadata
import com.sudoplatform.sudologging.LogDriverInterface
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SignInGuard
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.IOException

/**
 * Test the auth retry behavior of [DefaultS3Client].
 *
 * Validates:
 * - NotAuthorizedException with SignInGuard set triggers retry
 * - Successful retry returns the expected result
 * - Failed retry propagates the original exception
 * - Non-auth errors propagate immediately without retry
 * - No SignInGuard set means no retry (backward compatible)
 */
@RunWith(RobolectricTestRunner::class)
class DefaultS3ClientAuthRetryTest {
    private lateinit var mockContext: Context
    private lateinit var mockTransferUtility: TransferUtility
    private lateinit var mockSudoUserClient: SudoUserClient
    private lateinit var mockCredentialsProvider: CognitoCredentialsProvider
    private lateinit var signInGuard: SignInGuard
    private lateinit var logger: Logger

    @Before
    fun setUp() {
        mockContext = RuntimeEnvironment.getApplication()
        mockTransferUtility = mock()
        mockSudoUserClient = mock()
        mockCredentialsProvider =
            mock {
                on { identityId } doReturn "test-identity-id"
            }

        val mockLogDriver =
            mock<LogDriverInterface> {
                on { logLevel } doReturn LogLevel.NONE
            }
        logger = Logger("test", mockLogDriver)

        mockSudoUserClient.stub {
            on { getCredentialsProvider() } doReturn mockCredentialsProvider
        }

        // No callback set — ensureSignedIn() is a no-op
        signInGuard = SignInGuard(mockSudoUserClient)
    }

    private fun createS3Client(withSignInGuard: Boolean = true): DefaultS3Client {
        val client =
            DefaultS3Client(
                context = mockContext,
                sudoUserClient = mockSudoUserClient,
                region = "us-east-1",
                bucket = "test-bucket",
                logger = logger,
            )

        if (withSignInGuard) {
            client.signInGuard = signInGuard
        }

        val field = DefaultS3Client::class.java.getDeclaredField("transferUtility")
        field.isAccessible = true
        field.set(client, mockTransferUtility)

        return client
    }

    // =========================================================================
    // Download tests
    // =========================================================================

    @Test
    fun `download with NotAuthorizedException and signInGuard should retry and succeed`() =
        runTest {
            val s3Client = createS3Client()

            var downloadCallCount = 0
            val mockObserver = mock<TransferObserver>()
            val expectedData = "hello world".toByteArray()

            mockTransferUtility.stub {
                on { download(any<String>(), any<File>()) } doAnswer { invocation ->
                    downloadCallCount++
                    val tmpFile = invocation.getArgument<File>(1)
                    if (downloadCallCount == 2) {
                        tmpFile.writeBytes(expectedData)
                    }
                    mockObserver
                }
            }

            mockObserver.stub {
                on { setTransferListener(any()) } doAnswer { invocation ->
                    val listener = invocation.getArgument<TransferListener>(0)
                    if (downloadCallCount == 1) {
                        listener.onError(1, NotAuthorizedException("Unauthenticated access"))
                    } else {
                        listener.onStateChanged(1, TransferState.COMPLETED)
                    }
                    Unit
                }
            }

            val result = s3Client.download("test-key", S3Client.KeyOptions(isKeyCredentialled = true))

            result shouldBe expectedData
            downloadCallCount shouldBe 2
        }

    @Test
    fun `download with NotAuthorizedException and signInGuard should propagate original on retry failure`() =
        runTest {
            val s3Client = createS3Client()

            val mockObserver = mock<TransferObserver>()

            mockTransferUtility.stub {
                on { download(any<String>(), any<File>()) } doReturn mockObserver
            }

            mockObserver.stub {
                on { setTransferListener(any()) } doAnswer { invocation ->
                    val listener = invocation.getArgument<TransferListener>(0)
                    listener.onError(1, NotAuthorizedException("Unauthenticated access"))
                    Unit
                }
            }

            val exception =
                shouldThrow<S3Exception.DownloadException> {
                    s3Client.download("test-key", S3Client.KeyOptions(isKeyCredentialled = true))
                }

            (exception.cause is NotAuthorizedException) shouldBe true
        }

    @Test
    fun `download with NotAuthorizedException and no signInGuard should propagate immediately`() =
        runTest {
            val s3Client = createS3Client(withSignInGuard = false)

            var downloadCallCount = 0
            val mockObserver = mock<TransferObserver>()

            mockTransferUtility.stub {
                on { download(any<String>(), any<File>()) } doAnswer {
                    downloadCallCount++
                    mockObserver
                }
            }

            mockObserver.stub {
                on { setTransferListener(any()) } doAnswer { invocation ->
                    val listener = invocation.getArgument<TransferListener>(0)
                    listener.onError(1, NotAuthorizedException("Unauthenticated access"))
                    Unit
                }
            }

            shouldThrow<S3Exception.DownloadException> {
                s3Client.download("test-key", S3Client.KeyOptions(isKeyCredentialled = true))
            }

            // Should not have retried
            downloadCallCount shouldBe 1
        }

    @Test
    fun `download with non-auth error should propagate without retry`() =
        runTest {
            val s3Client = createS3Client()

            var downloadCallCount = 0
            val mockObserver = mock<TransferObserver>()
            val networkError = IOException("Network timeout")

            mockTransferUtility.stub {
                on { download(any<String>(), any<File>()) } doAnswer {
                    downloadCallCount++
                    mockObserver
                }
            }

            mockObserver.stub {
                on { setTransferListener(any()) } doAnswer { invocation ->
                    val listener = invocation.getArgument<TransferListener>(0)
                    listener.onError(1, networkError)
                    Unit
                }
            }

            val exception =
                shouldThrow<S3Exception.DownloadException> {
                    s3Client.download("test-key", S3Client.KeyOptions(isKeyCredentialled = true))
                }

            exception.cause shouldBe networkError
            downloadCallCount shouldBe 1
        }

    @Test
    fun `download should succeed on first attempt without retry`() =
        runTest {
            val s3Client = createS3Client()

            var downloadCallCount = 0
            val mockObserver = mock<TransferObserver>()
            val expectedData = "success".toByteArray()

            mockTransferUtility.stub {
                on { download(any<String>(), any<File>()) } doAnswer { invocation ->
                    downloadCallCount++
                    val tmpFile = invocation.getArgument<File>(1)
                    tmpFile.writeBytes(expectedData)
                    mockObserver
                }
            }

            mockObserver.stub {
                on { setTransferListener(any()) } doAnswer { invocation ->
                    val listener = invocation.getArgument<TransferListener>(0)
                    listener.onStateChanged(1, TransferState.COMPLETED)
                    Unit
                }
            }

            val result = s3Client.download("test-key", S3Client.KeyOptions(isKeyCredentialled = true))

            result shouldBe expectedData
            downloadCallCount shouldBe 1
        }

    // =========================================================================
    // Upload tests
    // =========================================================================

    @Test
    fun `upload with NotAuthorizedException and signInGuard should retry and succeed`() =
        runTest {
            val s3Client = createS3Client()

            var uploadCallCount = 0
            val mockObserver = mock<TransferObserver>()

            mockTransferUtility.stub {
                on { upload(any<String>(), any<File>(), any<ObjectMetadata>()) } doAnswer {
                    uploadCallCount++
                    mockObserver
                }
            }

            mockObserver.stub {
                on { setTransferListener(any()) } doAnswer { invocation ->
                    val listener = invocation.getArgument<TransferListener>(0)
                    if (uploadCallCount == 1) {
                        listener.onError(1, NotAuthorizedException("Unauthenticated access"))
                    } else {
                        listener.onStateChanged(1, TransferState.COMPLETED)
                    }
                    Unit
                }
            }

            val result =
                s3Client.upload(
                    "data".toByteArray(),
                    "test-key",
                    null,
                    S3Client.KeyOptions(isKeyCredentialled = true),
                )

            result shouldBe "test-key"
            uploadCallCount shouldBe 2
        }

    @Test
    fun `upload with NotAuthorizedException and signInGuard should propagate original on retry failure`() =
        runTest {
            val s3Client = createS3Client()

            val mockObserver = mock<TransferObserver>()

            mockTransferUtility.stub {
                on { upload(any<String>(), any<File>(), any<ObjectMetadata>()) } doReturn mockObserver
            }

            mockObserver.stub {
                on { setTransferListener(any()) } doAnswer { invocation ->
                    val listener = invocation.getArgument<TransferListener>(0)
                    listener.onError(1, NotAuthorizedException("Unauthenticated access"))
                    Unit
                }
            }

            val exception =
                shouldThrow<S3Exception.UploadException> {
                    s3Client.upload(
                        "data".toByteArray(),
                        "test-key",
                        null,
                        S3Client.KeyOptions(isKeyCredentialled = true),
                    )
                }

            (exception.cause is NotAuthorizedException) shouldBe true
        }

    @Test
    fun `upload with NotAuthorizedException and no signInGuard should propagate immediately`() =
        runTest {
            val s3Client = createS3Client(withSignInGuard = false)

            var uploadCallCount = 0
            val mockObserver = mock<TransferObserver>()

            mockTransferUtility.stub {
                on { upload(any<String>(), any<File>(), any<ObjectMetadata>()) } doAnswer {
                    uploadCallCount++
                    mockObserver
                }
            }

            mockObserver.stub {
                on { setTransferListener(any()) } doAnswer { invocation ->
                    val listener = invocation.getArgument<TransferListener>(0)
                    listener.onError(1, NotAuthorizedException("Unauthenticated access"))
                    Unit
                }
            }

            shouldThrow<S3Exception.UploadException> {
                s3Client.upload(
                    "data".toByteArray(),
                    "test-key",
                    null,
                    S3Client.KeyOptions(isKeyCredentialled = true),
                )
            }

            uploadCallCount shouldBe 1
        }

    @Test
    fun `upload with non-auth error should propagate without retry`() =
        runTest {
            val s3Client = createS3Client()

            var uploadCallCount = 0
            val mockObserver = mock<TransferObserver>()
            val networkError = IOException("Network timeout")

            mockTransferUtility.stub {
                on { upload(any<String>(), any<File>(), any<ObjectMetadata>()) } doAnswer {
                    uploadCallCount++
                    mockObserver
                }
            }

            mockObserver.stub {
                on { setTransferListener(any()) } doAnswer { invocation ->
                    val listener = invocation.getArgument<TransferListener>(0)
                    listener.onError(1, networkError)
                    Unit
                }
            }

            val exception =
                shouldThrow<S3Exception.UploadException> {
                    s3Client.upload(
                        "data".toByteArray(),
                        "test-key",
                        null,
                        S3Client.KeyOptions(isKeyCredentialled = true),
                    )
                }

            exception.cause shouldBe networkError
            uploadCallCount shouldBe 1
        }

    @Test
    fun `upload should succeed on first attempt without retry`() =
        runTest {
            val s3Client = createS3Client()

            var uploadCallCount = 0
            val mockObserver = mock<TransferObserver>()

            mockTransferUtility.stub {
                on { upload(any<String>(), any<File>(), any<ObjectMetadata>()) } doAnswer {
                    uploadCallCount++
                    mockObserver
                }
            }

            mockObserver.stub {
                on { setTransferListener(any()) } doAnswer { invocation ->
                    val listener = invocation.getArgument<TransferListener>(0)
                    listener.onStateChanged(1, TransferState.COMPLETED)
                    Unit
                }
            }

            val result =
                s3Client.upload(
                    "data".toByteArray(),
                    "test-key",
                    null,
                    S3Client.KeyOptions(isKeyCredentialled = true),
                )

            result shouldBe "test-key"
            uploadCallCount shouldBe 1
        }
}
