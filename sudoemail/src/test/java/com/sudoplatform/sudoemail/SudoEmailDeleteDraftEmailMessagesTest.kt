/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudoemail.graphql.GetEmailAddressQuery
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
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.inspectors.forAtLeastOne
import io.kotlintest.matchers.collections.shouldBeEmpty
import io.kotlintest.matchers.collections.shouldContain
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
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

    private val draftIds = listOf("draftId1", "draftId2")
    private val emailAddressId = "emailAddressId"

    private val input by before {
        DeleteDraftEmailMessagesInput(draftIds, emailAddressId)
    }

    private val emailAddressQueryResponse by before {
        JSONObject(
            """
                {
                    'getEmailAddress': {
                        '__typename': 'EmailAddress',
                        'id': 'emailAddressId',
                        'owner': 'owner',
                        'owners': [{
                            '__typename': 'Owner',
                            'id': 'ownerId',
                            'issuer': 'issuer'
                        }],
                        'identityId': 'identityId',
                        'keyRingId': 'keyRingId',
                        'keyIds': [],
                        'version': '1',
                        'createdAtEpochMs': 1.0,
                        'updatedAtEpochMs': 1.0,
                        'lastReceivedAtEpochMs': 1.0,
                        'emailAddress': 'example@sudoplatform.com',
                        'size': 0.0,
                        'numberOfEmailMessages': 0,
                        'folders': [{
                            '__typename': 'EmailFolder',
                            'id': 'folderId',
                            'owner': 'owner',
                            'owners': [{
                                '__typename': 'Owner',
                                'id': 'ownerId',
                                'issuer': 'issuer'
                            }],
                            'version': 1,
                            'createdAtEpochMs': 1.0,
                            'updatedAtEpochMs': 1.0,
                            'emailAddressId': 'emailAddressId',
                            'folderName': 'folderName',
                            'size': 0.0,
                            'unseenCount': 0.0,
                            'ttl': 1.0
                        }]
                    }
                }
            """.trimIndent(),
        )
    }

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

    private val mockApiCategory by before {
        mock<ApiCategory>().stub {
            on {
                query<String>(
                    argThat { this.query.equals(GetEmailAddressQuery.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(emailAddressQueryResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
            }
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
            GraphQLClient(mockApiCategory),
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

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockUserClient,
            mockKeyManager,
            mockApiCategory,
            mockS3Client,
            mockEmailMessageProcessor,
            mockEmailCryptoService,
        )
    }

    @Test
    fun `deleteDraftEmailMessages() should throw an error if email address not found`() =
        runTest {
            val error = GraphQLResponse.Error(
                "mock",
                null,
                null,
                mapOf("errorType" to "AddressNotFound"),
            )
            mockApiCategory.stub {
                on {
                    query<String>(
                        argThat { this.query.equals(GetEmailAddressQuery.OPERATION_DOCUMENT) },
                        any(),
                        any(),
                    )
                } doAnswer {
                    @Suppress("UNCHECKED_CAST")
                    (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                        GraphQLResponse(null, listOf(error)),
                    )
                    mock<GraphQLOperation<String>>()
                }
            }

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                    client.deleteDraftEmailMessages(input)
                }
            }
            deferredResult.start()
            deferredResult.await()

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
        }

    @Test
    fun `deleteDraftEmailMessages() should return success result if all operations succeeded`() =
        runTest {
            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.deleteDraftEmailMessages(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.SUCCESS

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
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
            // Throw an exception from internal S3 client to provoke failure
            whenever(
                mockS3Client.delete(
                    check {
                        it shouldContain "$emailAddressId/draft/${draftIds[1]}"
                    },
                ),
            ).thenThrow(S3Exception.DeleteException("S3 delete failed"))

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.deleteDraftEmailMessages(input)
            }
            deferredResult.start()
            val result = deferredResult.await()

            result shouldNotBe null
            result.status shouldBe BatchOperationStatus.PARTIAL
            result.successValues?.shouldContain(draftIds[0])
            result.failureValues?.shouldHaveSize(1)
            result.failureValues?.first() shouldBe EmailMessageOperationFailureResult(
                draftIds[1],
                "S3 delete failed",
            )

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
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
            // Throw an exception from internal S3 client to provoke failure
            whenever(mockS3Client.delete(any()))
                .thenThrow(S3Exception.DeleteException("S3 delete failed"))

            val deferredResult = async(StandardTestDispatcher(testScheduler)) {
                client.deleteDraftEmailMessages(input)
            }

            deferredResult.start()
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

            verify(mockApiCategory).query<String>(
                check {
                    it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
                },
                any(),
                any(),
            )
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
