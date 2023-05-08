/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.s3

import android.content.Context
import com.amazonaws.auth.CognitoCredentialsProvider
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.DeleteObjectRequest
import com.amazonaws.services.s3.model.ListObjectsV2Request
import com.amazonaws.services.s3.model.ObjectMetadata
import com.sudoplatform.sudoemail.s3.types.S3ClientListOutput
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Default S3 client implementation.
 *
 * @param context Android app context.
 * @param sudoUserClient [SudoUserClient] used for authenticating to AWS S3.
 */
class DefaultS3Client(
    context: Context,
    sudoUserClient: SudoUserClient,
    override val region: String,
    override val bucket: String,
    private val logger: Logger,
) : S3Client {

    private val transferUtility: TransferUtility

    private val amazonS3Client: AmazonS3Client

    private val credentialsProvider: CognitoCredentialsProvider = sudoUserClient.getCredentialsProvider()

    init {
        this.amazonS3Client = AmazonS3Client(this.credentialsProvider, Region.getRegion(region))
        this.transferUtility = TransferUtility.builder()
            .context(context)
            .s3Client(this.amazonS3Client)
            .defaultBucket(bucket)
            .build()
    }

    override suspend fun upload(data: ByteArray, objectId: String, metadata: Map<String, String>?): String = suspendCoroutine { cont ->
        this.logger.debug("Uploading a RFC822 data to S3.")

        val s3Key = this.constructS3KeyWithCredentials(objectId)

        val objectMetadata = ObjectMetadata()

        metadata?.forEach { entry ->
            objectMetadata.addUserMetadata(entry.key, entry.value)
        }

        objectMetadata.contentLength = data.size.toLong()

        val file = File(s3Key)
        val tmpFile = File.createTempFile(file.name, ".tmp")
        FileOutputStream(tmpFile).use { it.write(data) }

        val observer = transferUtility.upload(s3Key, tmpFile, objectMetadata)
        observer.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                when (state) {
                    TransferState.COMPLETED -> {
                        this@DefaultS3Client.logger.info("S3 upload completed successfully.")
                        cont.resume(s3Key)
                    }
                    TransferState.CANCELED -> {
                        this@DefaultS3Client.logger.error("S3 upload was cancelled.")
                        cont.resumeWithException(S3Exception.UploadException("Upload was cancelled."))
                    }
                    TransferState.FAILED -> {
                        this@DefaultS3Client.logger.error("S3 upload failed.")
                        cont.resumeWithException(S3Exception.UploadException("Upload failed."))
                    }
                    else -> this@DefaultS3Client.logger.info("S3 upload state changed: $state.")
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                this@DefaultS3Client.logger.debug("S3 upload progress changed: id=$id, bytesCurrent=$bytesCurrent, bytesTotal=$bytesTotal")
            }

            override fun onError(id: Int, e: Exception?) {
                cont.resumeWithException(S3Exception.UploadException(e?.message, cause = e))
            }
        })
    }

    override suspend fun download(key: String): ByteArray = suspendCoroutine { cont ->
        this.logger.debug("Downloading a RFC822 data from S3.")

        val s3Key = this.constructS3KeyWithCredentials(key)

        val id = UUID.randomUUID().toString().uppercase(Locale.ROOT)
        val tmpFile = File.createTempFile(id, ".tmp")
        val observer = transferUtility.download(s3Key, tmpFile)
        observer.setTransferListener(object : TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                when (state) {
                    TransferState.COMPLETED -> {
                        this@DefaultS3Client.logger.info("S3 download completed successfully.")
                        cont.resume(tmpFile.readBytes())
                    }
                    TransferState.CANCELED -> {
                        this@DefaultS3Client.logger.error("S3 download was cancelled.")
                        cont.resumeWithException(S3Exception.DownloadException("Download was cancelled."))
                    }
                    TransferState.FAILED -> {
                        this@DefaultS3Client.logger.error("S3 download failed.")
                        cont.resumeWithException(S3Exception.DownloadException("Download failed."))
                    }
                    else -> this@DefaultS3Client.logger.info("S3 download state changed: $state.")
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                this@DefaultS3Client.logger.debug(
                    "S3 download progress changed: id=$id, bytesCurrent=$bytesCurrent, bytesTotal=$bytesTotal"
                )
            }

            override fun onError(id: Int, e: Exception?) {
                cont.resumeWithException(S3Exception.DownloadException(e?.message, cause = e))
            }
        })
    }

    override suspend fun delete(objectId: String) {
        this.logger.info("Deleting a RFC822 data from S3.")
        val s3Key = this.constructS3KeyWithCredentials(objectId)

        try {
            val request = DeleteObjectRequest(this.bucket, s3Key)
            this.amazonS3Client.deleteObject(request)
        } catch (e: Exception) {
            this@DefaultS3Client.logger.error("S3 delete failed.")
            throw S3Exception.DeleteException("S3 delete failed")
        }
    }

    override suspend fun list(bucketName: String, prefix: String): List<S3ClientListOutput> {
        val request = ListObjectsV2Request()
        val s3Key = this.constructS3KeyWithCredentials(prefix)
        request.bucketName = bucketName
        request.prefix = s3Key
        val response = this.amazonS3Client.listObjectsV2(request)

        return response.objectSummaries.map {
            S3ClientListOutput(it.key, it.lastModified)
        }
    }

    override suspend fun getObjectMetadata(key: String): ObjectMetadata {
        val s3Key = this.constructS3KeyWithCredentials(key)
        return this.amazonS3Client.getObjectMetadata(this.bucket, s3Key)
    }

    private fun constructS3KeyWithCredentials(key: String): String {
        val credentialsPrefix = this.credentialsProvider.identityId
        return "$credentialsPrefix/$key"
    }
}

sealed class S3Exception(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    /**
     * Exception thrown when error occurs uploading items to S3 using the TransferListener.
     */
    class UploadException(message: String? = null, cause: Throwable? = null) :
        S3Exception(message = message, cause = cause)

    /**
     * Exception thrown when error occurs downloading items from S3 using the TransferListener.
     */
    class DownloadException(message: String? = null, cause: Throwable? = null) :
        S3Exception(message = message, cause = cause)

    /**
     * Exception thrown when error occurs while deleting an object from S3.
     */
    class DeleteException(message: String? = null, cause: Throwable? = null) :
        S3Exception(message, cause)
}
