/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.types.EmailAddress
import com.sudoplatform.sudoemail.types.EmailFolder
import com.sudoplatform.sudoemail.types.Owner
import com.sudoplatform.sudoemail.types.PartialEmailAddress
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudokeymanager.KeyNotFoundException
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddress as EmailAddressFragment
import com.sudoplatform.sudoemail.graphql.fragment.EmailFolder as EmailFolderFragment

/**
 * Transformer responsible for transforming the [EmailAddress] GraphQL data
 * types to the entity type that is exposed to users.
 */
internal object EmailAddressTransformer {

    /**
     * Transform the input [String] type into the corresponding sealed GraphQL type [SealedAttributeInput].
     */
    fun String?.toAliasInput(deviceKeyManager: DeviceKeyManager): SealedAttributeInput? {
        if (this == null) {
            return null
        }
        val symmetricKeyId = deviceKeyManager.getCurrentSymmetricKeyId() ?: throw KeyNotFoundException("Symmetric key not found")
        val encryptedAlias = deviceKeyManager.encryptWithSymmetricKeyId(symmetricKeyId, this.toByteArray(Charsets.UTF_8))
        val base64EncodedEncryptedAlias = String(Base64.encode(encryptedAlias), Charsets.UTF_8)
        return SealedAttributeInput(
            algorithm = SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
            base64EncodedSealedData = base64EncodedEncryptedAlias,
            keyId = symmetricKeyId,
            plainTextType = "string",
        )
    }

    /**
     * Transform the [EmailAddressFragment] GraphQL type to its entity type.
     *
     * @param deviceKeyManager [DeviceKeyManager] Used to retrieve keys to unseal data.
     * @param emailAddress [EmailAddressFragment] The GraphQL type.
     * @return The [EmailAddress] entity type.
     */
    fun toEntity(
        deviceKeyManager: DeviceKeyManager,
        emailAddress: EmailAddressFragment,
    ): EmailAddress {
        val emailAddressWithoutFolders = emailAddress.emailAddressWithoutFolders
        val emailAddressWithoutFoldersEntity = this.toEntity(
            deviceKeyManager,
            emailAddressWithoutFolders = emailAddressWithoutFolders,
        )
        return EmailAddress(
            id = emailAddressWithoutFoldersEntity.id,
            owner = emailAddressWithoutFoldersEntity.owner,
            owners = emailAddressWithoutFoldersEntity.owners,
            emailAddress = emailAddressWithoutFoldersEntity.emailAddress,
            size = emailAddressWithoutFoldersEntity.size,
            numberOfEmailMessages = emailAddressWithoutFoldersEntity.numberOfEmailMessages,
            version = emailAddressWithoutFoldersEntity.version,
            createdAt = emailAddressWithoutFoldersEntity.createdAt,
            updatedAt = emailAddressWithoutFoldersEntity.updatedAt,
            lastReceivedAt = emailAddressWithoutFoldersEntity.lastReceivedAt,
            alias = emailAddressWithoutFoldersEntity.alias,
            folders = emailAddress.folders.toFolders(),
        )
    }

    /**
     * Transform the [EmailAddressWithoutFolders] GraphQL type to its entity type.
     *
     * @param deviceKeyManager [DeviceKeyManager] Used to retrieve keys to unseal data.
     * @param emailAddressWithoutFolders [EmailAddressWithoutFolders] The GraphQL type.
     * @return The [EmailAddress] entity type.
     */
    fun toEntity(
        deviceKeyManager: DeviceKeyManager,
        emailAddressWithoutFolders: EmailAddressWithoutFolders,
    ): EmailAddress {
        return EmailAddress(
            id = emailAddressWithoutFolders.id,
            owner = emailAddressWithoutFolders.owner,
            owners = emailAddressWithoutFolders.owners.toEmailAddressOwners(),
            emailAddress = emailAddressWithoutFolders.emailAddress,
            size = emailAddressWithoutFolders.size,
            numberOfEmailMessages = emailAddressWithoutFolders.numberOfEmailMessages,
            version = emailAddressWithoutFolders.version,
            createdAt = emailAddressWithoutFolders.createdAtEpochMs.toDate(),
            updatedAt = emailAddressWithoutFolders.updatedAtEpochMs.toDate(),
            lastReceivedAt = emailAddressWithoutFolders.lastReceivedAtEpochMs.toDate(),
            alias = emailAddressWithoutFolders.alias?.let {
                val sealedAttribute = it.sealedAttribute
                val symmetricKeyInfo = KeyInfo(sealedAttribute.keyId, KeyType.SYMMETRIC_KEY, sealedAttribute.algorithm)
                val aliasUnsealer = Unsealer(deviceKeyManager, symmetricKeyInfo)
                aliasUnsealer.unseal(it)
            },
            folders = emptyList(),
        )
    }

