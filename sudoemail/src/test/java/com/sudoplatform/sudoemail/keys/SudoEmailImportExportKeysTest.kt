/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import android.content.Context
import android.util.Base64
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.DefaultSudoEmailClient
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.s3.S3Client
import com.sudoplatform.sudoemail.sealing.DefaultSealingService
import com.sudoplatform.sudokeymanager.KeyManager
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.runBlocking
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

/**
 * Test the correct operation of [SudoEmailClient.exportKeys, SudoEmailClient.importKeys]
 * using mocks and spies.
 */

@RunWith(RobolectricTestRunner::class)
class SudoEmailImportExportKeysTest : BaseTests() {

    private val mockContext by before {
        mock<Context>()
    }

    private val mockUserClient by before {
        mock<SudoUserClient>()
    }

    private val mockAppSyncClient by before {
        mock<AWSAppSyncClient>().stub {
        }
    }

    private val mockKeyManager by before {
        mock<KeyManagerInterface>().stub {
        }
    }

    private val dummyKeyString = "dummy key archive"

    private val mockDeviceKeyManager by before {
        mock<DeviceKeyManager>().stub {
            on { exportKeys() } doReturn dummyKeyString.toByteArray(Charsets.UTF_8)
        }
    }

    private val mockS3Client by before {
        mock<S3Client>()
    }

    private val mockSealingService by before {
        DefaultSealingService(
            mockDeviceKeyManager,
            mockLogger,
        )
    }

    private val client by before {
        DefaultSudoEmailClient(
            mockContext,
            mockAppSyncClient,
            mockUserClient,
            mockLogger,
            mockDeviceKeyManager,
            mockSealingService,
            "region",
            "identityBucket",
            "transientBucket",
            mockS3Client,
            mockS3Client,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockContext, mockUserClient, mockKeyManager, mockAppSyncClient, mockS3Client)
    }

    @Test
    fun `importKeys(archiveData) should succeed when no error present`() = runBlocking<Unit> {
        val archiveData = dummyKeyString.toByteArray()
        client.importKeys(archiveData)

        verify(mockDeviceKeyManager).importKeys(archiveData)
    }

    @Test
    fun `importKeys(archiveData) with empty archive data throws`() = runBlocking<Unit> {
        val archiveData = "".toByteArray()
        shouldThrow<SudoEmailClient.EmailCryptographicKeysException.SecureKeyArchiveException> {
            client.importKeys(archiveData)
        }
    }

    @Test
    fun `importKeys(archiveData) should throw when deviceKeyManager throws`() = runBlocking<Unit> {
        val mockDeviceKeyManager by before {
            mock<DeviceKeyManager>().stub {
                on { importKeys(any<ByteArray>()) } doThrow
                    DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException("Mock exception")
            }
        }
        val errorClient = DefaultSudoEmailClient(
            mockContext,
            mockAppSyncClient,
            mockUserClient,
            mockLogger,
            mockDeviceKeyManager,
            mockSealingService,
            "region",
            "identityBucket",
            "transientBucket",
            mockS3Client,
            mockS3Client,
        )
        shouldThrow<SudoEmailClient.EmailCryptographicKeysException.SecureKeyArchiveException> {
            errorClient.importKeys(dummyKeyString.toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun `exportKeys() should return key archive as exported from deviceKeyManager`() = runBlocking<Unit> {
        val keyArchiveData = client.exportKeys()

        verify(mockDeviceKeyManager).exportKeys()
        keyArchiveData.toString(Charsets.UTF_8) shouldBe dummyKeyString
    }

    @Test
    fun `exportKeys() should throw when deviceKeyManager throws`() = runBlocking<Unit> {
        val mockDeviceKeyManager by before {
            mock<DeviceKeyManager>().stub {
                on { exportKeys() } doThrow
                    DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException("Mock exception")
            }
        }

        val errorClient = DefaultSudoEmailClient(
            mockContext,
            mockAppSyncClient,
            mockUserClient,
            mockLogger,
            mockDeviceKeyManager,
            mockSealingService,
            "region",
            "identityBucket",
            "transientBucket",
            mockS3Client,
            mockS3Client,
        )

        shouldThrow<SudoEmailClient.EmailCryptographicKeysException.SecureKeyArchiveException> {
            errorClient.exportKeys()
        }
    }

    @Test
    fun `importKeys() successfully imports keys exported from the JS SDK`() = runBlocking<Unit> {
        // Obtained from JS exportKeys API. Don't update these constants since the point of this is to
        // check that we have not broken backward compatibility (with existing key
        // backups) and interoperability with all clients.
        @Suppress("ktlint:standard:max-line-length")
        val exportedKeys = "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"

        @Suppress("ktlint:standard:max-line-length")
        val sealedString = "oct9RDGr5QUS0c6HtdJtjsQAUgeY5BtTb+nj6crXkZ2kwnXrPL5ghq9e8nTH+LCh6NpJTlXJo/D6KS6dkPsIV0N1toplIswkCk8VfJ7BcfiYpFQcC6Y/+MmP778deWVERxkiYq5N1xIcB4p7RB7SL+VKJrdZ0Bt+Y9ZoMRxq8voNIInfl+kI4NS8iQkhAEBWoWO9H64YFsXvGLkth0K1obRJvs9HiXYjQyQlMBpP7Ku+ikJFnxlBgaV3ejxxv0SL"
        val keyId = "311220fc-a246-4181-87da-3fa0779ebb78"
        val algorithm = "AES/CBC/PKCS7Padding"
        val keyNameSpace = "SudoEmailClient"
        val from = "from: safe-09432135-1514-410128-11455-3110677109148911@team-gc-dev.com"
        val messageBody = "test draft message"
        // End of obtained from JS exportKeys API.

        val keyManager = KeyManager(InMemoryStore())

        val deviceKeyManager = DefaultDeviceKeyManager(keyNameSpace, mockUserClient, keyManager, mockLogger)
        val emailClient = DefaultSudoEmailClient(
            mockContext,
            mockAppSyncClient,
            mockUserClient,
            mockLogger,
            deviceKeyManager,
            mockSealingService,
            "region",
            "identityBucket",
            "transientBucket",
            mockS3Client,
            mockS3Client,
        )

        val archiveData = Base64.decode(exportedKeys, Base64.NO_WRAP)
        emailClient.importKeys(archiveData)

        val sealedData = Base64.decode(sealedString, Base64.NO_WRAP)
        val unsealedString = deviceKeyManager.decryptWithSymmetricKeyId(keyId, sealedData).toString(Charsets.UTF_8)
        unsealedString.contains(from) shouldBe true
        unsealedString.contains(messageBody) shouldBe true
    }
}
