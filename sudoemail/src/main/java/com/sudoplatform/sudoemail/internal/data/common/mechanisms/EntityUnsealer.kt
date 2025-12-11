/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.data.common.mechanisms

import androidx.annotation.VisibleForTesting
import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.internal.data.emailAddress.transformers.EmailAddressTransformer
import com.sudoplatform.sudoemail.internal.data.emailFolder.transformers.EmailFolderTransformer
import com.sudoplatform.sudoemail.internal.domain.entities.common.KeyInfo
import com.sudoplatform.sudoemail.internal.domain.entities.common.KeyType
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.SealedEmailAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailAddress.UnsealedEmailAddressEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.SealedEmailFolderEntity
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.UnsealedEmailFolderEntity
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudoemail.util.Constants
import com.sudoplatform.sudokeymanager.KeyManagerInterface

/**
 * Base class for unsealing (decrypting) entity types with sealed (encrypted) attributes.
 *
 * @param Sealed The sealed entity type as input.
 * @param Unsealed The unsealed entity type as output.
 * @property deviceKeyManager [DeviceKeyManager] The device key manager for decryption operations.
 */
internal sealed class EntityUnsealer<in Sealed, out Unsealed> {
    abstract val deviceKeyManager: DeviceKeyManager

    companion object {
        /** Size of the AES symmetric key in bits */
        @VisibleForTesting
        const val KEY_SIZE_AES = 256

        /** RSA block size in bytes */
        const val BLOCK_SIZE_RSA = 256
    }

    private fun decrypt(
        keyInfo: KeyInfo,
        data: ByteArray,
    ): String =
        when (keyInfo.keyType) {
            KeyType.PRIVATE_KEY -> {
                val algorithm =
                    when (keyInfo.algorithm) {
                        Constants.DEFAULT_PUBLIC_KEY_ALGORITHM -> KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1
                        else -> KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_PKCS1
                    }
                if (data.size < KEY_SIZE_AES) {
                    throw Unsealer.UnsealerException.SealedDataTooShortException("Sealed value too short")
                }
                val aesEncrypted = data.copyOfRange(0, BLOCK_SIZE_RSA)
                val cipherData = data.copyOfRange(KEY_SIZE_AES, data.size)
                val aesDecrypted = deviceKeyManager.decryptWithKeyPairId(aesEncrypted, keyInfo.keyId, algorithm)
                String(deviceKeyManager.decryptWithSymmetricKey(aesDecrypted, cipherData), Charsets.UTF_8)
            }
            KeyType.SYMMETRIC_KEY -> {
                String(deviceKeyManager.decryptWithSymmetricKeyId(keyInfo.keyId, data))
            }
        }

    /**
     * Unseals a sealed attribute entity.
     *
     * @param sealedEntity [SealedAttributeEntity] The sealed attribute to unseal.
     * @return [String] The unsealed string value.
     * @throws Unsealer.UnsealerException if unsealing fails.
     */
    fun unseal(sealedEntity: SealedAttributeEntity): String {
        val keyInfo =
            KeyInfo(
                keyId = sealedEntity.keyId,
                keyType = KeyType.SYMMETRIC_KEY,
                algorithm = sealedEntity.algorithm,
            )
        val algorithm = keyInfo.algorithm
        if (!SymmetricKeyEncryptionAlgorithm.Companion.isAlgorithmSupported(algorithm)) {
            throw Unsealer.UnsealerException.UnsupportedAlgorithmException(algorithm)
        }
        val valueBytes = Base64.decode(sealedEntity.base64EncodedSealedData)
        return decrypt(keyInfo, valueBytes)
    }

    /**
     * Unseals a sealed entity to its unsealed form.
     *
     * @param sealedEntity The sealed entity to unseal.
     * @return The unsealed entity.
     */
    abstract suspend fun unseal(sealedEntity: Sealed): Unsealed
}

/**
 * Unsealer for email folder entities.
 *
 * @property deviceKeyManager [DeviceKeyManager] The device key manager for decryption operations.
 */
internal class EmailFolderUnsealer(
    override val deviceKeyManager: DeviceKeyManager,
) : EntityUnsealer<SealedEmailFolderEntity, UnsealedEmailFolderEntity>() {
    /**
     * Unseals a sealed email folder entity.
     *
     * @param sealedEntity [SealedEmailFolderEntity] The sealed folder entity.
     * @return [UnsealedEmailFolderEntity] The unsealed folder entity.
     */
    override suspend fun unseal(sealedEntity: SealedEmailFolderEntity): UnsealedEmailFolderEntity {
        var customFolderName: String? = null

        if (sealedEntity.sealedCustomFolderName != null) {
            customFolderName = unseal(sealedEntity.sealedCustomFolderName)
        }

        return EmailFolderTransformer.toUnsealedEntity(sealedEntity, customFolderName)
    }
}

/**
 * Unsealer for email address entities.
 *
 * @property deviceKeyManager [DeviceKeyManager] The device key manager for decryption operations.
 */
internal class EmailAddressUnsealer(
    override val deviceKeyManager: DeviceKeyManager,
) : EntityUnsealer<SealedEmailAddressEntity, UnsealedEmailAddressEntity>() {
    /**
     * Unseals a sealed email address entity.
     *
     * @param sealedEntity [SealedEmailAddressEntity] The sealed email address entity.
     * @return [UnsealedEmailAddressEntity] The unsealed email address entity.
     */
    override suspend fun unseal(sealedEntity: SealedEmailAddressEntity): UnsealedEmailAddressEntity {
        var alias: String? = null

        if (sealedEntity.sealedAlias != null) {
            alias = unseal(sealedEntity.sealedAlias)
        }

        // Use EmailFolderUnsealer to unseal each folder
        val folderUnsealer =
            EmailFolderUnsealer(
                deviceKeyManager = deviceKeyManager,
            )
        val folders = sealedEntity.folders.map { folderUnsealer.unseal(it) }

        return EmailAddressTransformer.toUnsealedEntity(sealedEntity, folders, alias)
    }
}