    /**
     * Transform the [EmailAddressFragment] into a [PartialEmailAddress].
     *
     * @param emailAddress [EmailAddressFragment] The GraphQL query result.
     * @return The [PartialEmailAddress] entity type.
     */
    fun toPartialEntity(
        emailAddress: EmailAddressFragment,
    ): PartialEmailAddress {
        val partialEmailAddressWithoutFolders = this.toPartialEntity(
            emailAddressWithoutFolders = emailAddress.emailAddressWithoutFolders,
        )
        return PartialEmailAddress(
            id = partialEmailAddressWithoutFolders.id,
            owner = partialEmailAddressWithoutFolders.owner,
            owners = partialEmailAddressWithoutFolders.owners,
            emailAddress = partialEmailAddressWithoutFolders.emailAddress,
            size = partialEmailAddressWithoutFolders.size,
            numberOfEmailMessages = partialEmailAddressWithoutFolders.numberOfEmailMessages,
            version = partialEmailAddressWithoutFolders.version,
            createdAt = partialEmailAddressWithoutFolders.createdAt,
            updatedAt = partialEmailAddressWithoutFolders.updatedAt,
            lastReceivedAt = partialEmailAddressWithoutFolders.lastReceivedAt,
            folders = emailAddress.folders.toFolders(),
        )
    }

    private fun toPartialEntity(
        emailAddressWithoutFolders: EmailAddressWithoutFolders,
    ): EmailAddress {
        return EmailAddress(
            id = emailAddressWithoutFolders.id,
            owner = emailAddressWithoutFolders.owner,
            owners = emailAddressWithoutFolders.owners.toEmailAddressOwners(),
            emailAddress = emailAddressWithoutFolders.emailAddress,
            size = emailAddressWithoutFolders.size,
            numberOfEmailMessages = emailAddressWithoutFolders.numberOfEmailMessages,
            version = emailAddressWithoutFolders.version,
            createdAt = emailAddressWithoutFolders.createdAtEpochMs.toDate(),
            updatedAt = emailAddressWithoutFolders.updatedAtEpochMs.toDate(),
            lastReceivedAt = emailAddressWithoutFolders.lastReceivedAtEpochMs.toDate(),
            folders = emptyList(),
        )
    }

    private fun EmailAddressFragment.Folder.toEmailFolder(): EmailFolder {
        return EmailFolder(
            id = emailFolder.id,
            owner = emailFolder.owner,
            owners = emailFolder.owners.toEmailFolderOwners(),
            emailAddressId = emailFolder.emailAddressId,
            folderName = emailFolder.folderName,
            size = emailFolder.size,
            unseenCount = emailFolder.unseenCount.toInt(),
            version = emailFolder.version,
            createdAt = emailFolder.createdAtEpochMs.toDate(),
            updatedAt = emailFolder.updatedAtEpochMs.toDate(),
        )
    }

    private fun List<EmailAddressFragment.Folder>.toFolders(): List<EmailFolder> {
        return this.map {
            it.toEmailFolder()
        }
    }

    private fun EmailAddressWithoutFolders.Owner.toOwner(): Owner {
        return Owner(id = id, issuer = issuer)
    }

    private fun List<EmailAddressWithoutFolders.Owner>.toEmailAddressOwners(): List<Owner> {
        return this.map {
            it.toOwner()
        }
    }

    private fun EmailFolderFragment.Owner.toOwner(): Owner {
        return Owner(id = id, issuer = issuer)
    }

    private fun List<EmailFolderFragment.Owner>.toEmailFolderOwners(): List<Owner> {
        return this.map {
            it.toOwner()
        }
    }
}
