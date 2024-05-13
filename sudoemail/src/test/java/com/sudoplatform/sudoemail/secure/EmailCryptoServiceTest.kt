/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.secure

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.secure.types.ALGORITHM_JSON
import com.sudoplatform.sudoemail.secure.types.ENCRYPTED_KEY_JSON
import com.sudoplatform.sudoemail.secure.types.PUBLIC_KEY_ID_JSON
import com.sudoplatform.sudoemail.secure.types.SecureEmailAttachmentType
import com.sudoplatform.sudoemail.secure.types.SecurePackage
import com.sudoplatform.sudoemail.types.EmailAttachment
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.Base64

/**
 * Test the correct operation of [EmailCryptoService] using mocks and spies.
 */
class EmailCryptoServiceTest : BaseTests() {

    private val stubKeyIds = setOf("keyId1", "keyId2")

    private val encryptedData = Base64.getEncoder().encodeToString("encryptedData".toByteArray())
    private val initVectorKeyID =
        Base64.getEncoder().encodeToString("initVectorKeyID".toByteArray())

    private val stubData = """{
            "encryptedData": "$encryptedData",
            "initVectorKeyID": "$initVectorKeyID"
        }"""
    private val stubKey = """{
            "$PUBLIC_KEY_ID_JSON":"publicKeyId",
            "$ENCRYPTED_KEY_JSON":"$encryptedData",
            "$ALGORITHM_JSON":"RSA_ECB_OAEPSHA1"
        }"""

    private val bodyAttachment = EmailAttachment(
        fileName = SecureEmailAttachmentType.BODY.fileName,
        contentId = SecureEmailAttachmentType.BODY.contentId,
        mimeType = SecureEmailAttachmentType.BODY.mimeType,
        inlineAttachment = false,
        data = stubData.toByteArray(),
    )
    private val keyAttachment = EmailAttachment(
        fileName = SecureEmailAttachmentType.KEY_EXCHANGE.fileName,
        contentId = SecureEmailAttachmentType.KEY_EXCHANGE.contentId,
        mimeType = SecureEmailAttachmentType.KEY_EXCHANGE.mimeType,
        inlineAttachment = false,
        data = stubKey.toByteArray(),
    )
    private val securePackage = SecurePackage(setOf(keyAttachment), bodyAttachment)

    private val mockDeviceKeyManager by before {
        mock<DeviceKeyManager>().stub {
            on { generateRandomSymmetricKey() } doReturn ByteArray(42)
            on { createRandomData(anyInt()) } doReturn ByteArray(42)
            on { encryptWithSymmetricKey(any(), any(), any()) } doReturn ByteArray(42)
            on { encryptWithKeyPairId(anyString(), any(), any()) } doReturn ByteArray(42)
            on { privateKeyExists(anyString()) } doReturn true
            on { decryptWithKeyPairId(any(), anyString(), any()) } doReturn ByteArray(42)
            on { decryptWithSymmetricKey(any(), any(), any()) } doReturn ByteArray(42)
        }
    }

    private val emailCryptoService by before {
        DefaultEmailCryptoService(
            mockDeviceKeyManager,
            mockLogger,
        )
    }

    @After
    fun fini() {
        verifyNoMoreInteractions(mockDeviceKeyManager)
    }

    @Test
    fun `encrypt() should return results when no error present`() = runBlocking<Unit> {
        val deferredResult = async(Dispatchers.IO) {
            emailCryptoService.encrypt(stubData.toByteArray(), stubKeyIds)
        }

        deferredResult.start()
        delay(100L)

        val result = deferredResult.await()
        result shouldNotBe null

        with(result) {
            bodyAttachment.fileName shouldBe securePackage.bodyAttachment.fileName
            bodyAttachment.contentId shouldBe securePackage.bodyAttachment.contentId
            bodyAttachment.mimeType shouldBe securePackage.bodyAttachment.mimeType
            bodyAttachment.inlineAttachment shouldBe false
            bodyAttachment.data shouldNotBe null

            var fileNameIndex: Int
            keyAttachments.forEachIndexed { index, key ->
                fileNameIndex = index + 1
                key.fileName shouldBe "${securePackage.keyAttachments.first().fileName} $fileNameIndex"
                key.contentId shouldBe securePackage.keyAttachments.first().contentId
                key.mimeType shouldBe securePackage.keyAttachments.first().mimeType
                key.inlineAttachment shouldBe false
                key.data shouldNotBe null
            }
        }

        verify(mockDeviceKeyManager).generateRandomSymmetricKey()
        verify(mockDeviceKeyManager).createRandomData(anyInt())
        verify(mockDeviceKeyManager).encryptWithSymmetricKey(any(), any(), any())
        verify(mockDeviceKeyManager, times(2)).encryptWithKeyPairId(anyString(), any(), any())
    }

