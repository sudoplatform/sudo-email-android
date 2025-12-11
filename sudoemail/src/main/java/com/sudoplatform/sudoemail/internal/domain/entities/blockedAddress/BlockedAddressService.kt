/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.blockedAddress

import com.sudoplatform.sudoemail.internal.domain.entities.common.BatchOperationResultEntity
import com.sudoplatform.sudoemail.internal.domain.entities.common.SealedAttributeEntity

/**
 * Represents a single email address to be blocked.
 *
 * @property sealedValue [SealedAttributeEntity] The sealed (encrypted) email address value.
 * @property hashedBlockedValue [String] The hashed value of the email address for matching.
 * @property hashAlgorithm [BlockedAddressHashAlgorithmEntity] The hash algorithm used to generate the hashed value.
 * @property action [BlockedEmailAddressActionEntity] The action to take when this address is encountered (e.g., DROP, SPAM).
 */
internal data class BlockEmailAddressRequestItem(
    val sealedValue: SealedAttributeEntity,
    val hashedBlockedValue: String,
    val hashAlgorithm: BlockedAddressHashAlgorithmEntity,
    val action: BlockedEmailAddressActionEntity,
)

/**
 * Request to block one or more email addresses.
 *
 * @property owner [String] The identifier of the owner performing the block operation.
 * @property blockedAddresses [List] The list of [BlockEmailAddressRequestItem]s.
 * @property emailAddressId [String] Optional email address ID to associate the blocks with.
 */
internal data class BlockEmailAddressesRequest(
    val owner: String,
    val blockedAddresses: List<BlockEmailAddressRequestItem>,
    val emailAddressId: String?,
)

/**
 * Request to unblock email addresses by their hashed values.
 *
 * @property owner [String] The identifier of the owner performing the unblock operation.
 * @property hashedBlockedValues [List] The list of hashed value [String]s to unblock.
 */
internal data class UnblockEmailAddressesRequest(
    val owner: String,
    val hashedBlockedValues: List<String>,
)

/**
 * Request to retrieve the email address blocklist.
 *
 * @property owner [String] The identifier of the owner whose blocklist to retrieve.
 */
internal data class GetEmailAddressBlocklistRequest(
    val owner: String,
)

/**
 * Service interface for managing blocked email addresses.
 *
 * Provides operations to block, unblock, and retrieve blocked email addresses.
 */
internal interface BlockedAddressService {
    /**
     * Blocks email addresses by adding them to the blocklist.
     *
     * @param request [BlockEmailAddressesRequest] The request containing the addresses to block.
     * @return [BatchOperationResultEntity] A batch operation result indicating which addresses were successfully blocked and which failed.
     * @throws com.sudoplatform.sudoemail.SudoEmailClient.EmailBlocklistException if the operation fails.
     */
    suspend fun blockEmailAddresses(request: BlockEmailAddressesRequest): BatchOperationResultEntity<String, String>

    /**
     * Unblocks email addresses by removing them from the blocklist.
     *
     * @param request [UnblockEmailAddressesRequest] The request containing the hashed values of addresses to unblock.
     * @return [BatchOperationResultEntity] A batch operation result indicating which addresses were successfully unblocked and which failed.
     * @throws com.sudoplatform.sudoemail.SudoEmailClient.EmailBlocklistException if the operation fails.
     */
    suspend fun unblockEmailAddresses(request: UnblockEmailAddressesRequest): BatchOperationResultEntity<String, String>

    /**
     * Retrieves the complete blocklist of email addresses for the current user.
     *
     * @param request [GetEmailAddressBlocklistRequest] The request containing the owner identifier.
     * @return A [List] of [BlockedAddressEntity]s. Returns an empty list if no addresses are blocked.
     * @throws com.sudoplatform.sudoemail.SudoEmailClient.EmailBlocklistException if the operation fails.
     */
    suspend fun getEmailAddressBlocklist(request: GetEmailAddressBlocklistRequest): List<BlockedAddressEntity>
}
