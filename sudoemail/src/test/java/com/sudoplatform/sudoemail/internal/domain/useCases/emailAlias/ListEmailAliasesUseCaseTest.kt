/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailAlias

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.data.EntityDataFactory
import com.sudoplatform.sudoemail.internal.domain.entities.common.ListAPIResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasEntityRealAddressType
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasEntityStatus
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasFilterInputEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EmailAliasService
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.EqualStatusFilterEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.ListEmailAliasesOutput
import com.sudoplatform.sudoemail.internal.domain.entities.emailAlias.NotOneOfRealAddressTypeFilterEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.types.EmailAliasRealAddressType
import com.sudoplatform.sudoemail.types.EmailAliasStatus
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudoemail.types.inputs.EmailAliasFilterInput
import com.sudoplatform.sudoemail.types.inputs.EqualStatusFilter
import com.sudoplatform.sudoemail.types.inputs.NotOneOfRealAddressTypeFilter
import io.kotlintest.fail
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
 * Test the correct operation of [ListEmailAliasesUseCase]
 * using mocks and spies.
 */
@RunWith(RobolectricTestRunner::class)
class ListEmailAliasesUseCaseTest : BaseTests() {
    private val sealedEmailAliasListEntity by before {
        EntityDataFactory.getSealedEmailAliasListEntity()
    }

    private val mockEmailAliasService by before {
        mock<EmailAliasService>().stub {
            onBlocking { list(any()) } doReturn
                ListEmailAliasesOutput(
                    items = listOf(sealedEmailAliasListEntity),
                    nextToken = null,
                )
        }
    }

    private val mockServiceKeyManager by before {
        mock<ServiceKeyManager>().stub {
            on { decryptWithSymmetricKeyId(any(), any()) } doReturn "{\"key\": \"unsealed\"}".toByteArray()
        }
    }

