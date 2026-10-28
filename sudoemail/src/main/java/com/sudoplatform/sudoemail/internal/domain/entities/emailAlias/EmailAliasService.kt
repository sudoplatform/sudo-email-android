package com.sudoplatform.sudoemail.internal.domain.entities.emailAlias

import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.keys.KeyPair
import java.util.Date

/**
 * Request to provision an email alias.
 *
 * @property aliasAddress [String] The alias email address to provision.
 * @property realAddress [String] The real email address that the alias forwards to.
 * @property metadata [SealedAttributeInput] Optional sealed metadata for the email alias.
 * @property expiresAt [Date] Optional expiration date for the email alias.
 * @property ownershipProofToken [String] The token proving ownership of the Sudo.
 */
internal data class ProvisionEmailAliasRequest(
    val aliasAddress: String,
    val realAddress: String,
    val metadata: SealedAttributeInput? = null,
    val expiresAt: Date? = null,
    val ownershipProofToken: String,
    val keyPair: KeyPair,
)

/**
 * Request to deprovision an email alias.
 *
 * @property emailAliasId [String] The ID of the email alias to deprovision.
 */
internal data class DeprovisionEmailAliasRequest(
    val emailAliasId: String,
)

/**
 * Request to update an email alias.
 *
 * @property id [String] The ID of the email alias to update.
 * @property metadata [SealedAttributeInput] Optional sealed metadata for the email alias.
 * @property expiresAt [Date] Optional expiration date for the email alias.
 * @property realAddress [String] Optional real email address for the email alias. Only valid for external aliases in a pending state.
 */
internal data class UpdateEmailAliasRequest(
    val id: String,
    val metadata: SealedAttributeInput? = null,
    val clearMetadata: Boolean = false,
    val expiresAt: Date? = null,
    val clearExpiresAt: Boolean = false,
    val realAddress: String? = null,
)

/**
 * Request to enable an email alias.
 *
 * @property emailAliasId [String] The ID of the email alias to enable.
 */
internal data class EnableEmailAliasRequest(
    val emailAliasId: String,
)

/**
 * Request to disable an email alias.
 *
 * @property emailAliasId [String] The ID of the email alias to disable.
 */
internal data class DisableEmailAliasRequest(
    val emailAliasId: String,
)

/**
 * Request to verify an external email address for an email alias.
 *
 * @property emailAddress [String] The external email address to verify.
 * @property emailAliasId [String] The ID of the email alias.
 * @property verificationCode [String] Optional verification code. If null, triggers sending verification email.
 */
internal data class VerifyExternalEmailAddressRequest(
    val emailAddress: String,
    val emailAliasId: String,
    val verificationCode: String? = null,
)

/**
 * Result entity for external email address verification.
 *
 * @property isVerified [Boolean] True if the external address was successfully verified.
 * @property reason [String] Optional reason for verification failure.
 */
internal data class VerifyExternalEmailAddressResultEntity(
    val isVerified: Boolean,
    val reason: String? = null,
)

/**
 * Filter criteria for email alias status.
 *
 * @property ne [EmailAliasEntityStatus] Status not equal to this value.
 * @property eq [EmailAliasEntityStatus] Status equal to this value.
 * @property `in` [List<EmailAliasEntityStatus>] Status in this list of values.
 * @property notIn [List<EmailAliasEntityStatus>] Status not in this list of values.
 */
internal data class EmailAliasStatusFilter(
    val ne: EmailAliasEntityStatus? = null,
    val eq: EmailAliasEntityStatus? = null,
    val `in`: List<EmailAliasEntityStatus>? = null,
    val notIn: List<EmailAliasEntityStatus>? = null,
)

/**
 * Filter criteria for email alias real address type.
 *
 * @property ne [EmailAliasEntityRealAddressType] Real address type not equal to this value.
 * @property eq [EmailAliasEntityRealAddressType] Real address type equal to this value.
 * @property `in` [List<EmailAliasEntityRealAddressType>] Real address type in this list of values.
 * @property notIn [List<EmailAliasEntityRealAddressType>] Real address type not in this list of values.
 */
