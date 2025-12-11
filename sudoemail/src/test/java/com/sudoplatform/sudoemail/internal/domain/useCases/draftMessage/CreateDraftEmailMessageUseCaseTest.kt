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
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
import com.sudoplatform.sudoemail.internal.util.EmailMessageDataProcessor
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.secure.EmailCryptoService
import com.sudoplatform.sudoemail.secure.SealingService
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
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

/**
 * Test the correct operation of [CreateDraftEmailMessageUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class CreateDraftEmailMessageUseCaseTest : BaseTests() {
    private val senderEmailAddressId = mockEmailAddressId
    private val rfc822Data = DataFactory.unsealedHeaderDetailsString.toByteArray()

    private val mockEmailAddress by before {
        EntityDataFactory.getSealedEmailAddressEntity(
            id = senderEmailAddressId,
        )
    }

    private val mockEmailMessageDataProcessor by before {
        mock<EmailMessageDataProcessor>()
    }

    private val mockDraftEmailMessageService by before {
        mock<DraftEmailMessageService>()
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
        CreateDraftEmailMessageUseCase(
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
    fun `execute() should execute SaveDraftEmailMessageUseCase and return draft ID`() =
        runTest {
            val input =
                CreateDraftEmailMessageUseCaseInput(
                    emailAddressId = senderEmailAddressId,
                    rfc822Data = rfc822Data,
                )

            val draftId = useCase.execute(input)

            draftId shouldNotBe null
            draftId.isNotEmpty() shouldBe true

            verify(mockEmailAddressService).get(
                check {
                    it.id shouldBe senderEmailAddressId
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
                CreateDraftEmailMessageUseCaseInput(
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
    fun `execute() should throw when symmetric key not found`() =
        runTest {
            mockServiceKeyManager.stub {
                on { getCurrentSymmetricKeyId() } doReturn null
            }

            val input =
                CreateDraftEmailMessageUseCaseInput(
                    emailAddressId = senderEmailAddressId,
                    rfc822Data = rfc822Data,
                )

            val exception =
                shouldThrow<KeyNotFoundException> {
                    useCase.execute(input)
                }

            exception.message shouldBe StringConstants.SYMMETRIC_KEY_NOT_FOUND_ERROR_MSG

            verify(mockEmailAddressService).get(any())
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
        }

    @Test
    fun `execute() should throw when SaveDraftEmailMessageUseCase fails`() =
        runTest {
            mockSaveDraftEmailMessageUseCase.stub {
                onBlocking { execute(any()) } doThrow RuntimeException("Error")
            }

            val input =
                CreateDraftEmailMessageUseCaseInput(
                    emailAddressId = senderEmailAddressId,
                    rfc822Data = rfc822Data,
                )

            shouldThrow<RuntimeException> {
                useCase.execute(input)
            }

            verify(mockEmailAddressService).get(any())
            verify(mockServiceKeyManager).getCurrentSymmetricKeyId()
            verify(mockSaveDraftEmailMessageUseCase).execute(
                check {
                    it.s3Key shouldContain senderEmailAddressId
                    it.symmetricKeyId shouldBe mockSymmetricKeyId
                    it.rfc822Data shouldBe rfc822Data
                },
            )
        }
}
