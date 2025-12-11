/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAddress

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.data.common.StringConstants
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.EmailAddressService
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

/**
 * Test the correct operation of [LookupEmailAddressesPublicInfoUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class LookupPublicInfoUseCaseTest : BaseTests() {
    private val address1 = "user1@$mockInternalDomain"
    private val address2 = "user2@$mockInternalDomain"
    private val address3 = "user3@$mockInternalDomain"

    private val publicInfoEntity1 by before {
        EntityDataFactory.getEmailAddressPublicInfoEntity(
            emailAddress = address1,
        )
    }

    private val publicInfoEntity2 by before {
        EntityDataFactory.getEmailAddressPublicInfoEntity(
            emailAddress = address2,
        )
    }

    private val publicInfoEntity3 by before {
        EntityDataFactory.getEmailAddressPublicInfoEntity(
            emailAddress = address3,
        )
    }

    private val mockEmailAddressService by before {
        mock<EmailAddressService>().stub {
            onBlocking { lookupPublicInfo(any()) } doReturn
                listOf(publicInfoEntity1, publicInfoEntity2, publicInfoEntity3)
        }
    }

    private val useCase by before {
        LookupEmailAddressesPublicInfoUseCase(
            emailAddressService = mockEmailAddressService,
            logger = mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractionsOnBaseMocks()
        verifyNoMoreInteractions(
            mockEmailAddressService,
        )
    }

    @Test
    fun `execute() should lookup public info for multiple addresses`() =
        runTest {
            val addresses = listOf(address1, address2, address3)

            val result =
                useCase.execute(
                    LookupEmailAddressesPublicInfoUseCaseInput(
                        addresses = addresses,
                    ),
                )

            result.size shouldBe 3
            result[0].emailAddress shouldBe address1
            result[1].emailAddress shouldBe address2
            result[2].emailAddress shouldBe address3

            verify(mockEmailAddressService).lookupPublicInfo(
                check {
                    it.emailAddresses shouldBe addresses
                },
            )
        }

    @Test
    fun `execute() should lookup public info for single address`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { lookupPublicInfo(any()) } doReturn listOf(publicInfoEntity1)
            }

            val addresses = listOf(address1)

            val result =
                useCase.execute(
                    LookupEmailAddressesPublicInfoUseCaseInput(
                        addresses = addresses,
                    ),
                )

            result.size shouldBe 1
            result[0].emailAddress shouldBe address1

            verify(mockEmailAddressService).lookupPublicInfo(
                check {
                    it.emailAddresses shouldBe addresses
                },
            )
        }

    @Test
    fun `execute() should return empty list when no addresses provided`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { lookupPublicInfo(any()) } doReturn emptyList()
            }

            val addresses = emptyList<String>()

            val result =
                useCase.execute(
                    LookupEmailAddressesPublicInfoUseCaseInput(
                        addresses = addresses,
                    ),
                )

            result.size shouldBe 0

            verify(mockEmailAddressService).lookupPublicInfo(
                check {
                    it.emailAddresses shouldBe emptyList()
                },
            )
        }

    @Test
    fun `execute() should throw when no public info found and throwIfNotAllInternal is true`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { lookupPublicInfo(any()) } doReturn emptyList()
            }

            val addresses = listOf(address1, address2)

            val useCase =
                LookupEmailAddressesPublicInfoUseCase(
                    emailAddressService = mockEmailAddressService,
                    logger = mockLogger,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.InNetworkAddressNotFoundException> {
                    useCase.execute(
                        LookupEmailAddressesPublicInfoUseCaseInput(
                            addresses = addresses,
                            throwIfNotAllInternal = true,
                        ),
                    )
                }

            exception.message shouldBe StringConstants.IN_NETWORK_EMAIL_ADDRESSES_NOT_FOUND_ERROR_MSG

            verify(mockEmailAddressService).lookupPublicInfo(
                check {
                    it.emailAddresses shouldBe addresses
                },
            )
        }

    @Test
    fun `execute() should throw when in-network address not found and throwIfNotAllInternal is true`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { lookupPublicInfo(any()) } doReturn listOf(publicInfoEntity1)
            }

            val addresses = listOf(address1, address2, address3)

            val useCase =
                LookupEmailAddressesPublicInfoUseCase(
                    emailAddressService = mockEmailAddressService,
                    logger = mockLogger,
                )

            val exception =
                shouldThrow<SudoEmailClient.EmailMessageException.InNetworkAddressNotFoundException> {
                    useCase.execute(
                        LookupEmailAddressesPublicInfoUseCaseInput(
                            addresses = addresses,
                            throwIfNotAllInternal = true,
                        ),
                    )
                }

            exception.message shouldBe StringConstants.IN_NETWORK_EMAIL_ADDRESSES_NOT_FOUND_ERROR_MSG

            verify(mockEmailAddressService).lookupPublicInfo(
                check {
                    it.emailAddresses shouldBe addresses
                },
            )
        }

    @Test
    fun `execute() should throw when email address service fails`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { lookupPublicInfo(any()) } doThrow RuntimeException("Service failed")
            }

            val addresses = listOf(address1, address2)

            shouldThrow<RuntimeException> {
                val result =
                    useCase.execute(
                        LookupEmailAddressesPublicInfoUseCaseInput(
                            addresses = addresses,
                        ),
                    )
            }

            verify(mockEmailAddressService).lookupPublicInfo(any())
        }

    @Test
    fun `execute() should handle duplicate addresses in input`() =
        runTest {
            val addressesWithDuplicates = listOf(address1, address1, address2)

            mockEmailAddressService.stub {
                onBlocking { lookupPublicInfo(any()) } doReturn listOf(publicInfoEntity1, publicInfoEntity2)
            }

            val result =
                useCase.execute(
                    LookupEmailAddressesPublicInfoUseCaseInput(
                        addresses = addressesWithDuplicates,
                    ),
                )

            result.size shouldBe 2

            verify(mockEmailAddressService).lookupPublicInfo(
                check {
                    it.emailAddresses shouldBe addressesWithDuplicates
                },
            )
        }

    @Test
    fun `execute() should handle network errors`() =
        runTest {
            mockEmailAddressService.stub {
                onBlocking { lookupPublicInfo(any()) } doThrow RuntimeException("Network error")
            }

            val addresses = listOf(address1)

            shouldThrow<RuntimeException> {
                useCase.execute(
                    LookupEmailAddressesPublicInfoUseCaseInput(
                        addresses = addresses,
                    ),
                )
            }

            verify(mockEmailAddressService).lookupPublicInfo(any())
        }

    @Test
    fun `execute() should preserve order of results from service`() =
        runTest {
            // Return in different order
            mockEmailAddressService.stub {
                onBlocking { lookupPublicInfo(any()) } doReturn
                    listOf(publicInfoEntity3, publicInfoEntity1, publicInfoEntity2)
            }

            val addresses = listOf(address1, address2, address3)

            val result =
                useCase.execute(
                    LookupEmailAddressesPublicInfoUseCaseInput(
                        addresses = addresses,
                    ),
                )

            result.size shouldBe 3
            result[0].emailAddress shouldBe address3
            result[1].emailAddress shouldBe address1
            result[2].emailAddress shouldBe address2

            verify(mockEmailAddressService).lookupPublicInfo(any())
        }
}
