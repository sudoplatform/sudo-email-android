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
import com.sudoplatform.sudoemail.s3.types.S3ClientListOutput
import com.sudoplatform.sudoemail.secure.DefaultSealingService
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
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
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the correct operation of [SudoEmailClient.listDraftEmailMessageMetadataForEmailAddressId]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailListDraftEmailMessageMetadataForEmailAddressIdTest : BaseTests() {

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

    private val mockNotificationHandler by before {
        mock<SudoEmailNotificationHandler>()
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
            mockNotificationHandler,
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
            mockNotificationHandler,
        )
    }

    @Test
    fun `listDraftEmailMessageMetadataForEmailAddressId() should throw an error if send address not found`() = runTest {
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

        val emailAddressId = "emailAddressId"

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                client.listDraftEmailMessageMetadataForEmailAddressId(emailAddressId)
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
    fun `listDraftEmailMessageMetadataForEmailAddressId() should return an empty list if no drafts found`() = runTest {
        val emailAddressId = "emailAddressId"

        mockS3Client.stub {
            onBlocking {
                list(anyString(), anyString())
            } doReturn emptyList()
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listDraftEmailMessageMetadataForEmailAddressId(emailAddressId)
        }

        deferredResult.start()
        val result = deferredResult.await()

        result.size shouldBe 0

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
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
    fun `listDraftEmailMessageMetadataForEmailAddressId() should return a list of metadata for the email address`() = runTest {
        val emailAddressId = "emailAddressId"

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.listDraftEmailMessageMetadataForEmailAddressId(emailAddressId)
        }

        deferredResult.start()
        val result = deferredResult.await()

        result.size shouldBe 2
        result[0].id shouldBe mockListObjectsResponse[0].key
        result[0].emailAddressId shouldBe emailAddressId
        result[1].id shouldBe mockListObjectsResponse[1].key
        result[1].emailAddressId shouldBe emailAddressId

        verify(mockApiCategory).query<String>(
            check {
                it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
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
