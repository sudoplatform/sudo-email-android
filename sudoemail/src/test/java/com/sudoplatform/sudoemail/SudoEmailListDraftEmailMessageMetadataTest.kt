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
import com.sudoplatform.sudoemail.graphql.ListEmailAddressesQuery
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddress
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.graphql.fragment.EmailFolder
import com.sudoplatform.sudoemail.graphql.type.ListEmailAddressesInput
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.s3.types.S3ClientListOutput
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
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
                        1.0,
                        1.0,
                        "emailAddressId",
                        "folderName",
                        0.0,
                        0.0,
                        1.0,
                    ),
                ),
            ),
        )
    }

    private val listEmailAddressesResult by before {
        ListEmailAddressesQuery.ListEmailAddresses(
            "typename",
            listOf(
                ListEmailAddressesQuery.Item(
                    "typename",
                    ListEmailAddressesQuery.Item.Fragments(
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
                                    0,
                                    null,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            null,
        )
    }

    private val input by before {
        ListEmailAddressesInput.builder()
            .limit(null)
            .nextToken(null)
            .build()
    }

    private val listEmailAddressesQueryResponse by before {
        Response.builder<ListEmailAddressesQuery.Data>(ListEmailAddressesQuery(input))
            .data(ListEmailAddressesQuery.Data(listEmailAddressesResult))
            .build()
    }

    private val holder = CallbackHolder<ListEmailAddressesQuery.Data>()

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

    private val mockServiceKeyManager by before {
        DefaultServiceKeyManager("keyRingServiceName", mockUserClient, mockKeyManager, mockLogger)
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<ListEmailAddressesQuery>()) } doReturn holder.queryOperation
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

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        DefaultSealingService(mockServiceKeyManager, mockLogger)
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>()
    }

    private val client by before {
        DefaultSudoEmailClient(
            context,
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
            mockUserClient,
            mockKeyManager,
            mockAppSyncClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `listDraftEmailMessageMetadata() should throw an error if an unknown error occurs`() = runTest {
        holder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<ListEmailAddressesQuery>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.listDraftEmailMessageMetadata()
            }
        }
        deferredResult.start()

        delay(100L)
        deferredResult.await()

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
    }

    @Test
    fun `listDraftEmailMessageMetadata() should return an empty list if no addresses found for user`() = runTest {
        holder.callback shouldBe null

        val queryResultWithEmptyList by before {
            ListEmailAddressesQuery.ListEmailAddresses(
                "typename",
                emptyList(),
                null,
            )
        }

        val responseWithEmptyList by before {
            Response.builder<ListEmailAddressesQuery.Data>(ListEmailAddressesQuery(input))
                .data(ListEmailAddressesQuery.Data(queryResultWithEmptyList))
                .build()
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listDraftEmailMessageMetadata()
        }
        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(responseWithEmptyList)

        val result = deferredResult.await()
        result shouldNotBe null
        result.size shouldBe 0

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
    }

    @Test
    fun `listDraftEmailMessageMetadata() should return an empty list if no drafts found`() = runTest {
        holder.callback shouldBe null

        val emailAddressId = "emailAddressId"

        mockS3Client.stub {
            onBlocking {
                list(anyString(), anyString())
            } doReturn emptyList()
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listDraftEmailMessageMetadata()
        }

        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(listEmailAddressesQueryResponse)

        val result = deferredResult.await()
        result.size shouldBe 0

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
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
    fun `listDraftEmailMessageMetadata() should return a list of metadata for the user`() = runTest {
        holder.callback shouldBe null

        val emailAddressId = "emailAddressId"

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listDraftEmailMessageMetadata()
        }

        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(listEmailAddressesQueryResponse)

        val result = deferredResult.await()
        result.size shouldBe 2
        result[0].id shouldBe mockListObjectsResponse[0].key
        result[0].emailAddressId shouldBe emailAddressId
        result[1].id shouldBe mockListObjectsResponse[1].key
        result[1].emailAddressId shouldBe emailAddressId

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
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
