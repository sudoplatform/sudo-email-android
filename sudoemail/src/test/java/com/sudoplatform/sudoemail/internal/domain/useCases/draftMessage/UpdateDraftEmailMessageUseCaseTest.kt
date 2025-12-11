/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.draftMessage

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.DataFactory
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.configuration.ConfigurationDataService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.DraftEmailMessageService
import com.sudoplatform.sudoemail.internal.domain.entities.draftMessage.GetDraftEmailMessageResponse
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.s3.DefaultS3Client
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
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
 * Test the correct operation of [UpdateDraftEmailMessageUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class UpdateDraftEmailMessageUseCaseTest : BaseTests() {
    private val senderEmailAddressId = mockEmailAddressId
    private val draftId = mockDraftId
    private val mockS3Key = DefaultS3Client.constructS3KeyForDraftEmailMessage(senderEmailAddressId, draftId)
    private val rfc822Data = DataFactory.unsealedHeaderDetailsString.toByteArray()
    private val mockSealedData = mockSeal(DataFactory.unsealedHeaderDetailsString).toByteArray()

    private val mockEmailAddress by before {
        EntityDataFactory.getSealedEmailAddressEntity(
            id = senderEmailAddressId,
        )
    }

    private val mockGetDraftEmailMessageResponse by before {
        GetDraftEmailMessageResponse(
            s3Key = mockS3Key,
            rfc822Data = mockSealedData,
            keyId = mockSymmetricKeyId,
            updatedAt = Date(1L),
        )
    }

    private val mockEmailMessageDataProcessor by before {
        mock<EmailMessageDataProcessor>()
    }

    private val mockDraftEmailMessageService by before {
        mock<DraftEmailMessageService>().stub {
            onBlocking { get(any()) } doReturn mockGetDraftEmailMessageResponse
        }
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking { get(any()) } doReturn mockEmailAddress
        }
    }

    private val mockConfigurationDataService by before {
        mock<ConfigurationDataService>()
    }

    private val mockEmailCryptoService by before {
        mock<EmailCryptoService>()
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { getCurrentSymmetricKeyId() } doReturn mockSymmetricKeyId
        }
    }

    private val mockSealingService by before {
        mock<SealingService>()
    }

    private val mockSaveDraftEmailMessageUseCase by before {
        mock<SaveDraftEmailMessageUseCase>().stub {
            onBlocking {
                execute(any())
            } doReturn "s3Key"
        }
    }

    private val useCase by before {
        UpdateDraftEmailMessageUseCase(
            draftEmailMessageService = mockDraftEmailMessageService,
            emailAddressService = mockEmailAddressService,
            configurationDataService = mockConfigurationDataService,
            emailMessageDataProcessor = mockEmailMessageDataProcessor,
            serviceKeyManager = mockServiceKeyManager,
            sealingService = mockSealingService,
            logger = mockLogger,
            saveDraftEmailMessageUseCase = mockSaveDraftEmailMessageUseCase,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockDraftEmailMessageService,
            mockEmailAddressService,
            mockConfigurationDataService,
            mockEmailMessageDataProcessor,
            mockEmailCryptoService,
            mockServiceKeyManager,
            mockSealingService,
            mockSaveDraftEmailMessageUseCase,
        )
    }

    @Test
    fun `execute() should update draft and return draft ID`() =
        runTest {
            val input =
                UpdateDraftEmailMessageUseCaseInput(
                    draftId = draftId,
                    emailAddressId = senderEmailAddressId,
                    rfc822Data = rfc822Data,
                )

            val result = useCase.execute(input)

            result shouldBe draftId

            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe senderEmailAddressId
                },
            )
            verify(mockDraftEmailMessageService).get(
                check {
                    it.s3Key shouldBe mockS3Key
                },
            )
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSaveDraftEmailMessageUseCase).execute(
                check {
                    it.s3Key shouldContain senderEmailAddressId
                    it.s3Key shouldContain draftId
                    it.symmetricKeyId shouldBe mockSymmetricKeyId
                    it.rfc822Data shouldBe rfc822Data
                },
            )
        }

    @Test
    fun `execute() should throw when email address not found`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { get(any()) } doReturn null
            }

            val input =
                UpdateDraftEmailMessageUseCaseInput(
                    draftId = draftId,
                    emailAddressId = senderEmailAddressId,
                    rfc822Data = rfc822Data,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailAddressException.EmailAddressNotFoundException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.EMAIL_ADDRESS_NOT_FOUND_MSG

            verify(mockEmailAddressService).get(any())
        }

    @Test
    fun `execute() should throw when draft not found`() =
        runTest {
            mockDraftEmailMessageService.stub {
                onBlocking { get(any()) } doThrow SudoEmailClient.EmailMessageException.EmailMessageNotFoundException()
            }

            val input =
                UpdateDraftEmailMessageUseCaseInput(
                    draftId = draftId,
                    emailAddressId = senderEmailAddressId,
                    rfc822Data = rfc822Data,
                )

            shouldThrow<SudoEmailClient.EmailMessageException.EmailMessageNotFoundException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).get(any())
        }

    @Test
    fun `execute() should throw when symmetric key not found`() =
        runTest {
            mockServiceKeyManager.stub {
                on { getCurrentSymmetricKeyId() } doReturn null
            }

            val input =
                UpdateDraftEmailMessageUseCaseInput(
                    draftId = draftId,
                    emailAddressId = senderEmailAddressId,
                    rfc822Data = rfc822Data,
                )

            val exception =
                shouldThrow<KeyNotFoundException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.SYMMETRIC_KEY_NOT_FOUND_ERROR_MSG

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).get(any())
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        }

    @Test
    fun `execute() should throw when SaveDraftEmailMessageUseCase fails`() =
        runTest {
            mockSaveDraftEmailMessageUseCase.stub {
                onBlocking { execute(any()) } doThrow RuntimeException("Error")
            }

            val input =
                UpdateDraftEmailMessageUseCaseInput(
                    draftId = draftId,
                    emailAddressId = senderEmailAddressId,
                    rfc822Data = rfc822Data,
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).get(any())
            verify(mockDraftEmailMessageService).get(any())
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSaveDraftEmailMessageUseCase).execute(
                check {
                    it.s3Key shouldContain senderEmailAddressId
                    it.s3Key shouldContain draftId
                    it.symmetricKeyId shouldBe mockSymmetricKeyId
                    it.rfc822Data shouldBe rfc822Data
                },
            )
        }
}
