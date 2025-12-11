/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.internal.domain.useCases.emailFolder

import com.sudoplatform.sudoemail.internal.data.common.mechanisms.EmailFolderUnsealer
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.EmailFolderService
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.ListEmailFoldersForEmailAddressIdRequest
import com.sudoplatform.sudoemail.internal.domain.entities.emailFolder.UnsealedEmailFolderEntity
import com.sudoplatform.sudoemail.keys.ServiceKeyManager
import com.sudoplatform.sudoemail.types.ListOutput
import com.sudoplatform.sudologging.Logger

/**
 * Input for the list email folders for email address ID use case.
 *
 * @property emailAddressId [String] The email address ID to list folders for.
 * @property limit [Int] Optional maximum number of results to return.
 * @property nextToken [String] Optional token for pagination.
 */
internal data class ListEmailFoldersForEmailAddressIdUseCaseInput(
    val emailAddressId: String,
    val limit: Int?,
    val nextToken: String?,
)

/**
 * Use case for listing email folders for a specific email address.
 *
 * This use case retrieves and unseals (decrypts) email folders for a given email address.
 *
 * @property emailFolderService [EmailFolderService] Service for email folder operations.
 * @property serviceKeyManager [ServiceKeyManager] Manager for encryption keys.
 * @property logger [Logger] Logger for debugging.
 */
internal class ListEmailFoldersForEmailAddressIdUseCase(
    private val emailFolderService: EmailFolderService,
    private val serviceKeyManager: ServiceKeyManager,
    private val logger: Logger,
) {
    /**
     * Executes the list email folders for email address ID use case.
     *
     * @param input [ListEmailFoldersForEmailAddressIdUseCaseInput] The input parameters.
     * @return [ListOutput] containing unsealed email folders and pagination info.
     */
    suspend fun execute(input: ListEmailFoldersForEmailAddressIdUseCaseInput): ListOutput<UnsealedEmailFolderEntity> {
        logger.debug("ListEmailFoldersForEmailAddressIdUseCase execute input: $input")

        val (items, nextToken) =
            emailFolderService.listForEmailAddressId(
                input =
                    ListEmailFoldersForEmailAddressIdRequest(
                        emailAddressId = input.emailAddressId,
                        limit = input.limit,
                        nextToken = input.nextToken,
                    ),
            )

        val unsealedItems: MutableList<UnsealedEmailFolderEntity> = mutableListOf()
        val emailFolderUnsealer = EmailFolderUnsealer(this.serviceKeyManager)
        for (sealedEmailFolder in items) {
            try {
                val unsealed = emailFolderUnsealer.unseal(sealedEmailFolder)
                unsealedItems.add(unsealed)
            } catch (e: Exception) {
                logger.error("ListEmailFoldersForEmailAddressUseCase failed to unseal email folder ${sealedEmailFolder.id}: $e")
                throw e
            }
        }
        return ListOutput(
            items = unsealedItems,
            nextToken = nextToken,
        )
    }
}
