/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMessage.cache

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.CacheFlushInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.CachePutInput
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RoomEmailMessageBodyCacheTest : BaseTests() {
    private val mockCacheDao = mock<EmailMessageBodyCacheDao>()
    private val mockSettingsDao by before {
        mock<CacheSettingsDao>().stub {
            onBlocking {
                get(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT)
            } doReturn
                CacheSettingsEntity(
                    CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT,
                    (10L * 1024 * 1024).toString(),
                )
        }
    }

    private val mockDatabase by before {
        mock<EmailMessageBodyCacheDatabase>().stub {
            on { cacheDao() } doReturn mockCacheDao
            on { settingsDao() } doReturn mockSettingsDao
        }
    }

    private lateinit var cache: RoomEmailMessageBodyCache
    private lateinit var tmpDir: File

    @Before
    fun setUp() {
        tmpDir = File(System.getProperty("java.io.tmpdir"), "cache-test-${System.nanoTime()}")
        tmpDir.mkdirs()
        File(tmpDir, "blobs").mkdirs()

        cache =
            RoomEmailMessageBodyCache(
                context = mockContext,
                logger = mockLogger,
                cacheStoragePath = tmpDir.absolutePath,
                initialCacheSizeLimitBytes = 10 * 1024 * 1024,
                largeMessageThresholdBytes = 1024,
                database = mockDatabase,
            )
    }

    @After
    fun fini() {
        tmpDir.deleteRecursively()
        verifyNoMoreInteractions(
            mockCacheDao,
            mockSettingsDao,
            mockDatabase,
        )
    }

    // -------------------------------------------------------------------------
    // get
    // -------------------------------------------------------------------------

    @Test
    fun `get returns null for a missing entry`() =
        runTest {
            mockCacheDao.stub {
                onBlocking { getByMessageId(any()) } doReturn null
            }

            val result = cache.get("nonexistent")

            result shouldBe null

            verify(mockDatabase).settingsDao()
            verify(mockDatabase).cacheDao()
            verify(mockSettingsDao).get(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT)
            verify(mockCacheDao).getByMessageId(check { it shouldBe "nonexistent" })
        }

    @Test
    fun `get returns blob for inline entry and updates lastAccessedAt`() =
        runTest {
            val blob = "sealed-content".toByteArray()
            val entity =
                EmailMessageBodyCacheEntity(
                    messageId = "msg-1",
                    sudoId = "sudo-1",
                    emailAddressId = "addr-1",
                    content = blob,
                    fsPath = null,
                    contentEncoding = "sudoplatform-crypto",
                    sizeBytes = blob.size.toLong(),
                    lastAccessedAt = 1000L,
                )
            mockCacheDao.stub {
                onBlocking { getByMessageId("msg-1") } doReturn entity
            }

            val result = cache.get("msg-1")

            result shouldNotBe null
            result!!.messageId shouldBe "msg-1"
            result.sudoId shouldBe "sudo-1"
            result.emailAddressId shouldBe "addr-1"
            result.sealedBlob.contentEquals(blob) shouldBe true
            result.contentEncoding shouldBe "sudoplatform-crypto"

            verify(mockDatabase).settingsDao()
            verify(mockDatabase).cacheDao()
            verify(mockSettingsDao).get(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT)
            verify(mockCacheDao).getByMessageId("msg-1")
            verify(mockCacheDao).updateLastAccessedAt(check { it shouldBe "msg-1" }, any())
        }

    @Test
    fun `get returns blob for filesystem entry`() =
        runTest {
            val blob = ByteArray(2048) { it.toByte() }
            val blobFile = File(tmpDir, "blobs/msg-fs.blob")
            blobFile.writeBytes(blob)

            val entity =
                EmailMessageBodyCacheEntity(
                    messageId = "msg-fs",
                    sudoId = "sudo-1",
                    emailAddressId = "addr-1",
                    content = null,
                    fsPath = blobFile.absolutePath,
                    contentEncoding = null,
                    sizeBytes = blob.size.toLong(),
                    lastAccessedAt = 1000L,
                )
            mockCacheDao.stub {
                onBlocking { getByMessageId("msg-fs") } doReturn entity
            }

            val result = cache.get("msg-fs")

            result shouldNotBe null
            result!!.sealedBlob.contentEquals(blob) shouldBe true

            verify(mockDatabase).settingsDao()
            verify(mockDatabase).cacheDao()
            verify(mockSettingsDao).get(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT)
            verify(mockCacheDao).getByMessageId("msg-fs")
            verify(mockCacheDao).updateLastAccessedAt(check { it shouldBe "msg-fs" }, any())
        }

    @Test
    fun `get removes stale entry when filesystem blob is missing`() =
        runTest {
            val entity =
                EmailMessageBodyCacheEntity(
                    messageId = "msg-stale",
                    sudoId = "sudo-1",
                    emailAddressId = "addr-1",
                    content = null,
                    fsPath = "/nonexistent/path/msg-stale.blob",
                    contentEncoding = null,
                    sizeBytes = 100L,
                    lastAccessedAt = 1000L,
                )
            mockCacheDao.stub {
                onBlocking { getByMessageId("msg-stale") } doReturn entity
            }

            val result = cache.get("msg-stale")

            result shouldBe null

            verify(mockDatabase).settingsDao()
            verify(mockDatabase).cacheDao()
            verify(mockSettingsDao).get(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT)
            verify(mockCacheDao).getByMessageId("msg-stale")
            verify(mockCacheDao).deleteByMessageId("msg-stale")
        }

    @Test
    fun `get removes stale entry when both content and fsPath are null`() =
        runTest {
            val entity =
                EmailMessageBodyCacheEntity(
                    messageId = "msg-corrupt",
                    sudoId = "sudo-1",
                    emailAddressId = "addr-1",
                    content = null,
                    fsPath = null,
                    contentEncoding = null,
                    sizeBytes = 0L,
                    lastAccessedAt = 1000L,
                )
            mockCacheDao.stub {
                onBlocking { getByMessageId("msg-corrupt") } doReturn entity
            }

            val result = cache.get("msg-corrupt")

            result shouldBe null

            verify(mockDatabase).settingsDao()
            verify(mockDatabase).cacheDao()
            verify(mockSettingsDao).get(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT)
            verify(mockCacheDao).getByMessageId("msg-corrupt")
            verify(mockCacheDao).deleteByMessageId("msg-corrupt")
        }

    @Test
    fun `get returns null when cache size limit is 0`() =
        runTest {
            mockSettingsDao.stub {
                onBlocking {
                    get(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT)
                } doReturn CacheSettingsEntity(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT, "0")
            }

            val disabledCache =
                RoomEmailMessageBodyCache(
                    context = mockContext,
                    logger = mockLogger,
                    cacheStoragePath = tmpDir.absolutePath,
                    database = mockDatabase,
                )

            val result = disabledCache.get("msg-1")

            result shouldBe null

            verify(mockDatabase).settingsDao()
            verify(mockSettingsDao).get(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT)
            verify(mockCacheDao, never()).getByMessageId(any())
        }

    @Test
    fun `get works with null sudoId`() =
        runTest {
            val blob = "content".toByteArray()
            val entity =
                EmailMessageBodyCacheEntity(
                    messageId = "msg-no-sudo",
                    sudoId = null,
                    emailAddressId = "addr-1",
                    content = blob,
                    fsPath = null,
                    contentEncoding = null,
                    sizeBytes = blob.size.toLong(),
                    lastAccessedAt = 1000L,
                )
            mockCacheDao.stub {
                onBlocking { getByMessageId("msg-no-sudo") } doReturn entity
            }

            val result = cache.get("msg-no-sudo")

            result shouldNotBe null
            result!!.sudoId shouldBe null
            result.emailAddressId shouldBe "addr-1"

            verify(mockDatabase).settingsDao()
            verify(mockDatabase).cacheDao()
            verify(mockSettingsDao).get(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT)
            verify(mockCacheDao).getByMessageId("msg-no-sudo")
            verify(mockCacheDao).updateLastAccessedAt(check { it shouldBe "msg-no-sudo" }, any())
        }

    // -------------------------------------------------------------------------
    // put
    // -------------------------------------------------------------------------

    @Test
    fun `put stores small blob inline`() =
        runTest {
            mockCacheDao.stub {
                onBlocking { getTotalSizeBytes() } doReturn 0L
            }

            cache.put(
                CachePutInput(
                    messageId = "msg-small",
                    sudoId = "sudo-1",
                    emailAddressId = "addr-1",
                    sealedBlob = "small".toByteArray(),
                    contentEncoding = null,
                ),
            )

            verify(mockDatabase).settingsDao()
            verify(mockDatabase).cacheDao()
            verify(mockSettingsDao).get(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT)
            verify(mockCacheDao).getTotalSizeBytes()
            verify(mockCacheDao).insert(
                check { entity ->
                    entity.messageId shouldBe "msg-small"
                    entity.content shouldNotBe null
                    entity.fsPath shouldBe null
                },
            )
        }

    @Test
    fun `put stores large blob on filesystem`() =
        runTest {
            mockCacheDao.stub {
                onBlocking { getTotalSizeBytes() } doReturn 0L
            }

            val largeBlob = ByteArray(2048) { 0x42 }
            cache.put(
                CachePutInput(
                    messageId = "msg-fs",
                    sudoId = "sudo-1",
                    emailAddressId = "addr-1",
                    sealedBlob = largeBlob,
                    contentEncoding = null,
                ),
            )

            val blobFile = File(tmpDir, "blobs/msg-fs.blob")
            blobFile.exists() shouldBe true

            verify(mockDatabase).settingsDao()
            verify(mockDatabase).cacheDao()
            verify(mockSettingsDao).get(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT)
            verify(mockCacheDao).getTotalSizeBytes()
            verify(mockCacheDao).insert(
                check { entity ->
                    entity.messageId shouldBe "msg-fs"
                    entity.content shouldBe null
                    entity.fsPath shouldNotBe null
                },
            )
        }

    @Test
    fun `put skips oversized messages`() =
        runTest {
            val oversizedBlob = ByteArray(11 * 1024 * 1024)
            cache.put(
                CachePutInput(
                    messageId = "msg-oversized",
                    sudoId = "sudo-1",
                    emailAddressId = "addr-1",
                    sealedBlob = oversizedBlob,
                    contentEncoding = null,
                ),
            )

            verify(mockDatabase).settingsDao()
            verify(mockDatabase).cacheDao()
            verify(mockSettingsDao).get(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT)
            verify(mockCacheDao, never()).insert(any())
        }

    @Test
    fun `put evicts LRU entries when cache is full`() =
        runTest {
            val victim =
                EmailMessageBodyCacheEntity(
                    messageId = "victim",
                    sudoId = "sudo-1",
                    emailAddressId = "addr-1",
                    content = ByteArray(5 * 1024 * 1024),
                    fsPath = null,
                    contentEncoding = null,
                    sizeBytes = 5L * 1024 * 1024,
                    lastAccessedAt = 1000L,
                )
            mockCacheDao.stub {
                onBlocking { getTotalSizeBytes() } doReturn 9L * 1024 * 1024
                onBlocking { getLeastRecentlyAccessed(10) } doReturn listOf(victim)
            }

            cache.put(
                CachePutInput(
                    messageId = "msg-new",
                    sudoId = "sudo-1",
                    emailAddressId = "addr-1",
                    sealedBlob = ByteArray(2 * 1024 * 1024),
                    contentEncoding = null,
                ),
            )

            verify(mockDatabase).settingsDao()
            verify(mockDatabase).cacheDao()
            verify(mockSettingsDao).get(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT)
            verify(mockCacheDao).getTotalSizeBytes()
            verify(mockCacheDao).getLeastRecentlyAccessed(10)
            verify(mockCacheDao).deleteByMessageId("victim")
            verify(mockCacheDao).insert(check { it.messageId shouldBe "msg-new" })
        }

    // -------------------------------------------------------------------------
    // deleteMessage
    // -------------------------------------------------------------------------

    @Test
    fun `deleteMessage removes entry and filesystem file`() =
        runTest {
            val blobFile = File(tmpDir, "blobs/msg-del.blob")
            blobFile.writeBytes(ByteArray(100))

            val entity =
                EmailMessageBodyCacheEntity(
                    messageId = "msg-del",
                    sudoId = "sudo-1",
                    emailAddressId = "addr-1",
                    content = null,
                    fsPath = blobFile.absolutePath,
                    contentEncoding = null,
                    sizeBytes = 100L,
                    lastAccessedAt = 1000L,
                )
            mockCacheDao.stub {
                onBlocking { getByMessageId("msg-del") } doReturn entity
            }

            cache.deleteMessage("msg-del")

            blobFile.exists() shouldBe false

            verify(mockDatabase).cacheDao()
            verify(mockCacheDao).getByMessageId("msg-del")
            verify(mockCacheDao).deleteByMessageId("msg-del")
        }

    @Test
    fun `deleteMessage is a no-op for missing entry`() =
        runTest {
            mockCacheDao.stub {
                onBlocking { getByMessageId("nonexistent") } doReturn null
            }

            cache.deleteMessage("nonexistent")

            verify(mockDatabase).cacheDao()
            verify(mockCacheDao).getByMessageId("nonexistent")
            verify(mockCacheDao).deleteByMessageId("nonexistent")
        }

    // -------------------------------------------------------------------------
    // flush
    // -------------------------------------------------------------------------

    @Test
    fun `flush by sudoId removes matching entries`() =
        runTest {
            mockCacheDao.stub {
                onBlocking { getBySudoId("sudo-A") } doReturn emptyList()
            }

            cache.flush(CacheFlushInput(sudoId = "sudo-A"))

            verify(mockDatabase).cacheDao()
            verify(mockCacheDao).getBySudoId("sudo-A")
            verify(mockCacheDao).deleteBySudoId("sudo-A")
        }

    @Test
    fun `flush by emailAddressId removes matching entries`() =
        runTest {
            mockCacheDao.stub {
                onBlocking { getByEmailAddressId("addr-A") } doReturn emptyList()
            }

            cache.flush(CacheFlushInput(emailAddressId = "addr-A"))

            verify(mockDatabase).cacheDao()
            verify(mockCacheDao).getByEmailAddressId("addr-A")
            verify(mockCacheDao).deleteByEmailAddressId("addr-A")
        }

    @Test
    fun `flush throws when neither sudoId nor emailAddressId provided`() =
        runTest {
            shouldThrow<IllegalArgumentException> {
                cache.flush(CacheFlushInput())
            }

            verify(mockDatabase).cacheDao()
        }

    // -------------------------------------------------------------------------
    // flushAll
    // -------------------------------------------------------------------------

    @Test
    fun `flushAll calls deleteAll`() =
        runTest {
            cache.flushAll()

            verify(mockDatabase).cacheDao()
            verify(mockCacheDao).deleteAll()
        }

    // -------------------------------------------------------------------------
    // setCacheSizeLimit
    // -------------------------------------------------------------------------

    @Test
    fun `setCacheSizeLimit persists the value`() =
        runTest {
            mockCacheDao.stub {
                onBlocking { getTotalSizeBytes() } doReturn 0L
            }

            cache.setCacheSizeLimit(5 * 1024 * 1024)

            verify(mockDatabase).settingsDao()
            verify(mockDatabase).cacheDao()
            verify(mockSettingsDao).insertOrReplace(
                check { entity ->
                    entity.key shouldBe CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT
                    entity.value shouldBe (5L * 1024 * 1024).toString()
                },
            )
            verify(mockCacheDao).getTotalSizeBytes()
        }

    @Test
    fun `setCacheSizeLimit to 0 calls deleteAll`() =
        runTest {
            cache.setCacheSizeLimit(0)

            verify(mockDatabase).settingsDao()
            verify(mockDatabase).cacheDao()
            verify(mockSettingsDao).insertOrReplace(
                check { entity ->
                    entity.value shouldBe "0"
                },
            )
            verify(mockCacheDao).deleteAll()
        }

    @Test
    fun `setCacheSizeLimit throws for negative value`() =
        runTest {
            shouldThrow<IllegalArgumentException> {
                cache.setCacheSizeLimit(-1)
            }
        }

    // -------------------------------------------------------------------------
    // getCacheSizeLimit
    // -------------------------------------------------------------------------

    @Test
    fun `getCacheSizeLimit returns persisted value`() =
        runTest {
            val result = cache.getCacheSizeLimit()

            result shouldBe 10L * 1024 * 1024

            verify(mockDatabase).settingsDao()
            verify(mockSettingsDao).get(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT)
        }
}
