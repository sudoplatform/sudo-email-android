/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudokeymanager.KeyManagerException
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import java.util.UUID

/**
 * Responsible for Managing the lifecycle of key pairs associated with the email service.
 */
internal class DefaultServiceKeyManager(
    private val keyRingServiceName: String,
    private val userClient: SudoUserClient,
    private val keyManager: KeyManagerInterface,
    private val logger: Logger =
        Logger(
            LogConstants.SUDOLOG_TAG,
            AndroidUtilsLogDriver(LogLevel.INFO),
        ),
) : DefaultDeviceKeyManager(keyManager, logger),
    ServiceKeyManager {
    companion object {
        /** Name of the key store password whose value is the singleton key pair's identifier. */
        internal const val SINGLETON_KEY_ID_NAME = "singleton-key-id"
    }

    /** Guards reading and changing the singleton key pair and its recorded identifier. */
    private val singletonLock = Any()

    @Throws(DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException::class)
    override fun getKeyRingId(): String {
        try {
            val userId =
                userClient.getSubject()
                    ?: throw DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException("UserId not found")
            return "$keyRingServiceName.$userId"
        } catch (e: Exception) {
            throw DeviceKeyManager.DeviceKeyManagerException.UserIdNotFoundException(
                "UserId could not be accessed",
                e,
            )
        }
    }

    override fun getKeyPairWithId(id: String): KeyPair? {
        try {
            val publicKey =
                keyManager.getPublicKeyData(id)
                    ?: return null
            val privateKey =
                keyManager.getPrivateKeyData(id)
                    ?: return null
            return KeyPair(
                keyId = id,
                keyRingId = getKeyRingId(),
                publicKey = publicKey,
                privateKey = privateKey,
            )
        } catch (e: KeyManagerException) {
            throw DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException(
                "KeyManager exception",
                e,
            )
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun generateKeyPair(): KeyPair {
        val keyId = UUID.randomUUID().toString()
        try {
            // Generate the key pair
            keyManager.generateKeyPair(keyId, true)

            val publicKey = keyManager.getPublicKeyData(keyId)
            val privateKey = keyManager.getPrivateKeyData(keyId)
            return KeyPair(
                keyId = keyId,
                keyRingId = getKeyRingId(),
                publicKey = publicKey!!,
                privateKey = privateKey!!,
            )
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.KeyGenerationException(
                "Failed to generate key pair",
                e,
            )
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun getSingletonKeyPair(): KeyPair? =
        synchronized(singletonLock) {
            getSingletonKeyId()?.let { readSingletonKeyPair(it) }
        }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun createSingletonKeyPairIfAbsent(): SingletonKeyPairResult =
        synchronized(singletonLock) {
            val recordedKeyId = getSingletonKeyId()
            recordedKeyId?.let { readSingletonKeyPair(it) }?.let {
                return SingletonKeyPairResult(keyPair = it, created = false)
            }

            // Generated before its identifier is recorded, so the record never names a key
            // pair that does not exist.
            val keyPair = generateKeyPair()
            try {
                recordSingletonKeyId(keyPair.keyId)
            } catch (e: DeviceKeyManager.DeviceKeyManagerException) {
                try {
                    keyManager.deleteKeyPair(keyPair.keyId)
                } catch (deleteError: Exception) {
                    logger.warning("Couldn't delete key pair ${keyPair.keyId} after failing to record it: $deleteError")
                }
                throw e
            }

            if (recordedKeyId != null) {
                reportReplacedSingletonKeyPair(missingKeyId = recordedKeyId, replacementKeyId = keyPair.keyId)
            } else {
                logger.info("Created singleton key pair ${keyPair.keyId}")
            }
            SingletonKeyPairResult(keyPair = keyPair, created = true)
        }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun getSingletonKeyId(): String? {
        try {
            return keyManager
                .getPassword(SINGLETON_KEY_ID_NAME)
                ?.toString(Charsets.UTF_8)
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException(
                "Failed to read the singleton key pair identifier",
                e,
            )
        }
    }

    @Throws(DeviceKeyManager.DeviceKeyManagerException::class)
    override fun removeKeyPair(keyId: String) {
        synchronized(singletonLock) {
            if (keyId == getSingletonKeyId()) {
                return
            }
            try {
                keyManager.deleteKeyPair(keyId)
            } catch (e: Exception) {
                logger.error("error $e")
                throw DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException(
                    "Failed to remove key pair",
                    e,
                )
            }
        }
    }

    /**
     * The key archive leaves public keys out, but the key store regenerates the public key of
     * each private key it imports, so the singleton key pair is whole again afterwards.
     */
    override fun importKeys(archiveData: ByteArray) {
        synchronized(singletonLock) {
            super.importKeys(archiveData)
        }
    }

    /**
     * The recorded singleton key pair, or `null` if its private key is not on this device.
     *
     * @throws DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException if the
     *  private key is present without its public key. Replacing the key pair then would split
     *  email addresses across two singleton key pairs.
     */
    private fun readSingletonKeyPair(keyId: String): KeyPair? {
        getKeyPairWithId(keyId)?.let { return it }
        if (privateKeyExists(keyId)) {
            val message = "Singleton key pair $keyId has a private key but no public key"
            logger.error(message)
            throw DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException(message)
        }
        return null
    }

    private fun recordSingletonKeyId(keyId: String) {
        try {
            keyManager.deletePassword(SINGLETON_KEY_ID_NAME)
            keyManager.addPassword(keyId.toByteArray(Charsets.UTF_8), SINGLETON_KEY_ID_NAME)
        } catch (e: Exception) {
            logger.error("error $e")
            throw DeviceKeyManager.DeviceKeyManagerException.KeyOperationFailedException(
                "Failed to record the singleton key pair identifier",
                e,
            )
        }
    }

    /**
     * Logs, and outputs to the log driver as an error, that the singleton key pair's private
     * key was missing and a new singleton key pair replaced it. Email addresses sealed to the
     * missing key cannot be read on this device until a key archive containing it is imported.
     */
    private fun reportReplacedSingletonKeyPair(
        missingKeyId: String,
        replacementKeyId: String,
    ) {
        val message =
            "Singleton key pair $missingKeyId has no private key on this device; replaced it with $replacementKeyId. " +
                "Email addresses sealed to $missingKeyId can't be read here until a key archive containing it is imported."
        logger.error(message)
        logger.outputError(
            Error(message),
            mapOf("missingKeyId" to missingKeyId, "replacementKeyId" to replacementKeyId),
        )
    }
}
