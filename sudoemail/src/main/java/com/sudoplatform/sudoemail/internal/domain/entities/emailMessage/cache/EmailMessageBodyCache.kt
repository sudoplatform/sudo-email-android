/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailMessage.cache

/**
 * Local cache for sealed email message bodies.
 *
 * The cache stores sealed blobs (before unsealing) keyed by message ID. It uses an
 * LRU eviction policy and enforces a configurable total-size limit. Blobs larger than
 * the size limit are never cached. Blobs larger than the large-message threshold are
 * stored on the device filesystem rather than inline in the Room database.
 *
 * All cache errors are logged and swallowed — a cache failure must never prevent
 * message retrieval.
 */
internal interface EmailMessageBodyCache {
    /**
     * Retrieve a sealed blob from the cache.
     *
     * On a cache hit, updates the entry's last-accessed timestamp (resetting its LRU
     * position) and returns the result.
     *
     * On a cache miss, returns null.
     *
     * If the entry exists in the database but its stored content is missing or
     * unreadable (stale entry), removes the entry and returns null.
     *
     * @param messageId Identifier of the message to retrieve.
     * @return The cached sealed blob, or null on a miss or stale entry.
     */
    suspend fun get(messageId: String): CacheGetResult?

    /**
     * Store a sealed blob in the cache.
     *
     * No-ops if:
     * - The cache size limit is 0 (caching disabled).
     * - The blob is larger than the configured cache size limit (oversized message).
     *
     * Evicts the least recently used entries as needed before inserting to ensure the
     * total cached size does not exceed the limit.
     *
     * Blobs larger than the large-message threshold are stored on the device filesystem;
     * smaller blobs are stored inline in the Room database.
     *
     * @param input The message metadata and sealed blob to store.
     */
    suspend fun put(input: CachePutInput)

    /**
     * Remove a single cache entry by message ID.
     *
     * No-op if the entry does not exist.
     *
     * @param messageId Identifier of the message to remove.
     */
    suspend fun deleteMessage(messageId: String)

    /**
     * Remove all cache entries matching the given scope.
     *
     * Exactly one of `sudoId` or `emailAddressId` must be provided; throws
     * [IllegalArgumentException] if neither is supplied.
     *
     * @param input Scope of the flush operation.
     */
    suspend fun flush(input: CacheFlushInput)

    /**
     * Remove all entries from the cache, including any associated filesystem files.
     */
    suspend fun flushAll()

    /**
     * Update the maximum total size of the cache and persist the new value.
     *
     * If the new limit is lower than the current total cached size, immediately evicts
     * the least recently used entries until the total size is within the new limit.
     *
     * Setting [bytes] to 0 disables caching and evicts all existing entries.
     *
     * @param bytes New cache size limit in bytes. Must be >= 0.
     * @throws IllegalArgumentException If [bytes] is negative.
     */
    suspend fun setCacheSizeLimit(bytes: Long)

    /**
     * Returns the current persisted cache size limit in bytes.
     */
    suspend fun getCacheSizeLimit(): Long
}
