/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.GetEmailConfigQuery
import com.sudoplatform.sudoemail.graphql.fragment.EmailConfigurationData
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
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
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.getConfigurationData]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetConfigurationDataTest : BaseTests() {

    private val queryResult by before {
        GetEmailConfigQuery.GetEmailConfig(
            "typeName",
            GetEmailConfigQuery.GetEmailConfig.Fragments(
                EmailConfigurationData(
                    "typename",
                    10,
                    5,
                    200,
                    100,
                    5,
                    10,
                ),
            ),
        )
    }

    private val response by before {
        Response.builder<GetEmailConfigQuery.Data>(GetEmailConfigQuery())
            .data(GetEmailConfigQuery.Data(queryResult))
            .build()
    }

    private var holder = CallbackHolder<GetEmailConfigQuery.Data>()

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<GetEmailConfigQuery>()) } doReturn holder.queryOperation
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val mockServiceKeyManager by before {
        DefaultServiceKeyManager(
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
            mockServiceKeyManager,
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
            mockServiceKeyManager,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
            "region",
            "identityBucket",
            "transientBucket",
            null,
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
    fun `getConfigurationData() should return results when no error present`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val deferredResult = async(Dispatchers.IO) {
            client.getConfigurationData()
        }
        deferredResult.start()

        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(response)

        val result = deferredResult.await()
        result shouldNotBe null

        with(result) {
            deleteEmailMessagesLimit shouldBe 10
            updateEmailMessagesLimit shouldBe 5
            emailMessageMaxInboundMessageSize shouldBe 200
            emailMessageMaxOutboundMessageSize shouldBe 100
            emailMessageRecipientsLimit shouldBe 5
            encryptedEmailMessageRecipientsLimit shouldBe 10
        }

        verify(mockAppSyncClient).query(any<GetEmailConfigQuery>())
    }

    @Test
    fun `getConfigurationData() should throw when unknown error occurs`() = runBlocking<Unit> {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<GetEmailConfigQuery>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailConfigurationException.UnknownException> {
                client.getConfigurationData()
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEmailConfigQuery>())
    }

    @Test
    fun `getConfigurationData() should throw when no config data is returned`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val noDataResponse by before {
            Response.builder<GetEmailConfigQuery.Data>(GetEmailConfigQuery())
                .data(null)
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailConfigurationException.FailedException> {
                client.getConfigurationData()
            }
        }
        deferredResult.start()
        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(noDataResponse)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEmailConfigQuery>())
    }

    @Test
    fun `getConfigurationData() should throw when query response contains errors`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val testError = com.apollographql.apollo.api.Error(
            "Test generated error",
            null,
            null,
        )
        val errorResponse by before {
            Response.builder<GetEmailConfigQuery.Data>(GetEmailConfigQuery())
                .errors(listOf(testError))
                .build()
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailConfigurationException.FailedException> {
                client.getConfigurationData()
            }
        }
        deferredResult.start()
        delay(100L)
        holder.callback shouldNotBe null
        holder.callback?.onResponse(errorResponse)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEmailConfigQuery>())
    }
}
