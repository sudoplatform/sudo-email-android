/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.ObjectMetadata
import com.apollographql.apollo.api.Response
import com.sudoplatform.sudoemail.graphql.CallbackHolder
import com.sudoplatform.sudoemail.graphql.GetEmailAddressQuery
import com.sudoplatform.sudoemail.graphql.ListEmailAddressesQuery
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddress
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.graphql.fragment.EmailFolder
import com.sudoplatform.sudoemail.graphql.type.ListEmailAddressesInput
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.s3.types.S3ClientListOutput
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
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
import org.apache.commons.codec.binary.Base64
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.listDraftEmailMessages]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListDraftEmailMessagesTest : BaseTests() {

    private fun mockSeal(value: String): String {
        val valueBytes = value.toByteArray()
        val data = ByteArray(256)
        valueBytes.copyInto(data)
        return Base64.encodeBase64String(data)
    }

    private val unsealedHeaderDetailsString =
        "{\"bcc\":[],\"to\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"from\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"cc\":" +
            "[{\"emailAddress\":\"foobar@unittest.org\"}],\"replyTo\":[{\"emailAddress\":\"foobar@unittest.org\"}],\"subject\":" +
            "\"testSubject\",\"hasAttachments\":false}"

    private val mockUserMetadata = listOf(
        "keyId" to "keyId",
        "algorithm" to "algorithm",
    ).toMap()

    private val mockS3ObjectMetadata = ObjectMetadata()

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
                            0,
                            null,
                        ),
                    ),
                ),
            ),
        )
    }

    private val input by before {
        ListEmailAddressesInput.builder()
            .limit(null)
            .nextToken(null)
            .build()
    }

    private val mockEmailAddressIdInput by before {
        "emailAddressId"
    }

    private val listEmailAddressesQueryResponse by before {
        Response.builder<ListEmailAddressesQuery.Data>(ListEmailAddressesQuery(input))
            .data(ListEmailAddressesQuery.Data(listEmailAddressesResult))
            .build()
    }

    private val getEmailAddressQueryResponse by before {
        Response.builder<GetEmailAddressQuery.Data>(GetEmailAddressQuery(mockEmailAddressIdInput))
            .data(GetEmailAddressQuery.Data(emailAddressResult))
            .build()
    }

    private val listEmailHolder = CallbackHolder<ListEmailAddressesQuery.Data>()
    private val getEmailHolder = CallbackHolder<GetEmailAddressQuery.Data>()

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
            on { decryptWithPrivateKey(anyString(), any(), any()) } doReturn ByteArray(42)
            on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn unsealedHeaderDetailsString.toByteArray()
        }
    }

    private val mockDeviceKeyManager by before {
        DefaultDeviceKeyManager("keyRingServiceName", mockUserClient, mockKeyManager, mockLogger)
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<ListEmailAddressesQuery>()) } doReturn listEmailHolder.queryOperation
            on { query(any<GetEmailAddressQuery>()) } doReturn getEmailHolder.queryOperation
        }
    }

    private val timestamp by before {
        Date()
    }

    private val mockDownloadResponse by before {
        mockSeal("42").toByteArray(Charsets.UTF_8)
    }

    private val mockListObjectsResponse: List<S3ClientListOutput> by before {
        listOf(S3ClientListOutput("id1", Date()), S3ClientListOutput("id2", Date()))
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking {
                download(any())
            } doReturn mockDownloadResponse
            onBlocking {
                getObjectMetadata(any())
            } doReturn mockS3ObjectMetadata
            onBlocking {
                list(any(), any())
            } doReturn mockListObjectsResponse
        }
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on { unsealString(any(), any()) } doReturn unsealedHeaderDetailsString.toByteArray()
        }
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
        listEmailHolder.callback = null
        getEmailHolder.callback = null
        mockS3ObjectMetadata.lastModified = timestamp
        mockS3ObjectMetadata.userMetadata = mockUserMetadata
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockUserClient,
            mockKeyManager,
            mockAppSyncClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `listDraftEmailMessages() should return results when no error present`() = runBlocking<Unit> {
        listEmailHolder.callback shouldBe null
        getEmailHolder.callback shouldBe null

        val emailAddressId = "emailAddressId"

        val deferredResult = async(Dispatchers.IO) {
            client.listDraftEmailMessages()
        }
        deferredResult.start()

        delay(100L)
        listEmailHolder.callback shouldNotBe null
        listEmailHolder.callback?.onResponse(listEmailAddressesQueryResponse)

        delay(100L)
        getEmailHolder.callback shouldNotBe null
        getEmailHolder.callback?.onResponse(getEmailAddressQueryResponse)

        val result = deferredResult.await()

        result.size shouldBe 2
        result[0].id shouldBe "id1"
        result[0].updatedAt.time shouldBe timestamp.time
        result[0].rfc822Data shouldNotBe null
        result[1].id shouldBe "id2"
        result[1].updatedAt.time shouldBe timestamp.time
        result[1].rfc822Data shouldNotBe null

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
        verify(mockS3Client, times(2)).getObjectMetadata(anyString())
        verify(mockS3Client, times(2)).download(anyString())
        verify(mockS3Client).list(
            check {
                it shouldBe "transientBucket"
            },
            check {
                it shouldContain emailAddressId
            },
        )
        verify(mockSealingService, times(2)).unsealString(anyString(), any())
    }

    @Test
    fun `listDraftEmailMessages() should throw an error if an unknown error occurs`() = runBlocking<Unit> {
        listEmailHolder.callback shouldBe null

        mockAppSyncClient.stub {
            on { query(any<ListEmailAddressesQuery>()) } doThrow RuntimeException("Mock Runtime Exception")
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.listDraftEmailMessages()
            }
        }
        deferredResult.start()

        delay(100L)
        deferredResult.await()

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
    }

    @Test
    fun `listDraftEmailMessages() should return an empty list if no addresses found for user`() = runBlocking<Unit> {
        listEmailHolder.callback shouldBe null

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

        val deferredResult = async(Dispatchers.IO) {
            client.listDraftEmailMessages()
        }
        deferredResult.start()
        delay(100L)

        listEmailHolder.callback shouldNotBe null
        listEmailHolder.callback?.onResponse(responseWithEmptyList)

        val result = deferredResult.await()
        result shouldNotBe null
        result.size shouldBe 0

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
    }

    @Test
    fun `listDraftEmailMessages() should return an empty list if no drafts found`() = runBlocking<Unit> {
        listEmailHolder.callback shouldBe null
        getEmailHolder.callback shouldBe null

        val emailAddressId = "emailAddressId"

        mockS3Client.stub {
            onBlocking {
                list(anyString(), anyString())
            } doReturn emptyList()
        }

        val deferredResult = async(Dispatchers.IO) {
            client.listDraftEmailMessages()
        }
        deferredResult.start()

        delay(100L)
        listEmailHolder.callback shouldNotBe null
        listEmailHolder.callback?.onResponse(listEmailAddressesQueryResponse)

        delay(100L)
        getEmailHolder.callback shouldNotBe null
        getEmailHolder.callback?.onResponse(getEmailAddressQueryResponse)

        val result = deferredResult.await()
        result.size shouldBe 0

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
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
    fun `listDraftEmailMessages() should throw an error if draft message is not found`() = runBlocking<Unit> {
        listEmailHolder.callback shouldBe null
        getEmailHolder.callback shouldBe null

        val emailAddressId = "emailAddressId"

        val error = AmazonS3Exception("Not found")
        error.errorCode = "404 Not Found"
        mockS3Client.stub {
            onBlocking {
                download(anyString())
            } doThrow error
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                client.listDraftEmailMessages()
            }
        }

        deferredResult.start()

        delay(100L)
        listEmailHolder.callback shouldNotBe null
        listEmailHolder.callback?.onResponse(listEmailAddressesQueryResponse)

        delay(100L)
        getEmailHolder.callback shouldNotBe null
        getEmailHolder.callback?.onResponse(getEmailAddressQueryResponse)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<ListEmailAddressesQuery>())
        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
        verify(mockS3Client).getObjectMetadata(anyString())
        verify(mockS3Client).download(anyString())
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