internal data class EmailAliasRealAddressTypeFilter(
    val ne: EmailAliasEntityRealAddressType? = null,
    val eq: EmailAliasEntityRealAddressType? = null,
    val `in`: List<EmailAliasEntityRealAddressType>? = null,
    val notIn: List<EmailAliasEntityRealAddressType>? = null,
)

/**
 * Filter criteria for email aliases.
 *
 * @property status [EmailAliasStatusFilter] Optional status filter.
 * @property realAddressType [EmailAliasRealAddressTypeFilter] Optional real address type filter.
 * @property and [List<EmailAliasFilter>] List of filters that must all match (AND logic).
 * @property or [List<EmailAliasFilter>] List of filters where at least one must match (OR logic).
 * @property not [EmailAliasFilter] Filter that must not match (NOT logic).
 */
internal data class EmailAliasFilter(
    val status: EmailAliasStatusFilter? = null,
    val realAddressType: EmailAliasRealAddressTypeFilter? = null,
    val and: List<EmailAliasFilter>? = null,
    val or: List<EmailAliasFilter>? = null,
    val not: EmailAliasFilter? = null,
)

/**
 * Request to list email aliases for an owner.
 *
 * @property filter [EmailAliasFilter] Optional filter criteria for the email aliases.
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 */
internal data class ListEmailAliasesForOwnerRequest(
    val filter: EmailAliasFilterInputEntity? = null,
    val limit: Int? = null,
    val nextToken: String? = null,
)

/**
 * Output from listing email aliases.
 *
 * @property items The [List] of [EmailAliasEntity]s.
 * @property nextToken [String] Optional token for retrieving the next page of results.
 */
internal data class ListEmailAliasesOutput(
    val items: List<SealedEmailAliasEntity>,
    val nextToken: String? = null,
)

/**
 * Service interface for managing email aliases.
 *
 * Provides operations to provision, deprovision, update, enable, disable, retrieve, and list email aliases.
 */
internal interface EmailAliasService {
    /**
     * Provision an email alias.
     *
     * @param input [ProvisionEmailAliasRequest] Parameters used to provision an email alias.
     * @return [EmailAliasEntity] The provisioned email alias entity.
     */
    suspend fun provision(input: ProvisionEmailAliasRequest): SealedEmailAliasEntity

    /**
     * Deprovision an email alias.
     *
     * @param input [DeprovisionEmailAliasRequest] Parameters used to deprovision an email alias.
     * @return [EmailAliasEntity] The deprovisioned email alias entity.
     */
    suspend fun deprovision(input: DeprovisionEmailAliasRequest): SealedEmailAliasEntity

    /**
     * Update an email alias.
     *
     * @param input [UpdateEmailAliasRequest] Parameters used to update an email alias.
     * @return [EmailAliasEntity] The updated email alias entity.
     */
    suspend fun update(input: UpdateEmailAliasRequest): SealedEmailAliasEntity

    /**
     * Enable an email alias.
     *
     * @param input [EnableEmailAliasRequest] Parameters used to enable an email alias.
     * @return [EmailAliasEntity] The enabled email alias entity.
     */
    suspend fun enable(input: EnableEmailAliasRequest): SealedEmailAliasEntity

    /**
     * Disable an email alias.
     *
     * @param input [DisableEmailAliasRequest] Parameters used to disable an email alias.
     * @return [EmailAliasEntity] The disabled email alias entity.
     */
    suspend fun disable(input: DisableEmailAliasRequest): SealedEmailAliasEntity

    /**
     * List email aliases for an owner.
     *
     * @param input [ListEmailAliasesForOwnerRequest] Parameters used to list email aliases for an owner.
     * @return [ListEmailAliasesOutput] List API result containing email aliases and next token.
     */
    suspend fun listForOwner(input: ListEmailAliasesForOwnerRequest): ListEmailAliasesOutput

    /**
     * Verify an external email address for an email alias.
     *
     * @param input [VerifyExternalEmailAddressRequest] Parameters used to verify an external email address.
     * @return [VerifyExternalEmailAddressResultEntity] Result indicating verification status and optional reason.
     */
    suspend fun verifyExternalEmailAddress(input: VerifyExternalEmailAddressRequest): VerifyExternalEmailAddressResultEntity
}
