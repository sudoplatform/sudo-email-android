/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.entities.emailFolder

import com.sudoplatform.sudoemail.graphql.type.SealedAttributeInput

/**
 * Request to list email folders for a specific email address ID.
 *
 * @property emailAddressId [String] The email address ID to list folders for.
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 */
internal data class ListEmailFoldersForEmailAddressIdRequest(
    val emailAddressId: String,
    val limit: Int?,
    val nextToken: String?,
)

/**
 * Request to create a custom email folder.
 *
 * @property emailAddressId [String] The email address ID to create the folder for.
 * @property customFolderName [SealedAttributeInput] The sealed (encrypted) name for the custom folder.
 */
internal data class CreateCustomEmailFolderRequest(
    val emailAddressId: String,
    val customFolderName: SealedAttributeInput,
)

/**
 * Request to delete a custom email folder.
 *
 * @property emailFolderId [String] The ID of the email folder to delete.
 * @property emailAddressId [String] The email address ID associated with the folder.
 */
internal data class DeleteCustomEmailFolderRequest(
    val emailFolderId: String,
    val emailAddressId: String,
)

/**
 * Request to update a custom email folder.
 *
 * @property emailFolderId [String] The ID of the email folder to update.
 * @property emailAddressId [String] The email address ID associated with the folder.
 * @property customFolderName [SealedAttributeInput] The new sealed (encrypted) name for the custom folder, or null to keep unchanged.
 */
internal data class UpdateCustomEmailFolderRequest(
    val emailFolderId: String,
    val emailAddressId: String,
    val customFolderName: SealedAttributeInput?,
)

/**
 * Output from listing email folders.
 *
 * @property items The [List] of [SealedEmailFolderEntity]s.
 * @property nextToken [String] Optional token for retrieving the next page of results.
 */
internal data class ListEmailFoldersOutput(
    val items: List<SealedEmailFolderEntity>,
    val nextToken: String?,
)

/**
 * Service interface for managing email folders.
 *
 * Provides operations to list, create, delete, and update email folders.
 */
internal interface EmailFolderService {
    /**
     *  List all email folders for a given email address ID.
     *
     *  @param input [ListEmailFoldersForEmailAddressIdRequest] Parameters used to list email folders for an email address.
     *  @return [ListEmailFoldersOutput] List API result containing the sealed email folders and next token.
     */
    suspend fun listForEmailAddressId(input: ListEmailFoldersForEmailAddressIdRequest): ListEmailFoldersOutput

    /**
     * Create a custom email folder for a given email address ID.
     *
     * @param input [CreateCustomEmailFolderRequest] Parameters used to create a custom email folder.
     * @return [SealedEmailFolderEntity] The created sealed email folder.
     */
    suspend fun createCustom(input: CreateCustomEmailFolderRequest): SealedEmailFolderEntity

    /**
     * Delete a custom email folder for a given email address ID.
     *
     * @param input [DeleteCustomEmailFolderRequest] Parameters used to delete a custom email folder.
     * @return [SealedEmailFolderEntity] The deleted sealed email folder, or null if not found.
     */
    suspend fun deleteCustom(input: DeleteCustomEmailFolderRequest): SealedEmailFolderEntity?

    /**
     * Update a custom email folder for a given email address ID.
     *
     * @param input [UpdateCustomEmailFolderRequest] Parameters used to update a custom email folder.
     * @return [SealedEmailFolderEntity] The updated sealed email folder.
     */
    suspend fun updateCustom(input: UpdateCustomEmailFolderRequest): SealedEmailFolderEntity
}
