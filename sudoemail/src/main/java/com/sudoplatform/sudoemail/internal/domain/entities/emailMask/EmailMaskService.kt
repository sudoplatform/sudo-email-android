package com.sudoplatform.sudoemail.internal.domain.entities.emailMask

import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput
import com.sudoplatform.sudoemail.keys.KeyPair
import java.util.Date

/**
 * Request to provision an email mask.
 *
 * @property maskAddress [String] The mask email address to provision.
 * @property realAddress [String] The real email address that the mask forwards to.
 * @property metadata [SealedAttributeInput] Optional sealed metadata for the email mask.
 * @property expiresAt [Date] Optional expiration date for the email mask.
 * @property ownershipProofToken [String] The token proving ownership of the Sudo.
 */
internal data class ProvisionEmailMaskRequest(
    val maskAddress: String,
    val realAddress: String,
    val metadata: SealedAttributeInput? = null,
    val expiresAt: Date? = null,
    val ownershipProofToken: String,
    val keyPair: KeyPair,
)

/**
 * Request to deprovision an email mask.
 *
 * @property emailMaskId [String] The ID of the email mask to deprovision.
 */
internal data class DeprovisionEmailMaskRequest(
    val emailMaskId: String,
)

/**
 * Request to update an email mask.
 *
 * @property id [String] The ID of the email mask to update.
 * @property metadata [SealedAttributeInput] Optional sealed metadata for the email mask.
 * @property expiresAt [Date] Optional expiration date for the email mask.
 */
internal data class UpdateEmailMaskRequest(
    val id: String,
    val metadata: SealedAttributeInput? = null,
    val clearMetadata: Boolean = false,
    val expiresAt: Date? = null,
    val clearExpiresAt: Boolean = false,
)

/**
 * Request to enable an email mask.
 *
 * @property emailMaskId [String] The ID of the email mask to enable.
 */
internal data class EnableEmailMaskRequest(
    val emailMaskId: String,
)

/**
 * Request to disable an email mask.
 *
 * @property emailMaskId [String] The ID of the email mask to disable.
 */
internal data class DisableEmailMaskRequest(
    val emailMaskId: String,
)

/**
 * Filter criteria for email mask status.
 *
 * @property ne [EmailMaskEntityStatus] Status not equal to this value.
 * @property eq [EmailMaskEntityStatus] Status equal to this value.
 * @property `in` [List<EmailMaskEntityStatus>] Status in this list of values.
 * @property notIn [List<EmailMaskEntityStatus>] Status not in this list of values.
 */
internal data class EmailMaskStatusFilter(
    val ne: EmailMaskEntityStatus? = null,
    val eq: EmailMaskEntityStatus? = null,
    val `in`: List<EmailMaskEntityStatus>? = null,
    val notIn: List<EmailMaskEntityStatus>? = null,
)

/**
 * Filter criteria for email mask real address type.
 *
 * @property ne [EmailMaskEntityRealAddressType] Real address type not equal to this value.
 * @property eq [EmailMaskEntityRealAddressType] Real address type equal to this value.
 * @property `in` [List<EmailMaskEntityRealAddressType>] Real address type in this list of values.
 * @property notIn [List<EmailMaskEntityRealAddressType>] Real address type not in this list of values.
 */
internal data class EmailMaskRealAddressTypeFilter(
    val ne: EmailMaskEntityRealAddressType? = null,
    val eq: EmailMaskEntityRealAddressType? = null,
    val `in`: List<EmailMaskEntityRealAddressType>? = null,
    val notIn: List<EmailMaskEntityRealAddressType>? = null,
)

/**
 * Filter criteria for email masks.
 *
 * @property status [EmailMaskStatusFilter] Optional status filter.
 * @property realAddressType [EmailMaskRealAddressTypeFilter] Optional real address type filter.
 * @property and [List<EmailMaskFilter>] List of filters that must all match (AND logic).
 * @property or [List<EmailMaskFilter>] List of filters where at least one must match (OR logic).
 * @property not [EmailMaskFilter] Filter that must not match (NOT logic).
 */
internal data class EmailMaskFilter(
    val status: EmailMaskStatusFilter? = null,
    val realAddressType: EmailMaskRealAddressTypeFilter? = null,
    val and: List<EmailMaskFilter>? = null,
    val or: List<EmailMaskFilter>? = null,
    val not: EmailMaskFilter? = null,
)

/**
 * Request to list email masks for an owner.
 *
 * @property filter [EmailMaskFilter] Optional filter criteria for the email masks.
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 */
internal data class ListEmailMasksForOwnerRequest(
    val filter: EmailMaskFilterInputEntity? = null,
    val limit: Int? = null,
    val nextToken: String? = null,
)

/**
 * Output from listing email masks.
 *
 * @property items The [List] of [EmailMaskEntity]s.
 * @property nextToken [String] Optional token for retrieving the next page of results.
 */
internal data class ListEmailMasksOutput(
    val items: List<SealedEmailMaskEntity>,
    val nextToken: String? = null,
)

/**
 * Service interface for managing email masks.
 *
 * Provides operations to provision, deprovision, update, enable, disable, retrieve, and list email masks.
 */
internal interface EmailMaskService {
    /**
     * Provision an email mask.
     *
     * @param input [ProvisionEmailMaskRequest] Parameters used to provision an email mask.
     * @return [EmailMaskEntity] The provisioned email mask entity.
     */
    suspend fun provision(input: ProvisionEmailMaskRequest): SealedEmailMaskEntity

    /**
     * Deprovision an email mask.
     *
     * @param input [DeprovisionEmailMaskRequest] Parameters used to deprovision an email mask.
     * @return [EmailMaskEntity] The deprovisioned email mask entity.
     */
    suspend fun deprovision(input: DeprovisionEmailMaskRequest): SealedEmailMaskEntity

    /**
     * Update an email mask.
     *
     * @param input [UpdateEmailMaskRequest] Parameters used to update an email mask.
     * @return [EmailMaskEntity] The updated email mask entity.
     */
    suspend fun update(input: UpdateEmailMaskRequest): SealedEmailMaskEntity

    /**
     * Enable an email mask.
     *
     * @param input [EnableEmailMaskRequest] Parameters used to enable an email mask.
     * @return [EmailMaskEntity] The enabled email mask entity.
     */
    suspend fun enable(input: EnableEmailMaskRequest): SealedEmailMaskEntity

    /**
     * Disable an email mask.
     *
     * @param input [DisableEmailMaskRequest] Parameters used to disable an email mask.
     * @return [EmailMaskEntity] The disabled email mask entity.
     */
    suspend fun disable(input: DisableEmailMaskRequest): SealedEmailMaskEntity

    /**
     * List email masks for an owner.
     *
     * @param input [ListEmailMasksForOwnerRequest] Parameters used to list email masks for an owner.
     * @return [ListEmailMasksOutput] List API result containing email masks and next token.
     */
    suspend fun listForOwner(input: ListEmailMasksForOwnerRequest): ListEmailMasksOutput
}
