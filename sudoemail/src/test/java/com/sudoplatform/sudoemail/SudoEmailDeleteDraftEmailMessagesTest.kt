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
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.s3.S3Exception
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.types.BatchOperationStatus
import com.sudoplatform.sudoemail.types.EmailMessageOperationFailureResult
import com.sudoplatform.sudoemail.types.inputs.DeleteDraftEmailMessagesInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.inspectors.forAtLeastOne
import io.kotlintest.matchers.collections.shouldBeEmpty
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.collections.shouldHaveSize
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.deleteDraftEmailMessages]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailDeleteDraftEmailMessagesTest : BaseTests() {

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
        DefaultServiceKeyManager("keyRingServiceName", mockUserClient, mockKeyManager, mockLogger)
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
            on { query(any<GetEmailAddressQuery>()) } doReturn holder.queryOperation
        }
    }

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking {
                delete(any())
            } doReturn Unit
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
    fun `deleteDraftEmailMessages() should throw an error if email address not found`() =
        runTest {
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

            val draftIds = listOf("mock-draft-id-1", "mock-draft-id-2")
            val emailAddressId = "mock-email-address-id"
            val deleteDraftEmailMessagesInput =
                DeleteDraftEmailMessagesInput(draftIds, emailAddressId)

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                    client.deleteDraftEmailMessages(deleteDraftEmailMessagesInput)
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
    fun `deleteDraftEmailMessages() should return success result if all operations succeeded`() =
        runTest {
            holder.callback shouldBe null

            val draftIds = listOf("mock-draft-id-1", "mock-draft-id-2")
            val emailAddressId = "mock-email-address-id"
            val deleteDraftEmailMessagesInput =
                DeleteDraftEmailMessagesInput(draftIds, emailAddressId)

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.deleteDraftEmailMessages(deleteDraftEmailMessagesInput)
            }

            deferredResult.start()
            delay(100L)

            holder.callback shouldNotBe null
            holder.callback?.onResponse(emailAddressQueryResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            result.status shouldBe BatchOperationStatus.SUCCESS

            verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
            // S3 client delete method is called once per draft id
            verify(mockS3Client).delete(
                check {
                    it shouldContain "$emailAddressId/draft/${draftIds[0]}"
                },
            )
            verify(mockS3Client).delete(
                check {
                    it shouldContain "$emailAddressId/draft/${draftIds[1]}"
                },
            )
        }

    @Test
    fun `deleteDraftEmailMessages() should return partial result if some operations failed`() =
        runTest {
            holder.callback shouldBe null

            val draftIds = listOf("mock-success-draft-id", "mock-failure-draft-id")
            val emailAddressId = "mock-email-address-id"
            val deleteDraftEmailMessagesInput =
                DeleteDraftEmailMessagesInput(draftIds, emailAddressId)

            // Throw an exception from internal S3 client to provoke failure
            whenever(
                mockS3Client.delete(
                    check {
                        it shouldContain "$emailAddressId/draft/${draftIds[1]}"
                    },
                ),
            ).thenThrow(S3Exception.DeleteException("S3 delete failed"))

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.deleteDraftEmailMessages(deleteDraftEmailMessagesInput)
            }

            deferredResult.start()
            delay(100L)

            holder.callback shouldNotBe null
            holder.callback?.onResponse(emailAddressQueryResponse)

            val result = deferredResult.await()
            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.PARTIAL
            result.successValues?.shouldContain(draftIds[0])
            result.failureValues?.shouldHaveSize(1)
            result.failureValues?.first() shouldBe EmailMessageOperationFailureResult(
                draftIds[1],
                "S3 delete failed",
            )

            verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
            // S3 client delete method is called once per draft id
            verify(mockS3Client).delete(
                check {
                    it shouldContain "$emailAddressId/draft/${draftIds[0]}"
                },
            )
            verify(mockS3Client).delete(
                check {
                    it shouldContain "$emailAddressId/draft/${draftIds[1]}"
                },
            )
        }

    @Test
    fun `deleteDraftEmailMessages() should return failure result if all operations failed`() =
        runTest {
            holder.callback shouldBe null

            val draftIds = listOf("mock-success-draft-id", "mock-failure-draft-id")
            val emailAddressId = "mock-email-address-id"
            val deleteDraftEmailMessagesInput =
                DeleteDraftEmailMessagesInput(draftIds, emailAddressId)

            // Throw an exception from internal S3 client to provoke failure
            whenever(mockS3Client.delete(any()))
                .thenThrow(S3Exception.DeleteException("S3 delete failed"))

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.deleteDraftEmailMessages(deleteDraftEmailMessagesInput)
            }

            deferredResult.start()
            delay(100L)

            holder.callback shouldNotBe null
            holder.callback?.onResponse(emailAddressQueryResponse)

            val result = deferredResult.await()
            result shouldNotBe null

            result.status shouldBe BatchOperationStatus.FAILURE
            result.successValues?.shouldBeEmpty()
            result.failureValues?.shouldHaveSize(2)
            result.failureValues?.forAtLeastOne {
                it.id shouldBe draftIds[0]
                it.errorType shouldBe "S3 delete failed"
            }
            result.failureValues?.forAtLeastOne {
                it.id shouldBe draftIds[1]
                it.errorType shouldBe "S3 delete failed"
            }

            verify(mockAppSyncClient).query(any<GetEmailAddressQuery>())
            // S3 client delete method is called once per draft id
            verify(mockS3Client).delete(
                check {
                    it shouldContain "$emailAddressId/draft/${draftIds[0]}"
                },
            )
            verify(mockS3Client).delete(
                check {
                    it shouldContain "$emailAddressId/draft/${draftIds[1]}"
                },
            )
        }
}
