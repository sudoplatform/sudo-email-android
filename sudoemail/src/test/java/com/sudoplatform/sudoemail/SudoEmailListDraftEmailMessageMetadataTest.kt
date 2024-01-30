/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.api.Response
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.GetEmailAddressQuery
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddress
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.graphql.fragment.EmailFolder
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.s3.types.S3ClientListOutput
import com.sudoplatform.sudoemail.sealing.DefaultSealingService
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.matchers.string.shouldContain
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
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.listDraftEmailMessageMetadata]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListDraftEmailMessageMetadataTest : BaseTests() {

    private val owners by before {
        listOf(EmailAddressWithoutFolders.Owner("typename", "ownerId", "issuer"))
    }

    private val folderOwners by before {
        listOf(EmailFolder.Owner("typename", "ownerId", "issuer"))
    }

    private val folders by before {
        listOf(
            EmailAddress.Folder(
                "typename",
                EmailAddress.Folder.Fragments(
                    EmailFolder(
                        "EmailFolder",
                        "folderId",
                        "owner",
                        folderOwners,
                        1,
                        0.0,
                        0.0,
                        "emailAddressId",
                        "folderName",
                        1.0,
                        1.0,
                        1.0,
                    ),
                ),
            ),
        )
    }

    private val emailAddressResult by before {
        GetEmailAddressQuery.GetEmailAddress(
            "typename",
            GetEmailAddressQuery.GetEmailAddress.Fragments(
                EmailAddress(
                    "typename",
                    folders,
                    EmailAddress.Fragments(
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
                            null,
                        ),
                    ),
                ),
            ),
        )
    }

    private val input by before {
        "emailAddressId"
    }

    private val emailAddressQueryResponse by before {
        Response.builder<GetEmailAddressQuery.Data>(GetEmailAddressQuery(input))
            .data(GetEmailAddressQuery.Data(emailAddressResult))
            .build()
    }

    private val holder = CallbackHolder<GetEmailAddressQuery.Data>()

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { encryptWithSymmetricKey(anyString(), any()) } doReturn ByteArray(42)
        }
    }

    private val mockDeviceKeyManager by before {
        DefaultDeviceKeyManager("keyRingServiceName", mockUserClient, mockKeyManager, mockLogger)
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<GetEmailAddressQuery>()) } doReturn holder.queryOperation
        }
    }

    private val mockListObjectsResponse: List<S3ClientListOutput> by before {
        listOf(S3ClientListOutput("key1", Date()), S3ClientListOutput("key2", Date()))
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking {
                list(
                    any(),
                    any(),
                )
            } doReturn mockListObjectsResponse
        }
    }

    private val mockSealingService by before {
        DefaultSealingService(mockDeviceKeyManager, mockLogger)
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
            mockS3Client,
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
    fun `listDraftEmailMessageMetadata() should log and throw an error if send address not found`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val error = com.apollographql.apollo.api.Error(
            "mock",
            emptyList(),
            mapOf("errorType" to "AddressNotFound"),
        )

        val mockQuery by before {
            GetEmailAddressQuery(input)
        }

        val nullEmailResponse by before {
            Response.builder<GetEmailAddressQuery.Data>(mockQuery)
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val emailAddressId = "emailAddressId"

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                client.listDraftEmailMessageMetadata(emailAddressId)
            }
        }
        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(nullEmailResponse)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
    }

    @Test
    fun `listDraftEmailMessageMetadata() should return an empty list if no drafts found`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val emailAddressId = "emailAddressId"

        mockS3Client.stub {
            onBlocking {
                list(anyString(), anyString())
            } doReturn emptyList()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.listDraftEmailMessageMetadata(emailAddressId)
        }

        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(emailAddressQueryResponse)

        val result = deferredResult.await()
        result.size shouldBe 0

        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
        verify(mockS3Client).list(
            check {
                it shouldBe "transientBucket"
            },
            check {
                it shouldContain emailAddressId
            },
        )
    }

    @Test
    fun `listDraftEmailMessageMetadata() should return a list of metadata for the email address`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val emailAddressId = "emailAddressId"

        val deferredResult = async(Dispatchers.IO) {
            client.listDraftEmailMessageMetadata(emailAddressId)
        }

        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(emailAddressQueryResponse)

        val result = deferredResult.await()
        result.size shouldBe 2
        result[0].id shouldBe mockListObjectsResponse[0].key
        result[1].id shouldBe mockListObjectsResponse[1].key

        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
        verify(mockS3Client).list(
            check {
                it shouldBe "transientBucket"
            },
            check {
                it shouldContain emailAddressId
            },
        )
    }
}
