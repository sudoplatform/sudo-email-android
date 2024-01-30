/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import android.text.util.Rfc822Tokenizer
import com.google.gson.Gson
import com.sudoplatform.sudoemail.graphql.fragment.SealedEmailMessage
import com.sudoplatform.sudoemail.graphql.type.EmailMessageDirection
import com.sudoplatform.sudoemail.graphql.type.EmailMessageState
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.types.Direction
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.Owner
import com.sudoplatform.sudoemail.types.PartialEmailMessage
import com.sudoplatform.sudoemail.types.State

/**
 * Data class used to deserialize the email message RFC822 header value.
 */
internal data class EmailHeaderDetails(
    val from: List<EmailMessage.EmailAddress>,
    val to: List<EmailMessage.EmailAddress>,
    val cc: List<EmailMessage.EmailAddress>,
    val bcc: List<EmailMessage.EmailAddress>,
    val replyTo: List<EmailMessage.EmailAddress>,
    val hasAttachments: Boolean = false,
    val subject: String? = null,
)

/**
 * Transformer responsible for transforming the [EmailMessage] GraphQL data
 * types to the entity type that is exposed to users.
 */
internal object EmailMessageTransformer {

    /**
     * Transform the [SealedEmailMessage] GraphQL type to its entity type.
     *
     * @param deviceKeyManager [DeviceKeyManager] Used to retrieve keys to unseal data.
     * @param sealedEmailMessage [SealedEmailMessage] The GraphQL type.
     * @return The [EmailMessage] entity type.
     */
    fun toEntity(
        deviceKeyManager: DeviceKeyManager,
        sealedEmailMessage: SealedEmailMessage,
    ): EmailMessage {
        val keyInfo = KeyInfo(sealedEmailMessage.rfc822Header().keyId(), KeyType.PRIVATE_KEY, sealedEmailMessage.rfc822Header().algorithm())
        val unsealer = Unsealer(deviceKeyManager, keyInfo)

        val unsealedRfc822HeaderString = unsealer.unseal(sealedEmailMessage.rfc822Header().base64EncodedSealedData())
        val unsealedRfc822Header = Gson().fromJson(unsealedRfc822HeaderString, EmailHeaderDetails::class.java)

        return EmailMessage(
            id = sealedEmailMessage.id(),
            clientRefId = sealedEmailMessage.clientRefId(),
            owner = sealedEmailMessage.owner(),
            owners = sealedEmailMessage.owners().toOwners(),
            emailAddressId = sealedEmailMessage.emailAddressId(),
            folderId = sealedEmailMessage.folderId(),
            previousFolderId = sealedEmailMessage.previousFolderId(),
            seen = sealedEmailMessage.seen(),
            direction = sealedEmailMessage.direction().toEmailMessageDirection(),
            state = sealedEmailMessage.state().toEmailMessageState(),
            version = sealedEmailMessage.version(),
            sortDate = sealedEmailMessage.sortDateEpochMs().toDate(),
            createdAt = sealedEmailMessage.createdAtEpochMs().toDate(),
            updatedAt = sealedEmailMessage.updatedAtEpochMs().toDate(),
            size = sealedEmailMessage.size(),
            from = unsealedRfc822Header.from,
            to = unsealedRfc822Header.to,
            cc = unsealedRfc822Header.cc,
            bcc = unsealedRfc822Header.bcc,
            replyTo = unsealedRfc822Header.replyTo,
            subject = unsealedRfc822Header.subject,
            hasAttachments = unsealedRfc822Header.hasAttachments,
        )
    }

    /**
     * Transform the [SealedEmailMessage] into a [PartialEmailMessage].
     *
     * @param sealedEmailMessage [SealedEmailMessage] The GraphQL type.
     * @return The [PartialEmailMessage] entity type.
     */
    fun toPartialEntity(
        sealedEmailMessage: SealedEmailMessage,
    ): PartialEmailMessage {
        return PartialEmailMessage(
            id = sealedEmailMessage.id(),
            clientRefId = sealedEmailMessage.clientRefId(),
            owner = sealedEmailMessage.owner(),
            owners = sealedEmailMessage.owners().toOwners(),
            emailAddressId = sealedEmailMessage.emailAddressId(),
            folderId = sealedEmailMessage.folderId(),
            previousFolderId = sealedEmailMessage.previousFolderId(),
            seen = sealedEmailMessage.seen(),
            direction = sealedEmailMessage.direction().toEmailMessageDirection(),
            state = sealedEmailMessage.state().toEmailMessageState(),
            version = sealedEmailMessage.version(),
            sortDate = sealedEmailMessage.sortDateEpochMs().toDate(),
            createdAt = sealedEmailMessage.createdAtEpochMs().toDate(),
            updatedAt = sealedEmailMessage.updatedAtEpochMs().toDate(),
            size = sealedEmailMessage.size(),
        )
    }

    /**
     * Transform and unseal the RFC 822 data result.
     *
     * @param deviceKeyManager [DeviceKeyManager] Used to retrieve keys to unseal data.
     * @param keyId [String] Identifier of the key used to decrypt the payload.
     * @param algorithm [String] The algorithm used to decrypt the payload.
     * @param sealedRfc822Data [String] The sealed RFC 822 payload data.
     * @return The unsealed RFC 822 email message data.
     */
    fun toUnsealedRfc822Data(
        deviceKeyManager: DeviceKeyManager,
        keyId: String,
        algorithm: String,
        sealedRfc822Data: ByteArray,
    ): ByteArray {
        val keyInfo = KeyInfo(keyId, KeyType.PRIVATE_KEY, algorithm)
        val unsealer = Unsealer(deviceKeyManager, keyInfo)
        return unsealer.unsealBytes(sealedRfc822Data)
    }

    private fun EmailMessageDirection.toEmailMessageDirection(): Direction {
        for (value in Direction.values()) {
            if (value.name == this.name) {
                return value
            }
        }
        return Direction.UNKNOWN
    }

    private fun EmailMessageState.toEmailMessageState(): State {
        for (value in State.values()) {
            if (value.name == this.name) {
                return value
            }
        }
        return State.UNKNOWN
    }

    /**
     * Transform a string that might contain an RFC822 email address and display
     * name into an [EmailMessage.EmailAddress].
     */
    fun toEmailAddress(value: String): EmailMessage.EmailAddress? {
        Rfc822Tokenizer.tokenize(value).firstOrNull {
            val address = it.address
            return when {
                address.isNullOrBlank() -> {
                    null
                }
                it.name.isNullOrBlank() -> {
                    EmailMessage.EmailAddress(address)
                }
                else -> {
                    EmailMessage.EmailAddress(address, it.name)
                }
            }
        }
        return null
    }

    private fun List<SealedEmailMessage.Owner>.toOwners(): List<Owner> {
        return this.map {
            it.toOwner()
        }
    }

    private fun SealedEmailMessage.Owner.toOwner(): Owner {
        return Owner(id = id(), issuer = issuer())
    }
}
