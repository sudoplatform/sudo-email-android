/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.draftMessage

import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.graphql.CancelScheduledDraftMessageMutation
import com.sudoplatform.sudoemail.graphql.ListScheduledDraftMessagesForEmailAddressIdQuery
import com.sudoplatform.sudoemail.graphql.ScheduleSendDraftMessageMutation
import com.sudoplatform.sudoemail.graphql.type.ScheduledDraftMessageState
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationStatusEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.CancelScheduledDraftMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DeleteDraftEmailMessagesRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.EqualStateFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.GetDraftEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ListScheduledDraftMessagesForEmailAddressIdInputRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.NotEqualStateFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.NotOneOfStateFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.OneOfStateFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.SaveDraftEmailMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduleSendDraftMessageRequest
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduledDraftMessageFilterInputEntity
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.ScheduledDraftMessageStateEntity
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.s3.S3Exception
import com.sudoplatform.sudoemail.s3.types.S3ClientListOutput
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
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

/**
 * Test the correct operation of [GraphQLS3DraftEmailMessageService]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class GraphQLS3DraftEmailMessageServiceTest : BaseTests() {
    private val emailAddressId = mockEmailAddressId
    private val draftId = "draftId"
    private val s3Key =
        DefaultS3Client.constructS3KeyForDraftEmailMessage(
            emailAddressId = emailAddressId,
            draftId,
        )
    private val metadataObject =
        mapOf(
            StringConstants.DRAFT_METADATA_KEY_ID_NAME to mockSymmetricKeyId,
            StringConstants.DRAFT_METADATA_ALGORITHM_NAME to SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
        )

    override val mockS3Client by before {
        mock<S3Client>()
    }

    override val mockApiClient by before {
        mock<ApiClient>()
    }

    private val instanceUnderTest by before {
        GraphQLS3DraftEmailMessageService(
            mockS3Client,
            mockApiClient,
            mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockS3Client,
            mockApiClient,
        )
    }

    /** Start saveDraftEmailMessage tests **/

    @Test
    fun `saveDraftEmailMessage() should upload data and return s3Key on success`() =
        runTest {
            val mockUploadResponse by before {
                "42"
            }
            mockS3Client.stub {
                onBlocking {
                    upload(
                        any(),
                        any(),
                        anyOrNull(),
                    )
                } doReturn mockUploadResponse
            }

            val uploadData = DataFactory.unsealedHeaderDetailsString.toByteArray()
            val request by before {
                SaveDraftEmailMessageRequest(
                    uploadData = uploadData,
                    s3Key = s3Key,
                    metadataObject = metadataObject,
                )
            }

            val result = instanceUnderTest.save(request)

            result shouldBe s3Key

            verify(mockS3Client).upload(
                check {
                    it shouldBe uploadData
                },
                check {
                    it shouldBe s3Key
                },
                check {
                    it shouldBe metadataObject
                },
            )
        }

    @Test
    fun `saveDraftEmailMessage() should throw when S3 upload fails with UploadException`() =
        runTest {
            mockS3Client.stub {
                onBlocking {
                    upload(
                        any(),
                        any(),
                        anyOrNull(),
                    )
                } doThrow S3Exception.UploadException("Upload failed")
            }

            val uploadData = DataFactory.unsealedHeaderDetailsString.toByteArray()
            val request =
                SaveDraftEmailMessageRequest(
                    uploadData = uploadData,
                    s3Key = s3Key,
                    metadataObject = metadataObject,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.UnknownException> {
                instanceUnderTest.save(request)
            }

            verify(mockS3Client).upload(
                check {
                    it shouldBe uploadData
                },
                check {
                    it shouldBe s3Key
                },
                check {
                    it shouldBe metadataObject
                },
            )
        }

    @Test
    fun `saveDraftEmailMessage() should throw when generic exception occurs`() =
        runTest {
            mockS3Client.stub {
                onBlocking {
                    upload(
                        any(),
                        any(),
                        anyOrNull(),
                    )
                } doThrow RuntimeException("Unexpected error")
            }

            val uploadData = DataFactory.unsealedHeaderDetailsString.toByteArray()
            val request =
                SaveDraftEmailMessageRequest(
                    uploadData = uploadData,
                    s3Key = s3Key,
                    metadataObject = emptyMap(),
                )

            shouldThrow<RuntimeException> {
                instanceUnderTest.save(request)
            }

            verify(mockS3Client).upload(
                check {
                    it shouldBe uploadData
                },
                check {
                    it shouldBe s3Key
                },
                anyOrNull(),
            )
        }

    @Test
    fun `saveDraftEmailMessage() should work with large upload data`() =
        runTest {
            mockS3Client.stub {
                onBlocking {
                    upload(
                        any(),
                        any(),
                        anyOrNull(),
                    )
                } doReturn "uploadId"
            }

            val largeData = ByteArray(1024 * 1024) { it.toByte() } // 1 MB of data
            val request =
                SaveDraftEmailMessageRequest(
                    uploadData = largeData,
                    s3Key = s3Key,
                    metadataObject = emptyMap(),
                )

            val result = instanceUnderTest.save(request)

            result shouldBe s3Key

            verify(mockS3Client).upload(
                check {
                    it shouldBe largeData
                    it.size shouldBe 1024 * 1024
                },
                check {
                    it shouldBe s3Key
                },
                anyOrNull(),
            )
        }

    /** Start getDraftEmailMessage tests **/

    @Test
    fun `getDraftEmailMessage() should return data successfully`() =
        runTest {
            val rfc822Data = "RFC822 data content".toByteArray()
            val updatedAt = Date()
            val metadata =
                ObjectMetadata().apply {
                    userMetadata =
                        mapOf(
                            StringConstants.DRAFT_METADATA_KEY_ID_NAME to mockSymmetricKeyId,
                            StringConstants.DRAFT_METADATA_ALGORITHM_NAME to
                                SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                        )
                    lastModified = updatedAt
                }

            mockS3Client.stub {
                onBlocking { getObjectMetadata(any()) } doReturn metadata
                onBlocking { download(any()) } doReturn rfc822Data
            }

            val request =
                GetDraftEmailMessageRequest(
                    s3Key = s3Key,
                )

            val result = instanceUnderTest.get(request)

            result.s3Key shouldBe s3Key
            result.rfc822Data shouldBe rfc822Data
            result.keyId shouldBe mockSymmetricKeyId
            result.updatedAt shouldBe updatedAt

            verify(mockS3Client).getObjectMetadata(s3Key)
            verify(mockS3Client).download(s3Key)
        }

    @Test
    fun `getDraftEmailMessage() should throw UnsealingException when keyId is missing`() =
        runTest {
            val metadata =
                ObjectMetadata().apply {
                    userMetadata =
                        mapOf(
                            StringConstants.DRAFT_METADATA_ALGORITHM_NAME to
                                SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                        )
                }

            mockS3Client.stub {
                onBlocking { getObjectMetadata(any()) } doReturn metadata
            }

            val request =
                GetDraftEmailMessageRequest(
                    s3Key = s3Key,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                    instanceUnderTest.get(request)
                }

            exception.message shouldBe StringConstants.S3_KEY_ID_ERROR_MSG

            verify(mockS3Client).getObjectMetadata(s3Key)
        }

    @Test
    fun `getDraftEmailMessage() should throw UnsealingException when algorithm is missing`() =
        runTest {
            val metadata =
                ObjectMetadata().apply {
                    userMetadata =
                        mapOf(
                            StringConstants.DRAFT_METADATA_KEY_ID_NAME to mockSymmetricKeyId,
                        )
                }

            mockS3Client.stub {
                onBlocking { getObjectMetadata(any()) } doReturn metadata
            }

            val request =
                GetDraftEmailMessageRequest(
                    s3Key = s3Key,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                    instanceUnderTest.get(request)
                }

            exception.message shouldBe StringConstants.S3_ALGORITHM_ERROR_MSG

            verify(mockS3Client).getObjectMetadata(s3Key)
        }

    @Test
    fun `getDraftEmailMessage() should throw when S3 getObjectMetadata fails`() =
        runTest {
            mockS3Client.stub {
                onBlocking { getObjectMetadata(any()) } doThrow S3Exception.DownloadException(StringConstants.S3_NOT_FOUND_ERROR_CODE)
            }

            val request =
                GetDraftEmailMessageRequest(
                    s3Key = s3Key,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                instanceUnderTest.get(request)
            }

            verify(mockS3Client).getObjectMetadata(s3Key)
        }

    @Test
    fun `getDraftEmailMessage() should throw when S3 download fails`() =
        runTest {
            val updatedAt = Date()
            val metadata =
                ObjectMetadata().apply {
                    userMetadata =
                        mapOf(
                            StringConstants.DRAFT_METADATA_KEY_ID_NAME to mockSymmetricKeyId,
                            StringConstants.DRAFT_METADATA_ALGORITHM_NAME to
                                SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                        )
                    lastModified = updatedAt
                }

            mockS3Client.stub {
                onBlocking { getObjectMetadata(any()) } doReturn metadata
                onBlocking { download(any()) } doThrow S3Exception.DownloadException(StringConstants.S3_NOT_FOUND_ERROR_CODE)
            }

            val request =
                GetDraftEmailMessageRequest(
                    s3Key = s3Key,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                instanceUnderTest.get(request)
            }

            verify(mockS3Client).getObjectMetadata(s3Key)
            verify(mockS3Client).download(s3Key)
        }

    @Test
    fun `getDraftEmailMessage() should throw EmailMessageNotFoundException when S3 object not found`() =
        runTest {
            val s3Exception =
                AmazonS3Exception("Not Found").apply {
                    errorCode = StringConstants.S3_NOT_FOUND_ERROR_CODE
                }

            mockS3Client.stub {
                onBlocking { getObjectMetadata(any()) } doThrow s3Exception
            }

            val request =
                GetDraftEmailMessageRequest(
                    s3Key = s3Key,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                instanceUnderTest.get(request)
            }

            verify(mockS3Client).getObjectMetadata(s3Key)
        }

    /** Start deleteDraftEmailMessages tests **/

    @Test
    fun `deleteDraftEmailMessages() should return success result when all deletes succeed`() =
        runTest {
            val s3Keys =
                listOf(
                    DefaultS3Client.constructS3KeyForDraftEmailMessage(
                        emailAddressId = emailAddressId,
                        "draftId1",
                    ),
                    DefaultS3Client.constructS3KeyForDraftEmailMessage(
                        emailAddressId = emailAddressId,
                        "draftId2",
                    ),
                    DefaultS3Client.constructS3KeyForDraftEmailMessage(
                        emailAddressId = emailAddressId,
                        "draftId3",
                    ),
                )

            mockS3Client.stub {
                onBlocking { delete(any()) } doReturn Unit
            }

            val request =
                DeleteDraftEmailMessagesRequest(
                    s3Keys = s3Keys.toSet(),
                )

            val result = instanceUnderTest.delete(request)

            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues?.size shouldBe 3
            result.successValues?.map { it.id }?.toSet() shouldBe s3Keys.map { it }.toSet()
            result.failureValues?.size shouldBe 0

            verify(mockS3Client).delete(
                check {
                    it shouldBe s3Keys[0]
                },
            )
            verify(mockS3Client).delete(
                check {
                    it shouldBe s3Keys[1]
                },
            )
            verify(mockS3Client).delete(
                check {
                    it shouldBe s3Keys[2]
                },
            )
        }

    @Test
    fun `deleteDraftEmailMessages() should return success result when s3Keys is empty`() =
        runTest {
            val request =
                DeleteDraftEmailMessagesRequest(
                    s3Keys = emptySet(),
                )

            val result = instanceUnderTest.delete(request)

            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues?.size shouldBe 0
            result.failureValues?.size shouldBe 0
        }

    @Test
    fun `deleteDraftEmailMessages() should return failure result when all deletes fail`() =
        runTest {
            val s3Keys =
                listOf(
                    DefaultS3Client.constructS3KeyForDraftEmailMessage(
                        emailAddressId = emailAddressId,
                        "draftId1",
                    ),
                    DefaultS3Client.constructS3KeyForDraftEmailMessage(
                        emailAddressId = emailAddressId,
                        "draftId2",
                    ),
                    DefaultS3Client.constructS3KeyForDraftEmailMessage(
                        emailAddressId = emailAddressId,
                        "draftId3",
                    ),
                )

            mockS3Client.stub {
                onBlocking { delete(any()) } doThrow S3Exception.DeleteException("Delete failed")
            }

            val request =
                DeleteDraftEmailMessagesRequest(
                    s3Keys = s3Keys.toSet(),
                )

            val result = instanceUnderTest.delete(request)

            result.status shouldBe BatchOperationStatusEntity.FAILURE
            result.successValues?.size shouldBe 0
            result.failureValues?.size shouldBe 3
            result.failureValues?.map { it.id }?.toSet() shouldBe s3Keys.map { it }.toSet()
            result.failureValues?.all { it.errorType.contains("Delete failed") } shouldBe true

            verify(mockS3Client).delete(
                check {
                    it shouldBe s3Keys[0]
                },
            )
            verify(mockS3Client).delete(
                check {
                    it shouldBe s3Keys[1]
                },
            )
            verify(mockS3Client).delete(
                check {
                    it shouldBe s3Keys[2]
                },
            )
        }

    @Test
    fun `deleteDraftEmailMessages() should return partial result when some deletes fail`() =
        runTest {
            val s3Keys =
                listOf(
                    DefaultS3Client.constructS3KeyForDraftEmailMessage(
                        emailAddressId = emailAddressId,
                        "draftId1",
                    ),
                    DefaultS3Client.constructS3KeyForDraftEmailMessage(
                        emailAddressId = emailAddressId,
                        "draftId2",
                    ),
                    DefaultS3Client.constructS3KeyForDraftEmailMessage(
                        emailAddressId = emailAddressId,
                        "draftId3",
                    ),
                )

            mockS3Client.stub {
                onBlocking { delete(s3Keys[0]) } doReturn Unit
                onBlocking { delete(s3Keys[1]) } doThrow S3Exception.DeleteException("Delete failed")
                onBlocking { delete(s3Keys[2]) } doReturn Unit
            }

            val request =
                DeleteDraftEmailMessagesRequest(
                    s3Keys = s3Keys.toSet(),
                )

            val result = instanceUnderTest.delete(request)

            result.status shouldBe BatchOperationStatusEntity.PARTIAL
            result.successValues?.size shouldBe 2
            result.successValues?.map { it.id }?.toSet() shouldBe setOf(s3Keys[0], s3Keys[2])
            result.failureValues?.size shouldBe 1
            result.failureValues?.get(0)?.id shouldBe s3Keys[1]
            result.failureValues
                ?.get(0)
                ?.errorType
                ?.contains("Delete failed") shouldBe true

            verify(mockS3Client).delete(
                check {
                    it shouldBe s3Keys[0]
                },
            )
            verify(mockS3Client).delete(
                check {
                    it shouldBe s3Keys[1]
                },
            )
            verify(mockS3Client).delete(
                check {
                    it shouldBe s3Keys[2]
                },
            )
        }

    @Test
    fun `deleteDraftEmailMessages() should handle different exception types`() =
        runTest {
            val s3Keys =
                listOf(
                    DefaultS3Client.constructS3KeyForDraftEmailMessage(
                        emailAddressId = emailAddressId,
                        "draftId1",
                    ),
                    DefaultS3Client.constructS3KeyForDraftEmailMessage(
                        emailAddressId = emailAddressId,
                        "draftId2",
                    ),
                    DefaultS3Client.constructS3KeyForDraftEmailMessage(
                        emailAddressId = emailAddressId,
                        "draftId3",
                    ),
                )

            mockS3Client.stub {
                onBlocking { delete(s3Keys[0]) } doThrow S3Exception.DeleteException("S3 delete error")
                onBlocking { delete(s3Keys[1]) } doThrow RuntimeException("Runtime error")
                onBlocking { delete(s3Keys[2]) } doThrow IllegalStateException("Illegal state")
            }

            val request =
                DeleteDraftEmailMessagesRequest(
                    s3Keys = s3Keys.toSet(),
                )

            val result = instanceUnderTest.delete(request)

            result.status shouldBe BatchOperationStatusEntity.FAILURE
            result.successValues?.size shouldBe 0
            result.failureValues?.size shouldBe 3
            result.failureValues?.find { it.id == s3Keys[0] }?.errorType shouldBe "S3 delete error"
            result.failureValues?.find { it.id == s3Keys[1] }?.errorType shouldBe "Runtime error"
            result.failureValues?.find { it.id == s3Keys[2] }?.errorType shouldBe "Illegal state"

            verify(mockS3Client).delete(
                check {
                    it shouldBe s3Keys[0]
                },
            )
            verify(mockS3Client).delete(
                check {
                    it shouldBe s3Keys[1]
                },
            )
            verify(mockS3Client).delete(
                check {
                    it shouldBe s3Keys[2]
                },
            )
        }

    @Test
    fun `deleteDraftEmailMessages() should handle single s3Key successfully`() =
        runTest {
            val s3Keys =
                listOf(
                    DefaultS3Client.constructS3KeyForDraftEmailMessage(
                        emailAddressId = emailAddressId,
                        "draftId1",
                    ),
                )

            mockS3Client.stub {
                onBlocking { delete(any()) } doReturn Unit
            }

            val request =
                DeleteDraftEmailMessagesRequest(
                    s3Keys = s3Keys.toSet(),
                )

            val result = instanceUnderTest.delete(request)

            result.status shouldBe BatchOperationStatusEntity.SUCCESS
            result.successValues?.size shouldBe 1
            result.successValues?.get(0)?.id shouldBe s3Keys[0]
            result.failureValues?.size shouldBe 0

            verify(mockS3Client).delete(s3Keys[0])
        }

    @Test
    fun `deleteDraftEmailMessages() should handle exception with null message`() =
        runTest {
            val s3Keys =
                listOf(
                    DefaultS3Client.constructS3KeyForDraftEmailMessage(
                        emailAddressId = emailAddressId,
                        "draftId1",
                    ),
                )

            mockS3Client.stub {
                onBlocking { delete(any()) } doThrow RuntimeException(null as String?)
            }

            val request =
                DeleteDraftEmailMessagesRequest(
                    s3Keys = s3Keys.toSet(),
                )

            val result = instanceUnderTest.delete(request)

            result.status shouldBe BatchOperationStatusEntity.FAILURE
            result.successValues?.size shouldBe 0
            result.failureValues?.size shouldBe 1
            result.failureValues?.get(0)?.id shouldBe s3Keys[0]
            result.failureValues?.get(0)?.errorType shouldBe StringConstants.UNKNOWN_ERROR_MSG

            verify(mockS3Client).delete(s3Keys[0])
        }

    /** Start listDraftEmailMessageMetadataForEmailAddressId tests **/

    @Test
    fun `listDraftEmailMessageMetadataForEmailAddressId() should return list of draft metadata successfully`() =
        runTest {
            val emailAddressId = mockEmailAddressId
            val draftId1 = "draftId1"
            val draftId2 = "draftId2"
            val draftId3 = "draftId3"
            val date1 = Date(1000L)
            val date2 = Date(2000L)
            val date3 = Date(3000L)

            val s3ListKey = DefaultS3Client.constructS3KeyForDraftEmailMessage(emailAddressId)
            val s3Objects =
                listOf(
                    S3ClientListOutput(
                        key = "$s3ListKey/$draftId1",
                        lastModified = date1,
                    ),
                    S3ClientListOutput(
                        key = "$s3ListKey/$draftId2",
                        lastModified = date2,
                    ),
                    S3ClientListOutput(
                        key = "$s3ListKey/$draftId3",
                        lastModified = date3,
                    ),
                )

            mockS3Client.stub {
                onBlocking { list(any()) } doReturn s3Objects
            }

            val result = instanceUnderTest.listMetadataForEmailAddressId(emailAddressId)

            result.size shouldBe 3
            result[0].id shouldBe draftId1
            result[0].emailAddressId shouldBe emailAddressId
            result[0].updatedAt shouldBe date1
            result[1].id shouldBe draftId2
            result[1].emailAddressId shouldBe emailAddressId
            result[1].updatedAt shouldBe date2
            result[2].id shouldBe draftId3
            result[2].emailAddressId shouldBe emailAddressId
            result[2].updatedAt shouldBe date3

            verify(mockS3Client).list(any())
        }

    @Test
    fun `listDraftEmailMessageMetadataForEmailAddressId() should return empty list when no drafts exist`() =
        runTest {
            val emailAddressId = mockEmailAddressId

            mockS3Client.stub {
                onBlocking { list(any()) } doReturn emptyList()
            }

            val result = instanceUnderTest.listMetadataForEmailAddressId(emailAddressId)

            result.size shouldBe 0

            verify(mockS3Client).list(any())
        }

    @Test
    fun `listDraftEmailMessageMetadataForEmailAddressId() should extract draft ID from S3 key correctly`() =
        runTest {
            val emailAddressId = mockEmailAddressId
            val draftId = "uniqueDraftId123"
            val s3Prefix = DefaultS3Client.constructS3KeyForDraftEmailMessage(emailAddressId)
            val updatedAt = Date()

            val s3Objects =
                listOf(
                    S3ClientListOutput(
                        key = "$s3Prefix/$draftId",
                        lastModified = updatedAt,
                    ),
                )

            mockS3Client.stub {
                onBlocking { list(any()) } doReturn s3Objects
            }

            val result = instanceUnderTest.listMetadataForEmailAddressId(emailAddressId)

            result.size shouldBe 1
            result[0].id shouldBe draftId

            verify(mockS3Client).list(any())
        }

    @Test
    fun `listDraftEmailMessageMetadataForEmailAddressId() should handle single draft`() =
        runTest {
            val emailAddressId = mockEmailAddressId
            val draftId = "singleDraft"
            val s3Prefix = DefaultS3Client.constructS3KeyForDraftEmailMessage(emailAddressId)
            val updatedAt = Date()

            val s3Objects =
                listOf(
                    S3ClientListOutput(
                        key = "$s3Prefix/$draftId",
                        lastModified = updatedAt,
                    ),
                )

            mockS3Client.stub {
                onBlocking { list(any()) } doReturn s3Objects
            }

            val result = instanceUnderTest.listMetadataForEmailAddressId(emailAddressId)

            result.size shouldBe 1
            result[0].id shouldBe draftId
            result[0].emailAddressId shouldBe emailAddressId
            result[0].updatedAt shouldBe updatedAt

            verify(mockS3Client).list(any())
        }

    @Test
    fun `listDraftEmailMessageMetadataForEmailAddressId() should throw when S3 list fails`() =
        runTest {
            val emailAddressId = mockEmailAddressId

            mockS3Client.stub {
                onBlocking { list(any()) } doThrow S3Exception.DownloadException(StringConstants.S3_NOT_FOUND_ERROR_CODE)
            }

            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                instanceUnderTest.listMetadataForEmailAddressId(emailAddressId)
            }

            verify(mockS3Client).list(any())
        }

    @Test
    fun `listDraftEmailMessageMetadataForEmailAddressId() should throw when generic exception occurs`() =
        runTest {
            val emailAddressId = mockEmailAddressId

            mockS3Client.stub {
                onBlocking { list(any()) } doThrow RuntimeException("Unexpected error")
            }

            shouldThrow<RuntimeException> {
                instanceUnderTest.listMetadataForEmailAddressId(emailAddressId)
            }

            verify(mockS3Client).list(any())
        }

    @Test
    fun `listDraftEmailMessageMetadataForEmailAddressId() should preserve order from S3`() =
        runTest {
            val emailAddressId = mockEmailAddressId
            val s3Prefix = DefaultS3Client.constructS3KeyForDraftEmailMessage(emailAddressId)
            val date1 = Date(1000L)
            val date2 = Date(2000L)
            val date3 = Date(3000L)

            // Create objects in a specific order
            val s3Objects =
                listOf(
                    S3ClientListOutput(
                        key = "$s3Prefix/draft3",
                        lastModified = date3,
                    ),
                    S3ClientListOutput(
                        key = "$s3Prefix/draft1",
                        lastModified = date1,
                    ),
                    S3ClientListOutput(
                        key = "$s3Prefix/draft2",
                        lastModified = date2,
                    ),
                )

            mockS3Client.stub {
                onBlocking { list(any()) } doReturn s3Objects
            }

            val result = instanceUnderTest.listMetadataForEmailAddressId(emailAddressId)

            result.size shouldBe 3
            // Order should be preserved from S3
            result[0].id shouldBe "draft3"
            result[0].updatedAt shouldBe date3
            result[1].id shouldBe "draft1"
            result[1].updatedAt shouldBe date1
            result[2].id shouldBe "draft2"
            result[2].updatedAt shouldBe date2

            verify(mockS3Client).list(any())
        }

    @Test
    fun `listDraftEmailMessageMetadataForEmailAddressId() should construct correct S3 prefix`() =
        runTest {
            val emailAddressId = "testEmailAddressId123"
            val expectedPrefix = DefaultS3Client.constructS3KeyForDraftEmailMessage(emailAddressId)

            mockS3Client.stub {
                onBlocking { list(any()) } doReturn emptyList()
            }

            instanceUnderTest.listMetadataForEmailAddressId(emailAddressId)

            verify(mockS3Client).list(
                check {
                    it shouldBe expectedPrefix
                },
            )
        }

    /** Start scheduleSendDraftMessage tests **/

    @Test
    fun `scheduleSendDraftMessage() should return ScheduledDraftMessageEntity on success`() =
        runTest {
            val draftMessageKey = "draftMessageKey"
            val sendAt = Date(System.currentTimeMillis() + 86400000) // 1 day in future
            val symmetricKey = "symmetricKey"
            val owner = mockOwner
            val createdAt = Date()
            val updatedAt = Date()

            val scheduledDraftMessage =
                DataFactory.getScheduledDraftMessage(
                    draftMessageKey = draftMessageKey,
                    emailAddressId = emailAddressId,
                    owner = owner,
                    sendAtEpochMs = sendAt.time.toDouble(),
                    state = ScheduledDraftMessageState.SCHEDULED,
                    createdAtEpochMs = createdAt.time.toDouble(),
                    updatedAtEpochMs = updatedAt.time.toDouble(),
                )

            val mutationResponse = DataFactory.scheduleSendDraftMessageMutationResponse(scheduledDraftMessage)

            mockApiClient.stub {
                onBlocking { scheduleSendDraftMessageMutation(any()) } doReturn mutationResponse
            }

            val request =
                ScheduleSendDraftMessageRequest(
                    draftMessageKey = draftMessageKey,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                    symmetricKey = symmetricKey,
                )

            val result = instanceUnderTest.scheduleSend(request)

            result.id shouldBe draftMessageKey.substringAfterLast('/')
            result.emailAddressId shouldBe emailAddressId
            result.owner shouldBe owner
            result.sendAt.time shouldBe sendAt.time
            result.state shouldBe ScheduledDraftMessageStateEntity.SCHEDULED

            verify(mockApiClient).scheduleSendDraftMessageMutation(
                check {
                    it.draftMessageKey shouldBe draftMessageKey
                    it.emailAddressId shouldBe emailAddressId
                    it.sendAtEpochMs shouldBe sendAt.time.toDouble()
                    it.symmetricKey shouldBe symmetricKey
                },
            )
        }

    @Test
    fun `scheduleSendDraftMessage() should throw when mutation returns errors`() =
        runTest {
            val draftMessageKey = "draftMessageKey"
            val sendAt = Date(System.currentTimeMillis() + 86400000)
            val symmetricKey = "symmetricKey"

            val errorResponse =
                GraphQLResponse<ScheduleSendDraftMessageMutation.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "InvalidArgument",
                            emptyList(),
                            emptyList(),
                            mapOf("errorType" to "InvalidArgument"),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking { scheduleSendDraftMessageMutation(any()) } doReturn errorResponse
            }

            val request =
                ScheduleSendDraftMessageRequest(
                    draftMessageKey = draftMessageKey,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                    symmetricKey = symmetricKey,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                instanceUnderTest.scheduleSend(request)
            }

            verify(mockApiClient).scheduleSendDraftMessageMutation(any())
        }

    @Test
    fun `scheduleSendDraftMessage() should throw FailedException when response data is null`() =
        runTest {
            val draftMessageKey = "draftMessageKey"
            val sendAt = Date(System.currentTimeMillis() + 86400000)
            val symmetricKey = "symmetricKey"

            val nullDataResponse =
                GraphQLResponse<ScheduleSendDraftMessageMutation.Data>(
                    null,
                    null,
                )

            mockApiClient.stub {
                onBlocking { scheduleSendDraftMessageMutation(any()) } doReturn nullDataResponse
            }

            val request =
                ScheduleSendDraftMessageRequest(
                    draftMessageKey = draftMessageKey,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                    symmetricKey = symmetricKey,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                instanceUnderTest.scheduleSend(request)
            }

            verify(mockApiClient).scheduleSendDraftMessageMutation(any())
        }

    @Test
    fun `scheduleSendDraftMessage() should throw when API client throws exception`() =
        runTest {
            val draftMessageKey = "draftMessageKey"
            val sendAt = Date(System.currentTimeMillis() + 86400000)
            val symmetricKey = "symmetricKey"

            mockApiClient.stub {
                onBlocking { scheduleSendDraftMessageMutation(any()) } doThrow RuntimeException("Network error")
            }

            val request =
                ScheduleSendDraftMessageRequest(
                    draftMessageKey = draftMessageKey,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                    symmetricKey = symmetricKey,
                )

            shouldThrow<RuntimeException> {
                instanceUnderTest.scheduleSend(request)
            }

            verify(mockApiClient).scheduleSendDraftMessageMutation(any())
        }

    @Test
    fun `scheduleSendDraftMessage() should convert sendAt Date to epoch milliseconds correctly`() =
        runTest {
            val draftMessageKey = "draftMessageKey"
            val sendAtEpochMs = 1700000000000L // Specific timestamp
            val sendAt = Date(sendAtEpochMs)
            val symmetricKey = "symmetricKey"

            val scheduledDraftMessage =
                DataFactory.getScheduledDraftMessage(
                    draftMessageKey = draftMessageKey,
                    emailAddressId = emailAddressId,
                    sendAtEpochMs = sendAtEpochMs.toDouble(),
                    state = ScheduledDraftMessageState.SCHEDULED,
                    createdAtEpochMs = System.currentTimeMillis().toDouble(),
                    updatedAtEpochMs = System.currentTimeMillis().toDouble(),
                )

            val mutationResponse = DataFactory.scheduleSendDraftMessageMutationResponse(scheduledDraftMessage)

            mockApiClient.stub {
                onBlocking { scheduleSendDraftMessageMutation(any()) } doReturn mutationResponse
            }

            val request =
                ScheduleSendDraftMessageRequest(
                    draftMessageKey = draftMessageKey,
                    emailAddressId = emailAddressId,
                    sendAt = sendAt,
                    symmetricKey = symmetricKey,
                )

            instanceUnderTest.scheduleSend(request)

            verify(mockApiClient).scheduleSendDraftMessageMutation(
                check {
                    it.sendAtEpochMs shouldBe sendAtEpochMs.toDouble()
                },
            )
        }

    /** Start getDraftMessageObjectMetadata tests **/

    @Test
    fun `getDraftMessageObjectMetadata() should return metadata successfully`() =
        runTest {
            val s3Key = "test-s3-key"
            val updatedAt = Date()
            val metadata =
                ObjectMetadata().apply {
                    userMetadata =
                        mapOf(
                            StringConstants.DRAFT_METADATA_KEY_ID_NAME to mockSymmetricKeyId,
                            StringConstants.DRAFT_METADATA_ALGORITHM_NAME to
                                SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                        )
                    lastModified = updatedAt
                }

            mockS3Client.stub {
                onBlocking { getObjectMetadata(any()) } doReturn metadata
            }

            val result = instanceUnderTest.getObjectMetadata(s3Key)

            result.keyId shouldBe mockSymmetricKeyId
            result.algorithm shouldBe SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString()
            result.updatedAt shouldBe updatedAt

            verify(mockS3Client).getObjectMetadata(s3Key)
        }

    @Test
    fun `getDraftMessageObjectMetadata() should throw UnsealingException when keyId is null`() =
        runTest {
            val s3Key = "test-s3-key"
            val metadata =
                ObjectMetadata().apply {
                    userMetadata =
                        mapOf(
                            StringConstants.DRAFT_METADATA_ALGORITHM_NAME to
                                SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                        )
                }

            mockS3Client.stub {
                onBlocking { getObjectMetadata(any()) } doReturn metadata
            }

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                    instanceUnderTest.getObjectMetadata(s3Key)
                }

            exception.message shouldBe StringConstants.S3_KEY_ID_ERROR_MSG

            verify(mockS3Client).getObjectMetadata(s3Key)
        }

    @Test
    fun `getDraftMessageObjectMetadata() should throw UnsealingException when algorithm is null`() =
        runTest {
            val s3Key = "test-s3-key"
            val metadata =
                ObjectMetadata().apply {
                    userMetadata =
                        mapOf(
                            StringConstants.DRAFT_METADATA_KEY_ID_NAME to mockSymmetricKeyId,
                        )
                }

            mockS3Client.stub {
                onBlocking { getObjectMetadata(any()) } doReturn metadata
            }

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                    instanceUnderTest.getObjectMetadata(s3Key)
                }

            exception.message shouldBe StringConstants.S3_ALGORITHM_ERROR_MSG

            verify(mockS3Client).getObjectMetadata(s3Key)
        }

    @Test
    fun `getDraftMessageObjectMetadata() should throw when S3 getObjectMetadata fails`() =
        runTest {
            val s3Key = "test-s3-key"

            mockS3Client.stub {
                onBlocking { getObjectMetadata(any()) } doThrow S3Exception.DownloadException(StringConstants.S3_NOT_FOUND_ERROR_CODE)
            }

            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                instanceUnderTest.getObjectMetadata(s3Key)
            }

            verify(mockS3Client).getObjectMetadata(s3Key)
        }

    @Test
    fun `getDraftMessageObjectMetadata() should throw when generic exception occurs`() =
        runTest {
            val s3Key = "test-s3-key"

            mockS3Client.stub {
                onBlocking { getObjectMetadata(any()) } doThrow RuntimeException("Unexpected error")
            }

            shouldThrow<RuntimeException> {
                instanceUnderTest.getObjectMetadata(s3Key)
            }

            verify(mockS3Client).getObjectMetadata(s3Key)
        }

    @Test
    fun `getDraftMessageObjectMetadata() should handle empty userMetadata map`() =
        runTest {
            val s3Key = "test-s3-key"
            val metadata =
                ObjectMetadata().apply {
                    userMetadata = emptyMap()
                }

            mockS3Client.stub {
                onBlocking { getObjectMetadata(any()) } doReturn metadata
            }

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.UnsealingException> {
                    instanceUnderTest.getObjectMetadata(s3Key)
                }

            exception.message shouldBe StringConstants.S3_KEY_ID_ERROR_MSG

            verify(mockS3Client).getObjectMetadata(s3Key)
        }

    @Test
    fun `getDraftMessageObjectMetadata() should correctly extract all metadata fields`() =
        runTest {
            val s3Key = "test-s3-key"
            val testKeyId = "testKeyId123"
            val testAlgorithm = "AES-256-GCM"
            val testUpdatedAt = Date(1234567890000L)
            val metadata =
                ObjectMetadata().apply {
                    userMetadata =
                        mapOf(
                            StringConstants.DRAFT_METADATA_KEY_ID_NAME to testKeyId,
                            StringConstants.DRAFT_METADATA_ALGORITHM_NAME to testAlgorithm,
                        )
                    lastModified = testUpdatedAt
                }

            mockS3Client.stub {
                onBlocking { getObjectMetadata(any()) } doReturn metadata
            }

            val result = instanceUnderTest.getObjectMetadata(s3Key)

            result.keyId shouldBe testKeyId
            result.algorithm shouldBe testAlgorithm
            result.updatedAt shouldBe testUpdatedAt

            verify(mockS3Client).getObjectMetadata(s3Key)
        }

    /** Start cancelScheduledDraftMessage tests **/

    @Test
    fun `cancelScheduledDraftMessage() should return draft ID on success`() =
        runTest {
            val draftMessageKey = "identityId/email/$emailAddressId/draft/$draftId"
            val mutationResponse = DataFactory.cancelScheduledDraftMessageResponse(draftMessageKey)

            mockApiClient.stub {
                onBlocking { cancelScheduledDraftMessageMutation(any()) } doReturn mutationResponse
            }

            val request =
                CancelScheduledDraftMessageRequest(
                    draftMessageKey = draftMessageKey,
                    emailAddressId = emailAddressId,
                )

            val result = instanceUnderTest.cancelScheduledDraftMessage(request)

            result shouldBe draftId

            verify(mockApiClient).cancelScheduledDraftMessageMutation(
                check {
                    it.draftMessageKey shouldBe draftMessageKey
                    it.emailAddressId shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `cancelScheduledDraftMessage() should extract draft ID from full key path`() =
        runTest {
            val customDraftId = "customDraftId123"
            val draftMessageKey = "identityId/email/$emailAddressId/draft/$customDraftId"
            val mutationResponse = DataFactory.cancelScheduledDraftMessageResponse(draftMessageKey)

            mockApiClient.stub {
                onBlocking { cancelScheduledDraftMessageMutation(any()) } doReturn mutationResponse
            }

            val request =
                CancelScheduledDraftMessageRequest(
                    draftMessageKey = draftMessageKey,
                    emailAddressId = emailAddressId,
                )

            val result = instanceUnderTest.cancelScheduledDraftMessage(request)

            result shouldBe customDraftId

            verify(mockApiClient).cancelScheduledDraftMessageMutation(any())
        }

    @Test
    fun `cancelScheduledDraftMessage() should throw when mutation returns errors`() =
        runTest {
            val draftMessageKey = "identityId/email/$emailAddressId/draft/$draftId"

            val errorResponse =
                GraphQLResponse<CancelScheduledDraftMessageMutation.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "InvalidEmailAddressError",
                            emptyList(),
                            emptyList(),
                            mapOf("errorType" to "InvalidEmailAddressError"),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking { cancelScheduledDraftMessageMutation(any()) } doReturn errorResponse
            }

            val request =
                CancelScheduledDraftMessageRequest(
                    draftMessageKey = draftMessageKey,
                    emailAddressId = emailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                instanceUnderTest.cancelScheduledDraftMessage(request)
            }

            verify(mockApiClient).cancelScheduledDraftMessageMutation(any())
        }

    @Test
    fun `cancelScheduledDraftMessage() should throw FailedException when response data is null`() =
        runTest {
            val draftMessageKey = "identityId/email/$emailAddressId/draft/$draftId"

            val nullDataResponse =
                GraphQLResponse<CancelScheduledDraftMessageMutation.Data>(
                    null,
                    null,
                )

            mockApiClient.stub {
                onBlocking { cancelScheduledDraftMessageMutation(any()) } doReturn nullDataResponse
            }

            val request =
                CancelScheduledDraftMessageRequest(
                    draftMessageKey = draftMessageKey,
                    emailAddressId = emailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                instanceUnderTest.cancelScheduledDraftMessage(request)
            }

            verify(mockApiClient).cancelScheduledDraftMessageMutation(any())
        }

    @Test
    fun `cancelScheduledDraftMessage() should throw when API client throws exception`() =
        runTest {
            val draftMessageKey = "identityId/email/$emailAddressId/draft/$draftId"

            mockApiClient.stub {
                onBlocking { cancelScheduledDraftMessageMutation(any()) } doThrow RuntimeException("Network error")
            }

            val request =
                CancelScheduledDraftMessageRequest(
                    draftMessageKey = draftMessageKey,
                    emailAddressId = emailAddressId,
                )

            shouldThrow<RuntimeException> {
                instanceUnderTest.cancelScheduledDraftMessage(request)
            }

            verify(mockApiClient).cancelScheduledDraftMessageMutation(any())
        }

    @Test
    fun `cancelScheduledDraftMessage() should pass correct parameters to mutation`() =
        runTest {
            val draftMessageKey = "identityId/email/$emailAddressId/draft/$draftId"
            val customEmailAddressId = "customEmailAddressId"
            val mutationResponse = DataFactory.cancelScheduledDraftMessageResponse(draftMessageKey)

            mockApiClient.stub {
                onBlocking { cancelScheduledDraftMessageMutation(any()) } doReturn mutationResponse
            }

            val request =
                CancelScheduledDraftMessageRequest(
                    draftMessageKey = draftMessageKey,
                    emailAddressId = customEmailAddressId,
                )

            instanceUnderTest.cancelScheduledDraftMessage(request)

            verify(mockApiClient).cancelScheduledDraftMessageMutation(
                check {
                    it.draftMessageKey shouldBe draftMessageKey
                    it.emailAddressId shouldBe customEmailAddressId
                },
            )
        }

    @Test
    fun `cancelScheduledDraftMessage() should throw InvalidArgumentException for specific error type`() =
        runTest {
            val draftMessageKey = "identityId/email/$emailAddressId/draft/$draftId"

            val errorResponse =
                GraphQLResponse<CancelScheduledDraftMessageMutation.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "InvalidArgument",
                            emptyList(),
                            emptyList(),
                            mapOf("errorType" to "InvalidArgument"),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking { cancelScheduledDraftMessageMutation(any()) } doReturn errorResponse
            }

            val request =
                CancelScheduledDraftMessageRequest(
                    draftMessageKey = draftMessageKey,
                    emailAddressId = emailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.InvalidArgumentException> {
                instanceUnderTest.cancelScheduledDraftMessage(request)
            }

            verify(mockApiClient).cancelScheduledDraftMessageMutation(any())
        }

    /** Start listScheduledDraftMessagesForEmailAddressId tests **/

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should return list of scheduled draft messages successfully`() =
        runTest {
            val draftMessageKey1 = "identityId/email/$emailAddressId/draft/draft1"
            val draftMessageKey2 = "identityId/email/$emailAddressId/draft/draft2"
            val sendAt1 = Date(System.currentTimeMillis() + 86400000)
            val sendAt2 = Date(System.currentTimeMillis() + 172800000)

            val scheduledMessages =
                listOf(
                    DataFactory.getScheduledDraftMessage(
                        draftMessageKey = draftMessageKey1,
                        emailAddressId = emailAddressId,
                        sendAtEpochMs = sendAt1.time.toDouble(),
                        state = ScheduledDraftMessageState.SCHEDULED,
                    ),
                    DataFactory.getScheduledDraftMessage(
                        draftMessageKey = draftMessageKey2,
                        emailAddressId = emailAddressId,
                        sendAtEpochMs = sendAt2.time.toDouble(),
                        state = ScheduledDraftMessageState.SCHEDULED,
                    ),
                )

            val queryResponse = DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(scheduledMessages)

            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doReturn queryResponse
            }

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                )

            val result = instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)

            result.items.size shouldBe 2
            result.items[0].id shouldBe draftMessageKey1.substringAfterLast('/')
            result.items[0].emailAddressId shouldBe emailAddressId
            result.items[0].state shouldBe ScheduledDraftMessageStateEntity.SCHEDULED
            result.items[1].id shouldBe draftMessageKey2.substringAfterLast('/')
            result.items[1].emailAddressId shouldBe emailAddressId
            result.nextToken shouldBe null

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(
                check {
                    it.emailAddressId shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should return empty list when no scheduled messages exist`() =
        runTest {
            val queryResponse = DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(emptyList())

            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doReturn queryResponse
            }

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                )

            val result = instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)

            result.items shouldBe emptyList()
            result.nextToken shouldBe null

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(any())
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should handle pagination with nextToken`() =
        runTest {
            val draftMessageKey = "identityId/email/$emailAddressId/draft/draft1"
            val nextTokenValue = "nextTokenValue123"

            val scheduledMessages =
                listOf(
                    DataFactory.getScheduledDraftMessage(
                        draftMessageKey = draftMessageKey,
                        emailAddressId = emailAddressId,
                        sendAtEpochMs = Date().time.toDouble(),
                    ),
                )

            val queryResponse =
                DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(scheduledMessages, nextTokenValue)

            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doReturn queryResponse
            }

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                    nextToken = "previousToken",
                )

            val result = instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)

            result.items.size shouldBe 1
            result.nextToken shouldBe nextTokenValue

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(
                check {
                    it.emailAddressId shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should respect limit parameter`() =
        runTest {
            val queryResponse = DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(emptyList())

            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doReturn queryResponse
            }

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                    limit = 10,
                )

            instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(
                check {
                    it.emailAddressId shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should throw when query returns errors`() =
        runTest {
            val errorResponse =
                GraphQLResponse<ListScheduledDraftMessagesForEmailAddressIdQuery.Data>(
                    null,
                    listOf(
                        GraphQLResponse.Error(
                            "InvalidEmailAddressError",
                            emptyList(),
                            emptyList(),
                            mapOf("errorType" to "InvalidEmailAddressError"),
                        ),
                    ),
                )

            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doReturn errorResponse
            }

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)
            }

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(any())
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should handle different message states`() =
        runTest {
            val draftKey1 = "identityId/email/$emailAddressId/draft/draft1"
            val draftKey2 = "identityId/email/$emailAddressId/draft/draft2"
            val draftKey3 = "identityId/email/$emailAddressId/draft/draft3"

            val scheduledMessages =
                listOf(
                    DataFactory.getScheduledDraftMessage(
                        draftMessageKey = draftKey1,
                        emailAddressId = emailAddressId,
                        sendAtEpochMs = Date().time.toDouble(),
                        state = ScheduledDraftMessageState.SCHEDULED,
                    ),
                    DataFactory.getScheduledDraftMessage(
                        draftMessageKey = draftKey2,
                        emailAddressId = emailAddressId,
                        sendAtEpochMs = Date().time.toDouble(),
                        state = ScheduledDraftMessageState.SENT,
                    ),
                    DataFactory.getScheduledDraftMessage(
                        draftMessageKey = draftKey3,
                        emailAddressId = emailAddressId,
                        sendAtEpochMs = Date().time.toDouble(),
                        state = ScheduledDraftMessageState.CANCELLED,
                    ),
                )

            val queryResponse = DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(scheduledMessages)

            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doReturn queryResponse
            }

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                )

            val result = instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)

            result.items.size shouldBe 3
            result.items[0].state shouldBe ScheduledDraftMessageStateEntity.SCHEDULED
            result.items[1].state shouldBe ScheduledDraftMessageStateEntity.SENT
            result.items[2].state shouldBe ScheduledDraftMessageStateEntity.CANCELLED

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(any())
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should handle API client exception`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doThrow RuntimeException("Network error")
            }

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailMessageException> {
                instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)
            }

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(any())
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should propagate EmailMessageException`() =
        runTest {
            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doThrow
                    SudoEmailClient.EmailMessageException.FailedException("Failed")
            }

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.FailedException> {
                instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)
            }

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(any())
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should extract draft ID from key correctly`() =
        runTest {
            val draftMessageKey = "identityId/email/$emailAddressId/draft/myDraftId123"
            val expectedDraftId = "myDraftId123"

            val scheduledMessages =
                listOf(
                    DataFactory.getScheduledDraftMessage(
                        draftMessageKey = draftMessageKey,
                        emailAddressId = emailAddressId,
                        sendAtEpochMs = Date().time.toDouble(),
                    ),
                )

            val queryResponse = DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(scheduledMessages)

            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doReturn queryResponse
            }

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                )

            val result = instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)

            result.items[0].id shouldBe expectedDraftId

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(any())
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should filter by equal state`() =
        runTest {
            val queryResponse = DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(emptyList())

            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doReturn queryResponse
            }

            val filter =
                ScheduledDraftMessageFilterInputEntity(
                    state =
                        EqualStateFilterEntity(
                            equal = ScheduledDraftMessageStateEntity.SCHEDULED,
                        ),
                )

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                    filter = filter,
                )

            instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(
                check {
                    it.emailAddressId shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should filter by oneOf states`() =
        runTest {
            val draftKey1 = "identityId/email/$emailAddressId/draft/draft1"

            val scheduledMessages =
                listOf(
                    DataFactory.getScheduledDraftMessage(
                        draftMessageKey = draftKey1,
                        emailAddressId = emailAddressId,
                        sendAtEpochMs = Date().time.toDouble(),
                        state = ScheduledDraftMessageState.SCHEDULED,
                    ),
                )

            val queryResponse = DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(scheduledMessages)

            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doReturn queryResponse
            }

            val filter =
                ScheduledDraftMessageFilterInputEntity(
                    state =
                        OneOfStateFilterEntity(
                            oneOf =
                                listOf(
                                    ScheduledDraftMessageStateEntity.SCHEDULED,
                                    ScheduledDraftMessageStateEntity.FAILED,
                                ),
                        ),
                )

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                    filter = filter,
                )

            val result = instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)

            result.items.size shouldBe 1
            result.items[0].state shouldBe ScheduledDraftMessageStateEntity.SCHEDULED

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(
                check {
                    it.emailAddressId shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should filter by notEqual state`() =
        runTest {
            val draftKey1 = "identityId/email/$emailAddressId/draft/draft1"
            val draftKey2 = "identityId/email/$emailAddressId/draft/draft2"

            val scheduledMessages =
                listOf(
                    DataFactory.getScheduledDraftMessage(
                        draftMessageKey = draftKey1,
                        emailAddressId = emailAddressId,
                        sendAtEpochMs = Date().time.toDouble(),
                        state = ScheduledDraftMessageState.SENT,
                    ),
                    DataFactory.getScheduledDraftMessage(
                        draftMessageKey = draftKey2,
                        emailAddressId = emailAddressId,
                        sendAtEpochMs = Date().time.toDouble(),
                        state = ScheduledDraftMessageState.CANCELLED,
                    ),
                )

            val queryResponse = DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(scheduledMessages)

            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doReturn queryResponse
            }

            val filter =
                ScheduledDraftMessageFilterInputEntity(
                    state =
                        NotEqualStateFilterEntity(
                            notEqual = ScheduledDraftMessageStateEntity.SCHEDULED,
                        ),
                )

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                    filter = filter,
                )

            val result = instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)

            result.items.size shouldBe 2
            result.items[0].state shouldBe ScheduledDraftMessageStateEntity.SENT
            result.items[1].state shouldBe ScheduledDraftMessageStateEntity.CANCELLED

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(
                check {
                    it.emailAddressId shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should filter by notOneOf states`() =
        runTest {
            val draftKey1 = "identityId/email/$emailAddressId/draft/draft1"

            val scheduledMessages =
                listOf(
                    DataFactory.getScheduledDraftMessage(
                        draftMessageKey = draftKey1,
                        emailAddressId = emailAddressId,
                        sendAtEpochMs = Date().time.toDouble(),
                        state = ScheduledDraftMessageState.SENT,
                    ),
                )

            val queryResponse = DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(scheduledMessages)

            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doReturn queryResponse
            }

            val filter =
                ScheduledDraftMessageFilterInputEntity(
                    state =
                        NotOneOfStateFilterEntity(
                            notOneOf =
                                listOf(
                                    ScheduledDraftMessageStateEntity.SCHEDULED,
                                    ScheduledDraftMessageStateEntity.CANCELLED,
                                    ScheduledDraftMessageStateEntity.FAILED,
                                ),
                        ),
                )

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                    filter = filter,
                )

            val result = instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)

            result.items.size shouldBe 1
            result.items[0].state shouldBe ScheduledDraftMessageStateEntity.SENT

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(
                check {
                    it.emailAddressId shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should handle filter with pagination`() =
        runTest {
            val draftKey1 = "identityId/email/$emailAddressId/draft/draft1"
            val nextTokenValue = "nextToken123"

            val scheduledMessages =
                listOf(
                    DataFactory.getScheduledDraftMessage(
                        draftMessageKey = draftKey1,
                        emailAddressId = emailAddressId,
                        sendAtEpochMs = Date().time.toDouble(),
                        state = ScheduledDraftMessageState.SCHEDULED,
                    ),
                )

            val queryResponse =
                DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(scheduledMessages, nextTokenValue)

            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doReturn queryResponse
            }

            val filter =
                ScheduledDraftMessageFilterInputEntity(
                    state =
                        EqualStateFilterEntity(
                            equal = ScheduledDraftMessageStateEntity.SCHEDULED,
                        ),
                )

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                    filter = filter,
                    limit = 10,
                    nextToken = "previousToken",
                )

            val result = instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)

            result.items.size shouldBe 1
            result.nextToken shouldBe nextTokenValue

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(
                check {
                    it.emailAddressId shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should handle null filter parameter`() =
        runTest {
            val queryResponse = DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(emptyList())

            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doReturn queryResponse
            }

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                    filter = null,
                )

            instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(
                check {
                    it.emailAddressId shouldBe emailAddressId
                },
            )
        }

    @Test
    fun `listScheduledDraftMessagesForEmailAddressId() should filter FAILED state correctly`() =
        runTest {
            val draftKey1 = "identityId/email/$emailAddressId/draft/draft1"

            val scheduledMessages =
                listOf(
                    DataFactory.getScheduledDraftMessage(
                        draftMessageKey = draftKey1,
                        emailAddressId = emailAddressId,
                        sendAtEpochMs = Date().time.toDouble(),
                        state = ScheduledDraftMessageState.FAILED,
                    ),
                )

            val queryResponse = DataFactory.listScheduledDraftMessagesForEmailAddressIdQueryResponse(scheduledMessages)

            mockApiClient.stub {
                onBlocking { listScheduledDraftMessagesForEmailAddressIdQuery(any()) } doReturn queryResponse
            }

            val filter =
                ScheduledDraftMessageFilterInputEntity(
                    state =
                        EqualStateFilterEntity(
                            equal = ScheduledDraftMessageStateEntity.FAILED,
                        ),
                )

            val request =
                ListScheduledDraftMessagesForEmailAddressIdInputRequest(
                    emailAddressId = emailAddressId,
                    filter = filter,
                )

            val result = instanceUnderTest.listScheduledDraftMessagesForEmailAddressId(request)

            result.items.size shouldBe 1
            result.items[0].state shouldBe ScheduledDraftMessageStateEntity.FAILED

            verify(mockApiClient).listScheduledDraftMessagesForEmailAddressIdQuery(any())
        }
}
