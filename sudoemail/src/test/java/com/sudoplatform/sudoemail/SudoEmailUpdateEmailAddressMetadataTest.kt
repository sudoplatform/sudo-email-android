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
import com.sudoplatform.sudoemail.graphql.UpdateEmailAddressMetadataMutation
import com.sudoplatform.sudoemail.graphql.type.EmailAddressMetadataUpdateValuesInput
import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailAddressMetadataInput
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.transformers.Unsealer
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
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
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException
import com.sudoplatform.sudoemail.types.inputs.UpdateEmailAddressMetadataInput as UpdateEmailAddressMetadataRequest

/**
 * Test the correct operation of [SudoEmailClient.updateEmailAddressMetadata]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailUpdateEmailAddressMetadataTest : BaseTests() {

    private val sealedAttributeInput by before {
        SealedAttributeInput.builder()
            .keyId("keyId")
            .algorithm("algorithm")
            .plainTextType("string")
            .base64EncodedSealedData("John Doe")
            .build()
    }

    private val updateValues by before {
        EmailAddressMetadataUpdateValuesInput.builder()
            .alias(sealedAttributeInput)
            .build()
    }

    private val input by before {
        UpdateEmailAddressMetadataInput.builder()
            .id("emailAddressId")
            .values(updateValues)
            .build()
    }

    private val mutationResponse by before {
        Response.builder<UpdateEmailAddressMetadataMutation.Data>(UpdateEmailAddressMetadataMutation(input))
            .data(UpdateEmailAddressMetadataMutation.Data("emailAddressId"))
            .build()
    }

    private val mutationHolder = CallbackHolder<UpdateEmailAddressMetadataMutation.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { mutate(any<UpdateEmailAddressMetadataMutation>()) } doReturn mutationHolder.mutationOperation
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val mockDeviceKeyManager by before {
        mock<DeviceKeyManager>().stub {
            on { getCurrentSymmetricKeyId() } doReturn "symmetricKeyId"
            on { encryptWithSymmetricKeyId(anyString(), any(), eq(null)) } doReturn ByteArray(42)
        }
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking { upload(any(), anyString(), anyOrNull()) } doReturn "42"
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
        mutationHolder.callback = null
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockContext, mockUserClient, mockKeyManager, mockAppSyncClient, mockS3Client)
    }

    @Test
    fun `updateEmailAddressMetadata() should return results when no error present`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val input = UpdateEmailAddressMetadataRequest(
            "emailAddressId",
            "John Doe",
        )
        val deferredResult = async(Dispatchers.IO) {
            client.updateEmailAddressMetadata(input)
        }
        deferredResult.start()

        delay(100L)
        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(mutationResponse)

        val result = deferredResult.await()
        result shouldBe "emailAddressId"

        verify(mockAppSyncClient).mutate(any<UpdateEmailAddressMetadataMutation>())
        verify(mockDeviceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `updateEmailAddressMetadata() should throw when response is null`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val nullResponse by before {
            Response.builder<UpdateEmailAddressMetadataMutation.Data>(UpdateEmailAddressMetadataMutation(input))
                .data(null)
                .build()
        }

        val input = UpdateEmailAddressMetadataRequest(
            "emailAddressId",
            "John Doe",
        )
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.UpdateFailedException> {
                client.updateEmailAddressMetadata(input)
            }
        }
        deferredResult.start()

        delay(100L)
        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onResponse(nullResponse)

        verify(mockAppSyncClient).mutate(any<UpdateEmailAddressMetadataMutation>())
        verify(mockDeviceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `updateEmailAddressMetadata() should throw when unsealing fails`() = runBlocking<Unit> {
        mockAppSyncClient.stub {
            on { mutate(any<UpdateEmailAddressMetadataMutation>()) } doThrow Unsealer.UnsealerException.UnsupportedAlgorithmException(
                "Mock Unsealer Exception",
            )
        }

        val input = UpdateEmailAddressMetadataRequest(
            "emailAddressId",
            "John Doe",
        )
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.UnsealingException> {
                client.updateEmailAddressMetadata(input)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<UpdateEmailAddressMetadataMutation>())
        verify(mockDeviceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `updateEmailAddressMetadata() should throw when http error occurs`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        val input = UpdateEmailAddressMetadataRequest(
            "emailAddressId",
            "John Doe",
        )
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.UpdateFailedException> {
                client.updateEmailAddressMetadata(input)
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

        mutationHolder.callback shouldNotBe null
        mutationHolder.callback?.onHttpError(ApolloHttpException(forbidden))

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<UpdateEmailAddressMetadataMutation>())
        verify(mockDeviceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `updateEmailAddressMetadata() should throw when unknown error occurs()`() = runBlocking<Unit> {
        mutationHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { mutate(any<UpdateEmailAddressMetadataMutation>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val input = UpdateEmailAddressMetadataRequest(
            "emailAddressId",
            "John Doe",
        )
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.UnknownException> {
                client.updateEmailAddressMetadata(input)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).mutate(any<UpdateEmailAddressMetadataMutation>())
        verify(mockDeviceKeyManager).getCurrentSymmetricKeyId()
    }

    @Test
    fun `updateEmailAddressMetadata() should not block coroutine cancellation exception`() = runBlocking<Unit> {
        mockDeviceKeyManager.stub {
            on { getCurrentSymmetricKeyId() } doThrow CancellationException("mock")
        }

        val input = UpdateEmailAddressMetadataRequest(
            "emailAddressId",
            "John Doe",
        )
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<CancellationException> {
                client.updateEmailAddressMetadata(input)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockDeviceKeyManager).getCurrentSymmetricKeyId()
    }
}
