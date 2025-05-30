/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amazonaws.auth.CognitoCredentialsProvider
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.benasher44.uuid.bytes
import com.sudoplatform.sudoemail.graphql.GetEmailAddressQuery
import com.sudoplatform.sudoemail.graphql.ScheduleSendDraftMessageMutation
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.s3.S3Exception
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudoemail.types.inputs.ScheduleSendDraftMessageInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.amplify.GraphQLClient
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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.util.Date
import java.util.UUID

/**
 * Test the correct operation of [SudoEmailClient.scheduleSendDraftMessage]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailScheduleSendDraftMessageTest : BaseTests() {
    private val dummyDraftId = "dummyId"
    private val dummyEmailAddressId = "dummyEmailAddressId"
    private val sendAt = Date(Date().time + Duration.ofDays(1).toMillis())
    private val input by before {
        ScheduleSendDraftMessageInput(
            dummyDraftId,
            dummyEmailAddressId,
            sendAt,
        )
    }

    private val mutationResponse by before {
        JSONObject(
            """
                {
                    'scheduleSendDraftMessage': {
                        '__typename': 'ScheduledDraftMessage',
                        'draftMessageKey': 'dummyPrefix/$dummyDraftId',
                        'emailAddressId': '$dummyEmailAddressId',
                        'owner': 'owner',
                        'owners': [{
                            '__typename': 'Owner',
                            'id': 'ownerId',
                            'issuer': 'issuer'
                        }],
                        'sendAtEpochMs': ${sendAt.time},
                        'state': '${ScheduledDraftMessageState.SCHEDULED}',
                        'createdAtEpochMs': 1.0,
                        'updatedAtEpochMs': 1.0
                    }
                }
            """.trimIndent(),
        )
    }

    private val context by before {
        InstrumentationRegistry.getInstrumentation().targetContext
    }

    private val mockCognitoCredentialsProvider by before {
        mock<CognitoCredentialsProvider>().stub {
            on {
                identityId
            } doReturn "dummyIdentityId"
        }
    }

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on {
                getCredentialsProvider()
            } doReturn mockCognitoCredentialsProvider
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>()
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { getSymmetricKeyData(anyString()) } doReturn UUID.randomUUID().bytes
        }
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
            on {
                mutate<String>(
                    argThat { this.query.equals(ScheduleSendDraftMessageMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            } doAnswer {
                @Suppress("UNCHECKED_CAST")
                (it.arguments[1] as Consumer<GraphQLResponse<String>>).accept(
                    GraphQLResponse(mutationResponse.toString(), null),
                )
                mock<GraphQLOperation<String>>()
            }
        }
    }

    private val timestamp by before {
        Date()
    }

    private val mockMetadata: ObjectMetadata = ObjectMetadata()

    private val mockS3Client by before {
        mock<S3Client>().stub {
            onBlocking {
                getObjectMetadata(
                    any(),
                )
            } doReturn mockMetadata
        }
    }

    private val mockEmailMessageProcessor by before {
        mock<Rfc822MessageDataProcessor>()
    }

    private val mockSealingService by before {
        mock<SealingService>()
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
            mockServiceKeyManager,
            mockApiCategory,
            mockS3Client,
            mockEmailMessageProcessor,
            mockSealingService,
            mockEmailCryptoService,
        )
    }

    private fun setObjectMetadata() {
        mockMetadata.userMetadata["key-id"] = "dummyKeyId"
        mockMetadata.userMetadata["algorithm"] = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString()
        mockMetadata.lastModified = timestamp
    }

    @Test
    fun `scheduleSendDraftMessage() should throw EmailAddressNotFoundException if no address found`() = runTest {
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
                client.scheduleSendDraftMessage(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            org.mockito.kotlin.check {
                it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
    }

    @Test
    fun `scheduleSendDraftMessage() should throw InvalidArgumentException if sendAt is not in future`() = runTest {
        val sendAt = Date()

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                client.scheduleSendDraftMessage(
                    ScheduleSendDraftMessageInput(
                        input.id,
                        input.emailAddressId,
                        sendAt,
                    ),
                )
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            org.mockito.kotlin.check {
                it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
    }

    @Test
    fun `scheduleSendDraftMessage() should throw error if metadata download errors`() = runTest {
        val error = S3Exception.DownloadException("Unknown exception")

        mockS3Client.stub {
            onBlocking {
                getObjectMetadata(
                    any(),
                )
            } doThrow error
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.scheduleSendDraftMessage(input)
            }
        }

        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            org.mockito.kotlin.check {
                it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
        verify(mockS3Client).getObjectMetadata(
            anyString(),
        )
    }

    @Test
    fun `scheduleSendDraftMessage() should throw UnsealingException if no key-id in metadata`() = runTest {
        mockMetadata.userMetadata["algorithm"] = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString()
        mockS3Client.stub {
            onBlocking {
                getObjectMetadata(
                    any(),
                )
            } doReturn mockMetadata
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                client.scheduleSendDraftMessage(input)
            }
        }

        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            org.mockito.kotlin.check {
                it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
        verify(mockS3Client).getObjectMetadata(
            anyString(),
        )
    }

    @Test
    fun `scheduleSendDraftMessage() should throw UnsealingException if no algorithm in metadata`() = runTest {
        mockMetadata.userMetadata["key-id"] = "dummyKeyId"
        mockS3Client.stub {
            onBlocking {
                getObjectMetadata(
                    any(),
                )
            } doReturn mockMetadata
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                client.scheduleSendDraftMessage(input)
            }
        }

        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            org.mockito.kotlin.check {
                it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
        verify(mockS3Client).getObjectMetadata(
            anyString(),
        )
    }

    @Test
    fun `scheduleSendDraftMessage() should throw KeyNotFoundException if no symmetric key found`() = runTest {
        setObjectMetadata()
        mockServiceKeyManager.stub {
            onBlocking {
                getSymmetricKeyData(any())
            } doReturn null
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<KeyNotFoundException> {
                client.scheduleSendDraftMessage(input)
            }
        }

        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            org.mockito.kotlin.check {
                it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
        verify(mockS3Client).getObjectMetadata(
            anyString(),
        )
        verify(mockServiceKeyManager).getSymmetricKeyData(any())
    }

    @Test
    fun `scheduleSendDraftMessage() should throw an error if graphQl mutation fails`() = runTest {
        setObjectMetadata()
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(ScheduleSendDraftMessageMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            }.thenThrow(UnknownError("ERROR"))
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.scheduleSendDraftMessage(input)
            }
        }
        deferredResult.start()
        deferredResult.await()

        verify(mockApiCategory).query<String>(
            org.mockito.kotlin.check {
                it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
        verify(mockS3Client).getObjectMetadata(
            anyString(),
        )
        verify(mockUserClient, times(1)).getCredentialsProvider()
        verify(mockServiceKeyManager).getSymmetricKeyData(any())
        verify(mockApiCategory).mutate<String>(
            argThat { this.query.equals(ScheduleSendDraftMessageMutation.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `scheduleSendDraftMessage should return new ScheduledDraftMessage entity on success`() = runTest {
        setObjectMetadata()

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.scheduleSendDraftMessage(input)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result.id shouldBe dummyDraftId
        result.emailAddressId shouldBe dummyEmailAddressId
        result.sendAt shouldBe sendAt
        result.state shouldBe com.sudoplatform.sudoemail.types.ScheduledDraftMessageState.SCHEDULED

        verify(mockApiCategory).query<String>(
            org.mockito.kotlin.check {
                it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
        verify(mockUserClient, times(1)).getCredentialsProvider()
        verify(mockS3Client).getObjectMetadata(
            anyString(),
        )
        verify(mockServiceKeyManager).getSymmetricKeyData(any())
        verify(mockApiCategory).mutate<String>(
            argThat { this.query.equals(ScheduleSendDraftMessageMutation.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }
}
