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
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.inputs.CreateDraftEmailMessageInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.matchers.string.shouldMatch
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.CancellationException
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
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.createDraftEmailMessage]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailCreateDraftEmailTest : BaseTests() {
    private val uuidRegex = Regex("^[0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12}\$")

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
                            0,
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

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { getCurrentSymmetricKeyId() } doReturn "symmetricKeyId"
            on { generateNewCurrentSymmetricKey() } doReturn "newSymmetricKeyId"
        }
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<GetEmailAddressQuery>()) } doReturn holder.queryOperation
        }
    }

    private val mockUploadResponse by before {
        "42"
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking {
                upload(
                    any(),
                    any(),
                    anyOrNull(),
                )
            } doReturn mockUploadResponse
        }
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        mock<SealingService>().stub {
            on { sealString(any(), any()) } doReturn "sealString".toByteArray()
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
            mockServiceKeyManager,
            mockAppSyncClient,
            mockS3Client,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `createDraftEmailMessage() should log and throw an error if send address not found`() = runBlocking<Unit> {
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

        val createDraftInput = CreateDraftEmailMessageInput(
            "rfc822data".toByteArray(),
            "senderEmailAddressId",
        )

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                client.createDraftEmailMessage(createDraftInput)
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
    fun `createDraftEmailMessage() should log and throw an error if s3 upload errors`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val error = CancellationException("Unknown exception")

        mockS3Client.stub {
            onBlocking {
                upload(
                    any(),
                    any(),
                    anyOrNull(),
                )
            } doThrow error
        }

        val createDraftInput = CreateDraftEmailMessageInput(
            "rfc822data".toByteArray(),
            "senderEmailAddressId",
        )

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<CancellationException> {
                client.createDraftEmailMessage(createDraftInput)
            }
        }

        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(emailAddressQueryResponse)

        deferredResult.await()

        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        verify(mockSealingService).sealString(
            check {
                it shouldBe "symmetricKeyId"
            },
            check {
                it shouldBe "rfc822data".toByteArray()
            },
        )
        verify(mockS3Client).upload(
            check {
                it shouldNotBe null
            },
            check {
                it shouldContain createDraftInput.senderEmailAddressId
            },
            check {
                it shouldNotBe null
            },
        )
    }

    @Test
    fun `createDraftEmailMessage() should return uuid on success`() = runBlocking<Unit> {
        holder.callback shouldBe null

        val createDraftInput = CreateDraftEmailMessageInput(
            "rfc822data".toByteArray(),
            "senderEmailAddressId",
        )

        val deferredResult = async(Dispatchers.IO) {
            client.createDraftEmailMessage(createDraftInput)
        }

        deferredResult.start()
        delay(100L)

        holder.callback shouldNotBe null
        holder.callback?.onResponse(emailAddressQueryResponse)

        val result = deferredResult.await()
        result shouldMatch uuidRegex

        verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
        verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        verify(mockSealingService).sealString("symmetricKeyId", createDraftInput.rfc822Data)
        verify(mockS3Client).upload(
            check {
                it shouldNotBe null
            },
            check {
                it shouldContain createDraftInput.senderEmailAddressId
            },
            check {
                it shouldNotBe null
            },
        )
    }
}
