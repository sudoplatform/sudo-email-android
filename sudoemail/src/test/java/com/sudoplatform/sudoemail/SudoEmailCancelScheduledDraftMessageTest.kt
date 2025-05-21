/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import androidx.test.platform.app.InstrumentationRegistry
import com.amazonaws.auth.CognitoCredentialsProvider
import com.amplifyframework.api.ApiCategory
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudoemail.graphql.CancelScheduledDraftMessageMutation
import com.sudoplatform.sudoemail.graphql.GetEmailAddressQuery
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudoemail.types.inputs.CancelScheduledDraftMessageInput
import com.sudoplatform.sudoemail.util.Rfc822MessageDataProcessor
import com.sudoplatform.sudokeymanager.KeyManagerInterface
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

/**
 * Test the correct operation of [SudoEmailClient.cancelScheduledDraftMessage]
 * using mocks and spies
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailCancelScheduledDraftMessageTest : BaseTests() {
    private val dummyDraftId = "dummyId"
    private val dummyEmailAddressId = "dummyEmailAddressId"
    private val input by before {
        CancelScheduledDraftMessageInput(
            dummyDraftId,
            dummyEmailAddressId,
        )
    }

    private val mutationResponse by before {
        JSONObject(
            """
                {
                    'cancelScheduledDraftMessage': $dummyDraftId
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
        mock<ServiceKeyManager>()
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
                    argThat { this.query.equals(CancelScheduledDraftMessageMutation.OPERATION_DOCUMENT) },
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

    private val mockS3Client by before {
        mock<S3Client>()
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

    @Test
    fun `cancelScheduledDraftMessage() should throw EmailAddressNotFoundException if no address found`() = runTest {
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
                client.cancelScheduledDraftMessage(input)
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
    fun `cancelScheduledDraftMessage() should throw an error if graphQl mutation fails`() = runTest {
        mockApiCategory.stub {
            on {
                mutate<String>(
                    argThat { this.query.equals(CancelScheduledDraftMessageMutation.OPERATION_DOCUMENT) },
                    any(),
                    any(),
                )
            }.thenThrow(UnknownError("ERROR"))
        }

        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                client.cancelScheduledDraftMessage(input)
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
        verify(mockUserClient, times(1)).getCredentialsProvider()
        verify(mockApiCategory).mutate<String>(
            argThat { this.query.equals(CancelScheduledDraftMessageMutation.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }

    @Test
    fun `cancelScheduledDraftMessage() should return draft id on success`() = runTest {
        val deferredResult = async(StandardTestDispatcher(testScheduler)) {
            client.cancelScheduledDraftMessage(input)
        }
        deferredResult.start()
        val result = deferredResult.await()

        result shouldBe dummyDraftId

        verify(mockApiCategory).query<String>(
            org.mockito.kotlin.check {
                it.query shouldBe GetEmailAddressQuery.OPERATION_DOCUMENT
            },
            any(),
            any(),
        )
        verify(mockUserClient, times(1)).getCredentialsProvider()
        verify(mockApiCategory).mutate<String>(
            argThat { this.query.equals(CancelScheduledDraftMessageMutation.OPERATION_DOCUMENT) },
            any(),
            any(),
        )
    }
}
