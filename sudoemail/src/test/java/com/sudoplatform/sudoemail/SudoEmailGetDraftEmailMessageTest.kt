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
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddress
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.graphql.fragment.EmailFolder
import com.sudoplatform.sudoemail.keys.DefaultDeviceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.sealing.SealingService
import com.sudoplatform.sudoemail.types.inputs.GetDraftEmailMessageInput
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date
import java.util.UUID

/**
 * Test the correct operation of [SudoEmailClient.getDraftEmailMessage]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailGetDraftEmailMessageTest : BaseTests() {
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

    private val mockEmailAddressIdInput by before {
        "emailAddressId"
    }

    private val emailAddressQueryResponse by before {
        Response.builder<GetEmailAddressQuery.Data>(GetEmailAddressQuery(mockEmailAddressIdInput))
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
            on { decryptWithPrivateKey(anyString(), any(), any()) } doReturn ByteArray(42)
            on { decryptWithSymmetricKey(any<ByteArray>(), any<ByteArray>()) } doReturn unsealedHeaderDetailsString.toByteArray()
        }
    }

    private val mockDeviceKeyManager by before {
        DefaultDeviceKeyManager(
            "keyRingService",
            mockUserClient,
            mockKeyManager,
            mockLogger,
        )
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<GetEmailAddressQuery>()) } doReturn holder.queryOperation
        }
    }

    private val timestamp by before {
        Date()
    }

    private val mockUploadResponse by before {
        "42"
    }

    private val mockDownloadResponse by before {
        mockSeal("42").toByteArray(Charsets.UTF_8)
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking {
                upload(any(), any(), anyOrNull())
            } doReturn mockUploadResponse
            onBlocking {
                download(any())
            } doReturn mockDownloadResponse
            onBlocking {
                getObjectMetadata(any())
            } doReturn mockS3ObjectMetadata
        }
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on { unsealString(any(), any()) } doReturn unsealedHeaderDetailsString.toByteArray()
        }
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
        mockS3ObjectMetadata.lastModified = timestamp
        mockS3ObjectMetadata.userMetadata = mockUserMetadata
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockUserClient, mockKeyManager, mockAppSyncClient, mockS3Client)
    }

    @Test
    fun `getDraftEmailMessage() should log and throw an error if sender address not found`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val error = com.apollographql.apollo.api.Error(
            "mock",
            emptyList(),
            mapOf("errorType" to "AddressNotFound"),
        )

        val mockQuery by before {
            GetEmailAddressQuery(mockEmailAddressIdInput)
        }

        val nullEmailResponse by before {
            Response.builder<GetEmailAddressQuery.Data>(mockQuery)
                .errors(listOf(error))
                .data(null)
                .build()
        }

        val mockDraftId = UUID.randomUUID()
        val input = GetDraftEmailMessageInput(mockDraftId.toString(), mockEmailAddressIdInput)
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                client.getDraftEmailMessage(input)
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
    fun `getDraftEmailMessage() should log and throw an error if draft message is not found`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val error = AmazonS3Exception("Not found")
        error.errorCode = "404 Not Found"
        mockS3Client.stub {
            onBlocking {
                download(anyString())
            } doThrow error
        }

        val mockDraftId = UUID.randomUUID()
        val input = GetDraftEmailMessageInput(mockDraftId.toString(), mockEmailAddressIdInput)
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                client.getDraftEmailMessage(input)
            }
        }

        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(emailAddressQueryResponse)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
        verify(mockS3Client).getObjectMetadata(
            check {
                it shouldContain mockDraftId.toString()
            },
        )
        verify(mockS3Client).download(
            check {
                it shouldContain mockDraftId.toString()
            },
        )
    }

    @Test
    fun `getDraftEmailMessage() should throw error if no keyId is found in s3Object`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val mockBadObjectUserMetadata = listOf("algorithm" to "algorithm").toMap()
        val mockBadObjectMetadata = ObjectMetadata()
        mockBadObjectMetadata.lastModified = timestamp
        mockBadObjectMetadata.userMetadata = mockBadObjectUserMetadata

        mockS3Client.stub {
            onBlocking {
                getObjectMetadata(any())
            } doReturn mockBadObjectMetadata
        }

        val mockDraftId = UUID.randomUUID()
        val input = GetDraftEmailMessageInput(mockDraftId.toString(), mockEmailAddressIdInput)
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                client.getDraftEmailMessage(input)
            }
        }

        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(emailAddressQueryResponse)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
        verify(mockS3Client).getObjectMetadata(
            check {
                it shouldContain mockDraftId.toString()
            },
        )
    }

    @Test
    fun `getDraftEmailMessage() should return proper data if no errors`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val mockDraftId = UUID.randomUUID()
        val input = GetDraftEmailMessageInput(mockDraftId.toString(), mockEmailAddressIdInput)
        val deferredResult = async(Dispatchers.IO) {
            client.getDraftEmailMessage(input)
        }

        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(emailAddressQueryResponse)

        val result = deferredResult.await()
        result.id shouldBe mockDraftId.toString()
        result.updatedAt shouldBe timestamp

        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
        verify(mockS3Client).getObjectMetadata(
            check {
                it shouldContain mockDraftId.toString()
            },
        )
        verify(mockS3Client).download(
            check {
                it shouldContain mockDraftId.toString()
            },
        )
        verify(mockSealingService).unsealString(anyString(), any())
    }
}
