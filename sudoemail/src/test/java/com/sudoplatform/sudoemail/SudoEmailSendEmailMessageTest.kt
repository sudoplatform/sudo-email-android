/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.SendEmailMessageMutation
import com.sudoplatform.sudoemail.graphql.type.S3EmailObjectInput
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.sealing.DefaultSealingService
import com.sudoplatform.sudoemail.types.inputs.SendEmailMessageInput
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import com.sudoplatform.sudoemail.graphql.type.SendEmailMessageInput as SendEmailMessageRequest

/**
 * Test the correct operation of [SudoEmailClient.sendEmailMessage]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailSendEmailMessageTest : BaseTests() {

    private val message by before {
        S3EmailObjectInput.builder()
            .bucket("bucket")
            .key("key")
            .region("region")
            .build()
    }

    private val input by before {
        SendEmailMessageRequest.builder()
            .emailAddressId("emailAddressId")
            .clientRefId("clientRefId")
            .message(message)
            .build()
    }

    private val mutationResult by before {
        "sendEmailMessage"
    }

    private val mutationResponse by before {
        Response.builder<SendEmailMessageMutation.Data>(SendEmailMessageMutation(input))
            .data(SendEmailMessageMutation.Data(mutationResult))
            .build()
    }

    private val holder = CallbackHolder<SendEmailMessageMutation.Data>()

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "subject"
            on { getRefreshToken() } doReturn "refreshToken"
        }
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<SendEmailMessageMutation>()) } doReturn holder.mutationOperation
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { getPassword(anyString()) } doReturn ByteArray(42)
            on { getPublicKeyData(anyString()) } doReturn ByteArray(42)
            on { getPrivateKeyData(anyString()) } doReturn ByteArray(42)
        }
    }

    private val mockDeviceKeyManager by before {
        DefaultDeviceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger
        )
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking { upload(any(), anyString(), anyOrNull()) } doReturn "42"
        }
    }

    private val mockSealingService by before {
        DefaultSealingService(
            mockDeviceKeyManager,
            mockLogger
        )
    }

    private val client by before {
        DefaultSudoEmailClient(
            context,
            mockAppSyncClient,
            mockUserClient,
            mockLogger,
            mockDeviceKeyManager,
            mockSealingService,
            "region",
            "identityBucket",
            "transientBucket",
            mockS3Client,
            mockS3Client
        )
    }

    @Before
    fun init() {
        holder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockUserClient, mockKeyManager, mockAppSyncClient, mockS3Client)
    }

    @Test
    fun `sendEmailMessage() should return results when no error present`() = runBlocking {

        holder.callback shouldBe null

        val input = SendEmailMessageInput(
            "rfc822data".toByteArray(),
            "senderEmailAddress"
        )
        val deferredResult = async(Dispatchers.IO) {
            client.sendEmailMessage(input)
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(mutationResponse)

        val result = deferredResult.await()
        result shouldNotBe null
        result.isBlank() shouldBe false

        verify(mockAppSyncClient).mutate(any<SendEmailMessageMutation>())
        verify(mockS3Client).upload(any(), anyString(), anyOrNull())
        verify(mockS3Client).delete(anyString())
    }

    @Test
    fun `sendEmailMessage() should throw when email mutation response is null`() = runBlocking {

        holder.callback shouldBe null

        val nullProvisionResponse by before {
            Response.builder<SendEmailMessageMutation.Data>(SendEmailMessageMutation(input))
                .data(null)
                .build()
        }

        val input = SendEmailMessageInput(
            "rfc822data".toByteArray(),
            "senderEmailAddress"
        )
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.sendEmailMessage(input)
            }
        }
        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(nullProvisionResponse)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<SendEmailMessageMutation>())
        verify(mockS3Client).upload(any(), anyString(), anyOrNull())
        verify(mockS3Client).delete(anyString())
    }

    @Test
    fun `sendEmailMessage() should throw when response has various errors`() = runBlocking {
        testException<SudoEmailClient.EmailMessageException.InvalidMessageContentException>("InvalidEmailContents")
        testException<SudoEmailClient.EmailMessageException.UnauthorizedAddressException>("UnauthorizedAddress")
        testException<SudoEmailClient.EmailMessageException.FailedException>("blah")

        verify(mockAppSyncClient, times(3)).mutate(any<SendEmailMessageMutation>())
        verify(mockS3Client, times(3)).upload(any(), anyString(), anyOrNull())
        verify(mockS3Client, times(3)).delete(anyString())
    }

    private inline fun <reified T : Exception> testException(apolloError: String) = runBlocking<Unit> {

        holder.callback = null

        val errorSendResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to apolloError)
            )
            Response.builder<SendEmailMessageMutation.Data>(SendEmailMessageMutation(input))
                .errors(listOf(error))
                .build()
        }

        val input = SendEmailMessageInput(
            "rfc822data".toByteArray(),
            "senderEmailAddress"
        )
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<T> {
                client.sendEmailMessage(input)
            }
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(errorSendResponse)

        deferredResult.await()
    }

    @Test
    fun `sendEmailMessage() should not block coroutine cancellation exception`() = runBlocking<Unit> {

        mockS3Client.stub {
            onBlocking { upload(any(), anyString(), anyOrNull()) } doThrow CancellationException("mock")
        }

        val input = SendEmailMessageInput(
            "rfc822data".toByteArray(),
            "senderEmailAddress"
        )
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<CancellationException> {
                client.sendEmailMessage(input)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockS3Client).upload(any(), anyString(), anyOrNull())
    }
}
