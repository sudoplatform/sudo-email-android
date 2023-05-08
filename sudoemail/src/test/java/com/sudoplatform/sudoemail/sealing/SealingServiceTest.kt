/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.sealing

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.robolectric.RobolectricTestRunner

/**
 * Test that the [SealingService] works properly.
 */
@RunWith(RobolectricTestRunner::class)
class SealingServiceTest : BaseTests() {

    private val mockDeviceKeyManager by before {
        mock<DeviceKeyManager>().stub {
            on { encryptWithSymmetricKeyId(anyString(), any()) } doReturn ByteArray(42)
        }
    }

    private val sealingService by before {
        DefaultSealingService(
            mockDeviceKeyManager,
            mockLogger
        )
    }

    @Test
    fun sealStringReturnsProperSealString() {
        val keyId = "keyValue"
        val payload = "this is a payload".toByteArray()

        val result = sealingService.sealString(keyId, payload)

        result shouldNotBe null
    }

    @Test
    fun sealStringShouldThrowIfDeviceKeyManagerErrors() {
        mockDeviceKeyManager.stub {
            on {
                encryptWithSymmetricKeyId(
                    anyString(),
                    any()
                )
            } doThrow DeviceKeyManager.DeviceKeyManagerException.EncryptionException("Bad")
        }

        val keyId = "keyValue"
        val payload = "this is a payload".toByteArray()

        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.EncryptionException> {
            sealingService.sealString(keyId, payload)
        }
    }
}
