/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloHttpException
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.DeprovisionEmailAddressMutation
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.graphql.type.DeprovisionEmailAddressInput
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.net.HttpURLConnection
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.deprovisionEmailAddress]
 * using mocks and spies.
 */
class SudoEmailDeprovisionEmailAddressTest : BaseTests() {

    private val owners by before {
        listOf(EmailAddressWithoutFolders.Owner("typename", "ownerId", "issuer"))
    }

    private val mutationInput = DeprovisionEmailAddressInput.builder()
        .emailAddressId("emailAddressId")
        .build()

    private val mutationResult by before {
        DeprovisionEmailAddressMutation.DeprovisionEmailAddress(
            "typename",
            DeprovisionEmailAddressMutation.DeprovisionEmailAddress.Fragments(
                EmailAddressWithoutFolders(
                    "typename",
                    "emailAddressId",
                    "owner",
                    owners,
                    "identityId",
                    "keyRingId",
                    emptyList(),
                    1,
                    1.0,
                    1.0,
                    1.0,
                    "example@sudoplatform.com",
                    0.0,
                    0,
                    null,
                ),
            ),
        )
    }

    private val response by before {
        Response.builder<DeprovisionEmailAddressMutation.Data>(DeprovisionEmailAddressMutation(mutationInput))
            .data(DeprovisionEmailAddressMutation.Data(mutationResult))
            .build()
    }

    private val holder = CallbackHolder<DeprovisionEmailAddressMutation.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<DeprovisionEmailAddressMutation>()) } doReturn holder.mutationOperation
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val mockDeviceKeyManager by before {
        DefaultDeviceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger,
        )
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking { upload(any(), ArgumentMatchers.anyString(), anyOrNull()) } doReturn "42"
        }
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        DefaultSealingService(
            mockDeviceKeyManager,
            mockLogger,
        )
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>()
    }

    private val client by before {
        DefaultSudoEmailClient(
            mockContext,
            mockAppSyncClient,
            mockUserClient,
            mockLogger,
            mockDeviceKeyManager,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
            "region",
            "identityBucket",
            "transientBucket",
            mockS3Client,
            mockS3Client,
        )
    }

    @Before
    fun init() {
        holder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockContext,
            mockUserClient,
            mockKeyManager,
            mockAppSyncClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `deprovisionEmailAddress() should return results when no error present`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.deprovisionEmailAddress("emailAddressId")
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(response)

        val result = deferredResult.await()
        result shouldNotBe null

        with(result) {
            id shouldBe "emailAddressId"
            owner shouldBe "owner"
            owners.first().id shouldBe "ownerId"
            owners.first().issuer shouldBe "issuer"
            emailAddress shouldBe "example@sudoplatform.com"
            size shouldBe 0.0
            numberOfEmailMessages shouldBe 0
            version shouldBe 1
            createdAt shouldBe Date(1L)
            updatedAt shouldBe Date(1L)
            lastReceivedAt shouldBe Date(1L)
            alias shouldBe null
        }

        verify(mockAppSyncClient).mutate(any<DeprovisionEmailAddressMutation>())
    }

    @Test
    fun `deprovisionEmailAddress() should throw when mutation response is null`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val nullResponse by before {
            Response.builder<DeprovisionEmailAddressMutation.Data>(DeprovisionEmailAddressMutation(mutationInput))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.DeprovisionFailedException> {
                client.deprovisionEmailAddress("emailAddressId")
            }
        }
        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(nullResponse)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeprovisionEmailAddressMutation>())
    }

    @Test
    fun `deprovisionEmailAddress() should throw when response has an email address not found error`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val errorDeprovisionResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "AddressNotFound"),
            )
            Response.builder<DeprovisionEmailAddressMutation.Data>(DeprovisionEmailAddressMutation(mutationInput))
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                client.deprovisionEmailAddress("emailAddressId")
            }
        }
        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(errorDeprovisionResponse)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeprovisionEmailAddressMutation>())
    }

    @Test
    fun `deprovisionEmailAddress() should throw when response has an unauthorized address error`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val errorDeprovisionResponse by before {
            val error = com.apollographql.apollo.api.Error(
                "mock",
                emptyList(),
                mapOf("errorType" to "UnauthorizedAddress"),
            )
            Response.builder<DeprovisionEmailAddressMutation.Data>(DeprovisionEmailAddressMutation(mutationInput))
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.UnauthorizedEmailAddressException> {
                client.deprovisionEmailAddress("emailAddressId")
            }
        }
        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(errorDeprovisionResponse)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeprovisionEmailAddressMutation>())
    }

    @Test
    fun `deprovisionEmailAddress() should throw when http error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.DeprovisionFailedException> {
                client.deprovisionEmailAddress("emailAddressId")
            }
        }
        deferredResult.start()
        delay(100L)

        val request = okhttp3.Request.Builder()
            .get()
            .url("http://www.smh.com.au")
            .build()
        val responseBody = "{}".toResponseBody("application/json; charset=utf-8".toMediaType())
        val forbidden = okhttp3.Response.Builder()
            .protocol(Protocol.HTTP_1_1)
            .code(HttpURLConnection.HTTP_FORBIDDEN)
            .request(request)
            .message("Forbidden")
            .body(responseBody)
            .build()

        holder.callback shouldNotBe null
        holder.callback?.onHttpError(ApolloHttpException(forbidden))

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeprovisionEmailAddressMutation>())
    }

    @Test
    fun `deprovisionEmailAddress() should throw when unknown error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<DeprovisionEmailAddressMutation>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.UnknownException> {
                client.deprovisionEmailAddress("emailAddressId")
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeprovisionEmailAddressMutation>())
    }

    @Test
    fun `deprovisionEmailAddress() should not suppress CancellationException`() = runBlocking<Unit> {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<DeprovisionEmailAddressMutation>()) } doThrow CancellationException("Mock Cancellation Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<CancellationException> {
                client.deprovisionEmailAddress("emailAddressId")
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<DeprovisionEmailAddressMutation>())
    }
}