    @Test
    fun `encrypt() should throw error if data is empty`() = runBlocking<Unit> {
        val data = ByteArray(0)

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<EmailCryptoService.EmailCryptoServiceException.InvalidArgumentException> {
                emailCryptoService.encrypt(data, stubKeyIds)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()
    }

    @Test
    fun `encrypt() should throw error if keyIds are empty`() = runBlocking<Unit> {
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<EmailCryptoService.EmailCryptoServiceException.InvalidArgumentException> {
                emailCryptoService.encrypt(stubData.toByteArray(), emptySet())
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()
    }

    @Test
    fun `encrypt() should throw error if device key manager error occurs`() = runBlocking<Unit> {
        mockDeviceKeyManager.stub {
            on { generateRandomSymmetricKey() } doThrow DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException(
                "Mock",
            )
        }
        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<EmailCryptoService.EmailCryptoServiceException.SecureDataEncryptionException> {
                emailCryptoService.encrypt(stubData.toByteArray(), stubKeyIds)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockDeviceKeyManager).generateRandomSymmetricKey()
    }

    @Test
    fun `decrypt() should return results when no error present`() = runBlocking<Unit> {
        val deferredResult = async(Dispatchers.IO) {
            emailCryptoService.decrypt(securePackage)
        }

        deferredResult.start()
        delay(100L)

        val result = deferredResult.await()
        result shouldBe ByteArray(42)

        verify(mockDeviceKeyManager).privateKeyExists(anyString())
        verify(mockDeviceKeyManager).decryptWithKeyPairId(any(), anyString(), any())
        verify(mockDeviceKeyManager).decryptWithSymmetricKey(any(), any(), any())
    }

    @Test
    fun `decrypt() should throw error for empty body attachment on secure package`() =
        runBlocking<Unit> {
            val bodyAttachment = EmailAttachment(
                fileName = SecureEmailAttachmentType.BODY.fileName,
                contentId = SecureEmailAttachmentType.BODY.contentId,
                mimeType = SecureEmailAttachmentType.BODY.mimeType,
                inlineAttachment = false,
                data = ByteArray(0),
            )
            val securePackage = SecurePackage(setOf(keyAttachment), bodyAttachment)

            val deferredResult = async(Dispatchers.IO) {
                shouldThrow<EmailCryptoService.EmailCryptoServiceException.InvalidArgumentException> {
                    emailCryptoService.decrypt(securePackage)
                }
            }
            deferredResult.start()
            delay(100L)

            deferredResult.await()
        }

    @Test
    fun `decrypt() should throw error for empty key attachments on secure package`() =
        runBlocking<Unit> {
            val securePackage = SecurePackage(emptySet(), bodyAttachment)

            val deferredResult = async(Dispatchers.IO) {
                shouldThrow<EmailCryptoService.EmailCryptoServiceException.InvalidArgumentException> {
                    emailCryptoService.decrypt(securePackage)
                }
            }
            deferredResult.start()
            delay(100L)

            deferredResult.await()
        }

    @Test
    fun `decrypt() should throw error if no keys exist for user`() = runBlocking<Unit> {
        mockDeviceKeyManager.stub {
            on { privateKeyExists(anyString()) } doReturn false
        }

        val deferredResult = async(Dispatchers.IO) {
            shouldThrow<EmailCryptoService.EmailCryptoServiceException.KeyNotFoundException> {
                emailCryptoService.decrypt(securePackage)
            }
        }
        deferredResult.start()
        delay(100L)

        deferredResult.await()

        verify(mockDeviceKeyManager).privateKeyExists(anyString())
    }
}