    private val useCase by before {
        ListEmailAliasesUseCase(
            mockEmailAliasService,
            mockServiceKeyManager,
            mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(
            mockEmailAliasService,
            mockServiceKeyManager,
        )
    }

    @Test
    fun `execute() should return success result with unsealed aliases`() =
        runTest {
            val input =
                ListEmailAliasesUseCaseInput(
                    sudoId = null,
                    filter = null,
                    limit = null,
                    nextToken = null,
                )

            val listAPIResultEntity = useCase.execute(input)

            listAPIResultEntity shouldNotBe null
            when (listAPIResultEntity) {
                is ListAPIResultEntity.Success -> {
                    val successResult = listAPIResultEntity.result
                    successResult.items.size shouldBe 1
                    successResult.items[0].id shouldBe sealedEmailAliasListEntity.id
                    successResult.items[0].aliasAddress shouldBe sealedEmailAliasListEntity.aliasAddress
                    successResult.items[0].owner shouldBe sealedEmailAliasListEntity.owner
                    successResult.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAliasService).list(
                check {
                    it.sudoId shouldBe null
                    it.filter shouldBe null
                    it.limit shouldBe null
                    it.nextToken shouldBe null
                },
            )
        }

    @Test
    fun `execute() should return empty success result when no aliases exist`() =
        runTest {
            mockEmailAliasService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailAliasesOutput(
                        items = emptyList(),
                        nextToken = null,
                    )
            }
            val input =
                ListEmailAliasesUseCaseInput(
                    sudoId = null,
                    filter = null,
                    limit = null,
                    nextToken = null,
                )

            val listAPIResultEntity = useCase.execute(input)

            listAPIResultEntity shouldNotBe null
            when (listAPIResultEntity) {
                is ListAPIResultEntity.Success -> {
                    val successResult = listAPIResultEntity.result
                    successResult.items.size shouldBe 0
                    successResult.nextToken shouldBe null
                }

                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAliasService).list(
                check {
                    it.sudoId shouldBe null
                    it.filter shouldBe null
                    it.limit shouldBe null
                    it.nextToken shouldBe null
                },
            )
        }

    @Test
    fun `execute() should handle sudoId parameter correctly`() =
        runTest {
            val sudoId = "mockSudoId"
            val input =
                ListEmailAliasesUseCaseInput(
                    sudoId = sudoId,
                    filter = null,
                    limit = null,
                    nextToken = null,
                )

            val listAPIResultEntity = useCase.execute(input)

            listAPIResultEntity shouldNotBe null
            when (listAPIResultEntity) {
                is ListAPIResultEntity.Success -> {
                    val successResult = listAPIResultEntity.result
                    successResult.items.size shouldBe 1
                    successResult.items[0].id shouldBe sealedEmailAliasListEntity.id
                    successResult.items[0].aliasAddress shouldBe sealedEmailAliasListEntity.aliasAddress
                    successResult.items[0].owner shouldBe sealedEmailAliasListEntity.owner
                    successResult.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAliasService).list(
                check {
                    it.sudoId shouldBe sudoId
                    it.filter shouldBe null
                    it.limit shouldBe null
                    it.nextToken shouldBe null
                },
            )
        }

    @Test
    fun `execute() should handle filter parameter correctly`() =
        runTest {
            val filter =
                EmailAliasFilterInput(
                    status = EqualStatusFilter(EmailAliasStatus.DISABLED),
                    realAddressType = NotOneOfRealAddressTypeFilter(listOf(EmailAliasRealAddressType.EXTERNAL)),
                )
            val input =
                ListEmailAliasesUseCaseInput(
                    sudoId = null,
                    filter = filter,
                    limit = null,
                    nextToken = null,
                )

            val listAPIResultEntity = useCase.execute(input)

            listAPIResultEntity shouldNotBe null
            when (listAPIResultEntity) {
                is ListAPIResultEntity.Success -> {
                    val successResult = listAPIResultEntity.result
                    successResult.items.size shouldBe 1
                    successResult.items[0].id shouldBe sealedEmailAliasListEntity.id
                    successResult.items[0].aliasAddress shouldBe sealedEmailAliasListEntity.aliasAddress
                    successResult.items[0].owner shouldBe sealedEmailAliasListEntity.owner
                    successResult.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAliasService).list(
                check {
                    it.sudoId shouldBe null
                    it.filter shouldBe
                        EmailAliasFilterInputEntity(
                            status = EqualStatusFilterEntity(EmailAliasEntityStatus.DISABLED),
                            addressType = NotOneOfRealAddressTypeFilterEntity(listOf(EmailAliasEntityRealAddressType.EXTERNAL)),
                        )
                    it.limit shouldBe null
                    it.nextToken shouldBe null
                },
            )
        }

    @Test
    fun `execute() should handle limit parameter correctly`() =
        runTest {
            val limit = 2
            val input =
                ListEmailAliasesUseCaseInput(
                    sudoId = null,
                    filter = null,
                    limit = limit,
                    nextToken = null,
                )

            val listAPIResultEntity = useCase.execute(input)

            listAPIResultEntity shouldNotBe null
            when (listAPIResultEntity) {
                is ListAPIResultEntity.Success -> {
                    val successResult = listAPIResultEntity.result
                    successResult.items.size shouldBe 1
                    successResult.items[0].id shouldBe sealedEmailAliasListEntity.id
                    successResult.items[0].aliasAddress shouldBe sealedEmailAliasListEntity.aliasAddress
                    successResult.items[0].owner shouldBe sealedEmailAliasListEntity.owner
                    successResult.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAliasService).list(
                check {
                    it.sudoId shouldBe null
                    it.filter shouldBe null
                    it.limit shouldBe limit
                    it.nextToken shouldBe null
                },
            )
        }

    @Test
    fun `execute() should handle nextToken parameter correctly`() =
        runTest {
            val nextToken = "mockNextToken"
            val input =
                ListEmailAliasesUseCaseInput(
                    sudoId = null,
                    filter = null,
                    limit = null,
                    nextToken = nextToken,
                )

            val listAPIResultEntity = useCase.execute(input)

            listAPIResultEntity shouldNotBe null
            when (listAPIResultEntity) {
                is ListAPIResultEntity.Success -> {
                    val successResult = listAPIResultEntity.result
                    successResult.items.size shouldBe 1
                    successResult.items[0].id shouldBe sealedEmailAliasListEntity.id
                    successResult.items[0].aliasAddress shouldBe sealedEmailAliasListEntity.aliasAddress
                    successResult.items[0].owner shouldBe sealedEmailAliasListEntity.owner
                    successResult.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAliasService).list(
                check {
                    it.sudoId shouldBe null
                    it.filter shouldBe null
                    it.limit shouldBe null
                    it.nextToken shouldBe nextToken
                },
            )
        }

    @Test
    fun `execute() should handle multiple aliases correctly`() =
        runTest {
            val aliasList =
                listOf(
                    EntityDataFactory.getSealedEmailAliasListEntity(
                        id = "id-1",
                        metadata =
                            SealedAttributeEntity(
                                algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                                keyId = "keyId",
                                plainTextType = "string",
                                base64EncodedSealedData = mockSeal("{\"key\": \"unsealed\"}"),
                            ),
                    ),
                    EntityDataFactory.getSealedEmailAliasListEntity(id = "id-2"),
                    EntityDataFactory.getSealedEmailAliasListEntity(id = "id-3"),
                )
            mockEmailAliasService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailAliasesOutput(
                        items = aliasList,
                        nextToken = null,
                    )
            }

            val input =
                ListEmailAliasesUseCaseInput(
                    sudoId = null,
                    filter = null,
                    limit = null,
                    nextToken = null,
                )

            val listAPIResultEntity = useCase.execute(input)

            listAPIResultEntity shouldNotBe null
            when (listAPIResultEntity) {
                is ListAPIResultEntity.Success -> {
                    val successResult = listAPIResultEntity.result
                    successResult.items.size shouldBe aliasList.size
                    successResult.items[0].id shouldBe aliasList[0].id
                    successResult.items[0].aliasAddress shouldBe aliasList[0].aliasAddress
                    successResult.items[0].owner shouldBe aliasList[0].owner
                    successResult.items[0].metadata shouldBe mapOf("key" to "unsealed")
                    successResult.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAliasService).list(
                check {
                    it.sudoId shouldBe null
                    it.filter shouldBe null
                    it.limit shouldBe null
                    it.nextToken shouldBe null
                },
            )
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should include nextToken if required`() =
        runTest {
            val nextToken = "dummyToken"
            mockEmailAliasService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailAliasesOutput(
                        items = listOf(sealedEmailAliasListEntity),
                        nextToken = nextToken,
                    )
            }
            val input =
                ListEmailAliasesUseCaseInput(
                    sudoId = null,
                    filter = null,
                    limit = null,
                    nextToken = null,
                )

            val listAPIResultEntity = useCase.execute(input)

            listAPIResultEntity shouldNotBe null
            when (listAPIResultEntity) {
                is ListAPIResultEntity.Success -> {
                    val successResult = listAPIResultEntity.result
                    successResult.items.size shouldBe 1
                    successResult.items[0].id shouldBe sealedEmailAliasListEntity.id
                    successResult.items[0].aliasAddress shouldBe sealedEmailAliasListEntity.aliasAddress
                    successResult.items[0].owner shouldBe sealedEmailAliasListEntity.owner
                    successResult.nextToken shouldBe nextToken
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAliasService).list(
                check {
                    it.sudoId shouldBe null
                    it.filter shouldBe null
                    it.limit shouldBe null
                    it.nextToken shouldBe null
                },
            )
        }

    @Test
    fun `execute() should return partial result if unsealing fails for some alias`() =
        runTest {
            val aliasList =
                listOf(
                    EntityDataFactory.getSealedEmailAliasListEntity(
                        id = "id-1",
                        metadata =
                            SealedAttributeEntity(
                                algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                                keyId = "keyId",
                                plainTextType = "string",
                                base64EncodedSealedData = mockSeal("My Email"),
                            ),
                    ),
                    EntityDataFactory.getSealedEmailAliasListEntity(id = "id-2"),
                    EntityDataFactory.getSealedEmailAliasListEntity(id = "id-3"),
                )
            mockEmailAliasService.stub {
                onBlocking { list(any()) } doReturn
                    ListEmailAliasesOutput(
                        items = aliasList,
                        nextToken = null,
                    )
            }
            mockServiceKeyManager.stub {
                on {
                    decryptWithSymmetricKeyId(any(), any())
                } doThrow RuntimeException("Decryption failed")
            }
            val input =
                ListEmailAliasesUseCaseInput(
                    sudoId = null,
                    filter = null,
                    limit = null,
                    nextToken = null,
                )

            val listAPIResultEntity = useCase.execute(input)

            listAPIResultEntity shouldNotBe null
            when (listAPIResultEntity) {
                is ListAPIResultEntity.Partial -> {
                    val partialResult = listAPIResultEntity.result
                    partialResult.items.size shouldBe aliasList.size - 1
                    partialResult.items[0].id shouldBe aliasList[1].id
                    partialResult.items[0].aliasAddress shouldBe aliasList[1].aliasAddress
                    partialResult.items[0].owner shouldBe aliasList[1].owner
                    partialResult.failed[0].partial.id shouldBe aliasList[0].id
                    partialResult.nextToken shouldBe null
                }
                else -> {
                    fail("Unexpected ListAPIResult")
                }
            }

            verify(mockEmailAliasService).list(
                check {
                    it.sudoId shouldBe null
                    it.filter shouldBe null
                    it.limit shouldBe null
                    it.nextToken shouldBe null
                },
            )
            verify(mockServiceKeyManager).decryptWithSymmetricKeyId(any(), any())
        }

    @Test
    fun `execute() should throw when service throws`() =
        runTest {
            mockEmailAliasService.stub {
                onBlocking { list(any()) } doThrow RuntimeException("Service failed")

                val input =
                    ListEmailAliasesUseCaseInput(
                        sudoId = null,
                        filter = null,
                        limit = null,
                        nextToken = null,
                    )
                shouldThrow<RuntimeException> {
                    useCase.execute(input)
                }

                verify(mockEmailAliasService).list(
                    check {
                        it.sudoId shouldBe null
                        it.filter shouldBe null
                        it.limit shouldBe null
                        it.nextToken shouldBe null
                    },
                )
            }
        }
}
