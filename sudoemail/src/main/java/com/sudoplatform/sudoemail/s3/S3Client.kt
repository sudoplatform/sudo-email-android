/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.s3

import com.amazonaws.services.s3.model.ObjectMetadata
import com.sudoplatform.sudoemail.s3.types.S3ClientListOutput

/**
 * S3 client wrapper protocol mainly used for providing an abstraction layer on top of
 * AWS S3 SDK.
 */
interface S3Client {

    /**
     * AWS region hosting the S3 bucket.
     */
    val region: String

    /**
     * S3 bucket used by Email Service for storing RFC822 data.
     */
    val bucket: String

    /**
     * Uploads a RFC822 data to AWS S3.
     *
     * @param data [ByteArray] RFC822 data to upload.
     * @param objectId [String] Unique ID for the RFC822 data.
     * @param metadata Optional metadata to include with the upload as a [Map<String, String>].
     * @return AWS S3 key representing the location of the RFC822 data.
     */
    @Throws(S3Exception::class)
    suspend fun upload(data: ByteArray, objectId: String, metadata: Map<String, String>? = null): String

    /**
     * Downloads a RFC822 data from AWS S3.
     *
     * @param key [String] AWS S3 key representing the location of the RFC822 data.
     * @return The downloaded RFC822 data as a [ByteArray].
     */
    @Throws(S3Exception::class)
    suspend fun download(key: String): ByteArray

    /**
     * Deletes a RFC822 data from AWS S3.
     *
     * @param objectId [String] AWS S3 key representing the location of the RFC822 data.
     */
    @Throws(S3Exception::class)
    suspend fun delete(objectId: String)

    /**
     * Returns a list of objects from AWS S3
     *
     * @param prefix [String] The path in S3 to list objects from.
     * @return List of [S3ClientListOutput] objects that match the key.
     */
    @Throws(S3Exception::class)
    suspend fun list(prefix: String): List<S3ClientListOutput>

    /**
     * Returns the metadata associated with the object with the given key.
     *
     * @param key [String] The object's key.
     * @return The [ObjectMetadata].
     */
    @Throws(S3Exception::class)
    suspend fun getObjectMetadata(key: String): ObjectMetadata

    /**
     * Updates the metadata associated with the object with the given key.
     *
     * @param key [String] The object's key.
     * @param metadata [Map<String, String>] A map containing the new metadata.
     */
    @Throws(S3Exception::class)
    suspend fun updateObjectMetadata(key: String, metadata: Map<String, String>)
}
