/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.internal.domain.entities.common.OwnerEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasEntityRealAddressType
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasEntityStatus
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.GetEmailAliasByIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.SealedEmailAliasEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Test the correct operation of [GetEmailAliasByIdUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class GetEmailAliasByIdUseCaseTest : BaseTests() {
    private val mockEmailAliasService by before {
        mock<EmailAliasService>()
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>()
    }

    private val useCase by before {
        GetEmailAliasByIdUseCase(
            emailAliasService = mockEmailAliasService,
            serviceKeyManager = mockServiceKeyManager,
            logger = mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockEmailAliasService,
        )
    }

    private fun sealedEmailAlias(id: String = "mockEmailAliasId") =
        SealedEmailAliasEntity(
            id = id,
            owner = "mockOwner",
            owners = listOf(OwnerEntity(id = "mockOwner", issuer = "issuer")),
            identityId = "mockIdentityId",
            aliasAddress = "mask@example.com",
            realAddress = "real@example.com",
            realAddressType = EmailAliasEntityRealAddressType.EXTERNAL,
            status = EmailAliasEntityStatus.ENABLED,
            inboundReceived = 0,
            inboundDelivered = 0,
            outboundReceived = 0,
            outboundDelivered = 0,
            spamCount = 0,
            virusCount = 0,
            expiresAt = null,
            version = 1,
            createdAt = Date(1),
            updatedAt = Date(2),
            sealedMetadata = null,
        )

    @Test
    fun `execute() should return unsealed email alias when successful`() =
        runTest {
            mockEmailAliasService.stub {
                onBlocking { getById(any()) } doReturn sealedEmailAlias()
            }

            val input =
                GetEmailAliasByIdUseCaseInput(
                    id = "mockEmailAliasId",
                )

            val result = useCase.execute(input)

            result shouldNotBe null
            result.id shouldBe "mockEmailAliasId"
            result.aliasAddress shouldBe "mask@example.com"
            result.realAddress shouldBe "real@example.com"
            result.status shouldBe EmailAliasEntityStatus.ENABLED

            verify(mockEmailAliasService).getById(
                GetEmailAliasByIdRequest(id = "mockEmailAliasId"),
            )
        }

    @Test
    fun `execute() should throw EmailAliasNotFoundException when service returns null`() =
        runTest {
            mockEmailAliasService.stub {
                onBlocking { getById(any()) } doReturn null
            }

            val input =
                GetEmailAliasByIdUseCaseInput(
                    id = "nonExistentId",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.EmailAliasNotFoundException> {
                useCase.execute(input)
            }

            verify(mockEmailAliasService).getById(
                GetEmailAliasByIdRequest(id = "nonExistentId"),
            )
        }

    @Test
    fun `execute() should rethrow when service throws`() =
        runTest {
            mockEmailAliasService.stub {
                onBlocking { getById(any()) } doThrow
                    SudoEmailClient.EmailAliasException.AuthenticationException("Not authorized")
            }

            val input =
                GetEmailAliasByIdUseCaseInput(
                    id = "mockEmailAliasId",
                )

            shouldThrow<SudoEmailClient.EmailAliasException.AuthenticationException> {
                useCase.execute(input)
            }

            verify(mockEmailAliasService).getById(any())
        }
}
