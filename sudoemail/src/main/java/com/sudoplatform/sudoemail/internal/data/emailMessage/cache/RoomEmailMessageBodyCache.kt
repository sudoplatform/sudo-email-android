/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.emailMessage.cache

import android.content.Context
import androidx.room.Room
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.CacheFlushInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.CacheGetResult
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.CachePutInput
import com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache.EmailMessageBodyCache
import com.sudoplatform.sudologging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Room-backed implementation of [EmailMessageBodyCache].
 *
 * All database and filesystem operations run on [Dispatchers.IO].
 *
 * Cache errors are always logged and never propagated to callers — the cache is a
 * performance optimisation and must not break message retrieval.
 */
internal class RoomEmailMessageBodyCache(
    private val context: Context,
    private val logger: Logger,
    private val cacheStoragePath: String? = null,
    private val initialCacheSizeLimitBytes: Long = DEFAULT_CACHE_SIZE_LIMIT_BYTES,
    private val largeMessageThresholdBytes: Long = LARGE_MESSAGE_THRESHOLD_BYTES,
    database: EmailMessageBodyCacheDatabase? = null,
) : EmailMessageBodyCache {
    companion object {
        const val DEFAULT_CACHE_SIZE_LIMIT_BYTES: Long = 300L * 1024 * 1024
        const val LARGE_MESSAGE_THRESHOLD_BYTES: Long = 1L * 1024 * 1024
        private const val DEFAULT_STORAGE_DIR = "sudo-email-cache"
        private const val DATABASE_FILE_NAME = "email-cache.db"
        private const val BLOBS_DIR_NAME = "blobs"
    }

    private val storagePath: String by lazy {
        cacheStoragePath ?: File(context.filesDir, DEFAULT_STORAGE_DIR).absolutePath
    }

    private val blobsDir: File by lazy {
        File(storagePath, BLOBS_DIR_NAME).also { it.mkdirs() }
    }

    /**
     * The Room database instance. Null if initialization failed — all operations become no-ops.
     */
    private val db: EmailMessageBodyCacheDatabase? by lazy {
        if (database != null) return@lazy database
        try {
            val storageDir = File(storagePath)
            storageDir.mkdirs()
            if (!storageDir.exists() || !storageDir.canWrite()) {
                logger.error("Cache storage directory is not writable: $storagePath — caching disabled")
                return@lazy null
            }
            blobsDir.mkdirs()
            val dbFile = File(storagePath, DATABASE_FILE_NAME)
            Room
                .databaseBuilder(
                    context,
                    EmailMessageBodyCacheDatabase::class.java,
                    dbFile.absolutePath,
                ).build()
        } catch (e: Exception) {
            logger.error("Failed to initialise cache database — caching disabled: ${e.message}")
            null
        }
    }

    /** In-memory copy of the persisted cache size limit, initialized lazily. */
    private var cacheSizeLimitBytes: Long? = null

    private suspend fun getOrInitCacheSizeLimit(): Long {
        cacheSizeLimitBytes?.let { return it }
        val dao = db?.settingsDao() ?: return initialCacheSizeLimitBytes
        return withContext(Dispatchers.IO) {
            val persisted = dao.get(CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT)
            if (persisted != null) {
                persisted.value.toLong().also { cacheSizeLimitBytes = it }
            } else {
                dao.insertOrReplace(
                    CacheSettingsEntity(
                        key = CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT,
                        value = initialCacheSizeLimitBytes.toString(),
                    ),
                )
                initialCacheSizeLimitBytes.also { cacheSizeLimitBytes = it }
            }
        }
    }

    /** Returns true if the cache is active (db initialized and size limit > 0). */
    private suspend fun isCacheActive(): Boolean {
        if (db == null) return false
        return getOrInitCacheSizeLimit() > 0
    }

    /**
     * Returns the cache DAO if the cache is active (db initialized and size limit > 0),
     * or null if the cache is disabled or failed to initialize.
     */
    private suspend fun getActiveCacheDao(): EmailMessageBodyCacheDao? {
        val database = db ?: return null
        if (getOrInitCacheSizeLimit() == 0L) return null
        return database.cacheDao()
    }

    // -------------------------------------------------------------------------
    // Public interface methods
    // -------------------------------------------------------------------------

    override suspend fun get(messageId: String): CacheGetResult? {
        val dao = getActiveCacheDao() ?: return null
        return try {
            withContext(Dispatchers.IO) {
                val entity = dao.getByMessageId(messageId) ?: return@withContext null

                if (entity.content == null && entity.fsPath == null) {
                    dao.deleteByMessageId(messageId)
                    return@withContext null
                }

                val sealedBlob: ByteArray =
                    if (entity.fsPath != null) {
                        val file = File(entity.fsPath)
                        if (!file.exists() || !file.canRead()) {
                            dao.deleteByMessageId(messageId)
                            return@withContext null
                        }
                        file.readBytes()
                    } else {
                        entity.content!!
                    }

                dao.updateLastAccessedAt(messageId, System.currentTimeMillis())

                CacheGetResult(
                    messageId = entity.messageId,
                    sudoId = entity.sudoId,
                    emailAddressId = entity.emailAddressId,
                    sealedBlob = sealedBlob,
                    contentEncoding = entity.contentEncoding,
                )
            }
        } catch (e: Exception) {
            logger.error("Cache get error for messageId=$messageId: ${e.message}")
            null
        }
    }

    override suspend fun put(input: CachePutInput) {
        val dao = getActiveCacheDao() ?: return
        try {
            withContext(Dispatchers.IO) {
                val limit = getOrInitCacheSizeLimit()

                val blobSize = input.sealedBlob.size.toLong()
                if (blobSize > limit) return@withContext

                evictToFit(dao, blobSize, limit)

                val now = System.currentTimeMillis()

                if (blobSize > largeMessageThresholdBytes) {
                    val blobFile = File(blobsDir, "${input.messageId}.blob")
                    try {
                        blobFile.writeBytes(input.sealedBlob)
                    } catch (e: Exception) {
                        logger.error("Failed to write large-message blob: ${e.message}")
                        return@withContext
                    }
                    dao.insert(
                        EmailMessageBodyCacheEntity(
                            messageId = input.messageId,
                            sudoId = input.sudoId,
                            emailAddressId = input.emailAddressId,
                            content = null,
                            fsPath = blobFile.absolutePath,
                            contentEncoding = input.contentEncoding,
                            sizeBytes = blobSize,
                            lastAccessedAt = now,
                        ),
                    )
                } else {
                    dao.insert(
                        EmailMessageBodyCacheEntity(
                            messageId = input.messageId,
                            sudoId = input.sudoId,
                            emailAddressId = input.emailAddressId,
                            content = input.sealedBlob,
                            fsPath = null,
                            contentEncoding = input.contentEncoding,
                            sizeBytes = blobSize,
                            lastAccessedAt = now,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Cache put error for messageId=${input.messageId}: ${e.message}")
        }
    }

    override suspend fun deleteMessage(messageId: String) {
        val dao = db?.cacheDao() ?: return
        try {
            withContext(Dispatchers.IO) {
                val entity = dao.getByMessageId(messageId)
                if (entity?.fsPath != null) {
                    File(entity.fsPath).delete()
                }
                dao.deleteByMessageId(messageId)
            }
        } catch (e: Exception) {
            logger.error("Cache deleteMessage error for messageId=$messageId: ${e.message}")
        }
    }

    override suspend fun flush(input: CacheFlushInput) {
        val dao = db?.cacheDao() ?: return
        require(input.sudoId != null || input.emailAddressId != null) {
            "flush requires either sudoId or emailAddressId"
        }
        try {
            withContext(Dispatchers.IO) {
                if (input.sudoId != null) {
                    val entries = dao.getBySudoId(input.sudoId)
                    deleteFilesForEntries(entries)
                    dao.deleteBySudoId(input.sudoId)
                } else if (input.emailAddressId != null) {
                    val entries = dao.getByEmailAddressId(input.emailAddressId)
                    deleteFilesForEntries(entries)
                    dao.deleteByEmailAddressId(input.emailAddressId)
                }
            }
        } catch (e: Exception) {
            logger.error("Cache flush error: ${e.message}")
        }
    }

    override suspend fun flushAll() {
        val dao = db?.cacheDao() ?: return
        try {
            withContext(Dispatchers.IO) {
                blobsDir.listFiles()?.forEach { it.delete() }
                dao.deleteAll()
            }
        } catch (e: Exception) {
            logger.error("Cache flushAll error: ${e.message}")
        }
    }

    override suspend fun setCacheSizeLimit(bytes: Long) {
        require(bytes >= 0) { "Cache size limit must be >= 0, got $bytes" }
        val settingsDao = db?.settingsDao() ?: return
        val cacheDao = db?.cacheDao() ?: return
        try {
            withContext(Dispatchers.IO) {
                settingsDao.insertOrReplace(
                    CacheSettingsEntity(
                        key = CacheTableConstants.SETTINGS_KEY_CACHE_SIZE_LIMIT,
                        value = bytes.toString(),
                    ),
                )
                cacheSizeLimitBytes = bytes

                if (bytes == 0L) {
                    blobsDir.listFiles()?.forEach { it.delete() }
                    cacheDao.deleteAll()
                } else {
                    evictToFit(cacheDao, 0, bytes)
                }
            }
        } catch (e: Exception) {
            logger.error("Cache setCacheSizeLimit error: ${e.message}")
        }
    }

    override suspend fun getCacheSizeLimit(): Long =
        try {
            getOrInitCacheSizeLimit()
        } catch (e: Exception) {
            logger.error("Cache getCacheSizeLimit error: ${e.message}")
            initialCacheSizeLimitBytes
        }

    /**
     * Closes the underlying database connection. Call this when the cache is no longer needed.
     */
    fun close() {
        try {
            db?.close()
        } catch (e: Exception) {
            logger.error("Cache close error: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private suspend fun evictToFit(
        dao: EmailMessageBodyCacheDao,
        incomingBytes: Long,
        limit: Long,
    ) {
        var currentTotal = dao.getTotalSizeBytes()
        while (currentTotal + incomingBytes > limit) {
            val victims = dao.getLeastRecentlyAccessed(10)
            if (victims.isEmpty()) break
            for (victim in victims) {
                if (victim.fsPath != null) {
                    File(victim.fsPath).delete()
                }
                dao.deleteByMessageId(victim.messageId)
                currentTotal -= victim.sizeBytes
                if (currentTotal + incomingBytes <= limit) break
            }
        }
    }

    private fun deleteFilesForEntries(entries: List<EmailMessageBodyCacheEntity>) {
        for (entry in entries) {
            if (entry.fsPath != null) {
                File(entry.fsPath).delete()
            }
        }
    }
}
