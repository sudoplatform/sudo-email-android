/*
 * Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.sealing

import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger

internal class DefaultSealingService(
    private val deviceKeyManager: DeviceKeyManager,
    private val logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))
) : SealingService {

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun sealString(keyId: String, payload: ByteArray): ByteArray {
        try {
            return deviceKeyManager.encryptWithSymmetricKeyId(keyId, payload)
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.EncryptionException("Failed to seal string", e)
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun unsealString(keyId: String, payload: ByteArray): ByteArray {
        try {
            return deviceKeyManager.decryptWithSymmetricKeyId(keyId, payload)
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.DecryptionException("Failed to unseal string", e)
        }
    }
}
