/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailAddress

import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.keys.KeyPair

/**
 * Request to provision an email address.
 *
 * @property emailAddress [String] The email address to provision.
 * @property ownershipProofToken [String] The token proving ownership of the Sudo.
 * @property alias [SealedAttributeInput] Optional sealed alias for the email address.
 * @property keyPair [KeyPair] The key pair to use for encrypting email messages.
 */
internal data class ProvisionEmailAddressRequest(
    val emailAddress: String,
    val ownershipProofToken: String,
    val alias: SealedAttributeInput? = null,
    val keyPair: KeyPair,
)

/**
 * Request to deprovision an email address.
 *
 * @property emailAddressId [String] The ID of the email address to deprovision.
 */
internal data class DeprovisionEmailAddressRequest(
    val emailAddressId: String,
)

/**
 * Request to check email address availability.
 *
 * @property localParts The [List] of local parts [String] (username portions) to check.
 * @property domains Optional [List] of domains [String] to check against. If null, checks all available domains.
 */
internal data class CheckEmailAddressAvailabilityRequest(
    val localParts: List<String>,
    val domains: List<String>?,
)

/**
 * Request to update email address metadata.
 *
 * @property id [String] The ID of the email address to update.
 * @property alias [SealedAttributeInput] The new sealed alias for the email address, or null to remove it.
 */
internal data class UpdateEmailAddressMetadataRequest(
    val id: String,
    val alias: SealedAttributeInput?,
    val clearAlias: Boolean = false,
)

/**
 * Request to retrieve an email address by ID.
 *
 * @property id [String] The unique identifier of the email address.
 */
internal data class GetEmailAddressRequest(
    val id: String,
)

/**
 * Request to list email addresses.
 *
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 */
internal data class ListEmailAddressesRequest(
    val limit: Int?,
    val nextToken: String?,
)

/**
 * Request to list email addresses for a specific Sudo ID.
 *
 * @property sudoId [String] The Sudo ID to filter email addresses.
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 */
internal data class ListEmailAddressesForSudoIdRequest(
    val sudoId: String,
    val limit: Int?,
    val nextToken: String?,
)

/**
 * Output from listing email addresses.
 *
 * @property items The [List] of [SealedEmailAddressEntity]s.
 * @property nextToken [String] Optional token for retrieving the next page of results.
 */
internal data class ListEmailAddressesOutput(
    val items: List<SealedEmailAddressEntity>,
    val nextToken: String?,
)

/**
 * Request to lookup public information for email addresses.
 *
 * @property emailAddresses The [List] of email addresses [String] to lookup.
 */
internal data class LookupEmailAddressesPublicInfoRequest(
    val emailAddresses: List<String>,
)

/**
 * Service interface for managing email addresses.
 *
 * Provides operations to provision, deprovision, update, retrieve, and list email addresses.
 */
internal interface EmailAddressService {
    /**
     * Check if email addresses are available to be provisioned within any of the specified domains.
     *
     * @param input [CheckEmailAddressAvailabilityRequest] Parameters used to check email address availability.
     * @return [List] of available email addresses [String].
     */
    suspend fun checkAvailability(input: CheckEmailAddressAvailabilityRequest): List<String>

    /**
     * Provision an email address.
     *
     * @param input [ProvisionEmailAddressRequest] Parameters used to provision an email address.
     * @return [SealedEmailAddressEntity] The provisioned email address entity.
     */
    suspend fun provision(input: ProvisionEmailAddressRequest): SealedEmailAddressEntity

    /**
     * Deprovision an email address.
     *
     * @param input [DeprovisionEmailAddressRequest] Parameters used to deprovision an email address.
     * @return [SealedEmailAddressEntity] The deprovisioned email address entity.
     */
    suspend fun deprovision(input: DeprovisionEmailAddressRequest): SealedEmailAddressEntity

    /**
     * Update the metadata of an email address.
     *
     * @param input [UpdateEmailAddressMetadataRequest] Parameters used to update email address metadata.
     * @return [String] The identifier of the updated email address.
     */
    suspend fun updateMetadata(input: UpdateEmailAddressMetadataRequest): String

    /**
     * Get an email address by its identifier.
     *
     * @param input [GetEmailAddressRequest] Parameters used to retrieve an email address.
     * @return [SealedEmailAddressEntity] The email address entity or null if not found.
     */
    suspend fun get(input: GetEmailAddressRequest): SealedEmailAddressEntity?

    /**
     * List all provisioned email addresses for the user.
     *
     * @param input [ListEmailAddressesRequest] Parameters used to list email addresses.
     * @return [ListEmailAddressesOutput] List API result containing sealed email addresses and next token.
     */
    suspend fun list(input: ListEmailAddressesRequest): ListEmailAddressesOutput

    /**
     * List all provisioned email addresses for a specific Sudo.
     *
     * @param input [ListEmailAddressesForSudoIdRequest] Parameters used to list email addresses for a Sudo.
     * @return [ListEmailAddressesOutput] List API result containing sealed email addresses and next token.
     */
    suspend fun listForSudoId(input: ListEmailAddressesForSudoIdRequest): ListEmailAddressesOutput

    /**
     * Lookup public information for a list of email addresses.
     *
     * @param input [LookupEmailAddressesPublicInfoRequest] Parameters used to lookup email address public info.
     * @return [List] of [EmailAddressPublicInfoEntity]s.
     */
    suspend fun lookupPublicInfo(input: LookupEmailAddressesPublicInfoRequest): List<EmailAddressPublicInfoEntity>
}
