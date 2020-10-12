/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.DeleteEmailMessageMutation
import com.sudoplatform.sudoemail.graphql.type.DeleteEmailMessageInput
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.keys.DefaultPublicKeyService
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudoprofiles.S3Client
import com.sudoplatform.sudoprofiles.Sudo
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.deleteEmailMessage] using mocks and spies.
 *
 * @since 2020-08-19
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailDeleteEmailMessageTest : BaseTests() {

    private val input by before {
        DeleteEmailMessageInput.builder()
            .messageId("id")
            .build()
    }

    private val mutationResult by before {
        "deleteEmailMessage"
    }

    private val mutationResponse by before {
        Response.builder<DeleteEmailMessageMutation.Data>(DeleteEmailMessageMutation(input))
            .data(DeleteEmailMessageMutation.Data(mutationResult))
            .build()
    }

    private val holder = CallbackHolder<DeleteEmailMessageMutation.Data>()

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "subject"
            on { getRefreshToken() } doReturn "refreshToken"
        }
    }

    private val mockSudoClient by before {
        mock<SudoProfilesClient>().stub {
            onBlocking { getOwnershipProof(any<Sudo>(), anyString()) } doReturn "jwt"
        }
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<DeleteEmailMessageMutation>()) } doReturn holder.mutationOperation
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
            context,
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
        mock<S3Client>().stub {
            onBlocking { upload(any(), anyString()) } doReturn "42"
        }
    }

    private val client by before {
        DefaultSudoEmailClient(
            context,
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

    @Before
    fun init() {
        holder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockUserClient, mockSudoClient, mockKeyManager, mockAppSyncClient, mockS3Client)
    }

    @Test
    fun `deleteEmailMessage() should return results when no error present`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.deleteEmailMessage("id")
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(mutationResponse)

        val result = deferredResult.await()
        result shouldNotBe null
        result.isBlank() shouldBe false

        verify(mockAppSyncClient).mutate(any<DeleteEmailMessageMutation>())
    }

    @Test
    fun `deleteEmailMessage() should throw when email mutation response is null`() = runBlocking<Unit> {

        holder.callback shouldBe null

        val nullProvisionResponse by before {
            Response.builder<DeleteEmailMessageMutation.Data>(DeleteEmailMessageMutation(input))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                client.deleteEmailMessage("id")
            }
        }
        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(nullProvisionResponse)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeleteEmailMessageMutation>())
    }

    @Test
    fun `deleteEmailMessage() should throw when response has various errors`() = runBlocking<Unit> {
        testException<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException>("EmailMessageNotFound")
        testException<SudoEmailClient.EmailMessageException.FailedException>("blah")

        verify(mockAppSyncClient, times(2)).mutate(any<DeleteEmailMessageMutation>())
    }

    private inline fun <reified T : Exception> testException(apolloError: String) = runBlocking<Unit> {

        holder.callback = null

        val errorSendResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to apolloError)
            )
            Response.builder<DeleteEmailMessageMutation.Data>(DeleteEmailMessageMutation(input))
                .errors(listOf(error))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<T> {
                client.deleteEmailMessage("id")
            }
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(errorSendResponse)

        deferredResult.await()
    }
}
