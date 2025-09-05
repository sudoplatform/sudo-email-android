/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.api

import com.amplifyframework.api.ApiException
import com.amplifyframework.api.graphql.GraphQLOperation
import com.amplifyframework.api.graphql.GraphQLResponse
import com.amplifyframework.core.Action
import com.amplifyframework.core.Consumer
import com.sudoplatform.sudoemail.graphql.BlockEmailAddressesMutation
import com.sudoplatform.sudoemail.graphql.CancelScheduledDraftMessageMutation
import com.sudoplatform.sudoemail.graphql.CheckEmailAddressAvailabilityQuery
import com.sudoplatform.sudoemail.graphql.CreateCustomEmailFolderMutation
import com.sudoplatform.sudoemail.graphql.DeleteCustomEmailFolderMutation
import com.sudoplatform.sudoemail.graphql.DeleteEmailMessagesMutation
import com.sudoplatform.sudoemail.graphql.DeleteMessagesByFolderIdMutation
import com.sudoplatform.sudoemail.graphql.DeprovisionEmailAddressMutation
import com.sudoplatform.sudoemail.graphql.GetConfiguredEmailDomainsQuery
import com.sudoplatform.sudoemail.graphql.GetEmailAddressBlocklistQuery
import com.sudoplatform.sudoemail.graphql.GetEmailAddressQuery
import com.sudoplatform.sudoemail.graphql.GetEmailConfigQuery
import com.sudoplatform.sudoemail.graphql.GetEmailDomainsQuery
import com.sudoplatform.sudoemail.graphql.GetEmailMessageQuery
import com.sudoplatform.sudoemail.graphql.ListEmailAddressesForSudoIdQuery
import com.sudoplatform.sudoemail.graphql.ListEmailAddressesQuery
import com.sudoplatform.sudoemail.graphql.ListEmailFoldersForEmailAddressIdQuery
import com.sudoplatform.sudoemail.graphql.ListEmailMessagesForEmailAddressIdQuery
import com.sudoplatform.sudoemail.graphql.ListEmailMessagesForEmailFolderIdQuery
import com.sudoplatform.sudoemail.graphql.ListEmailMessagesQuery
import com.sudoplatform.sudoemail.graphql.ListScheduledDraftMessagesForEmailAddressIdQuery
import com.sudoplatform.sudoemail.graphql.LookupEmailAddressesPublicInfoQuery
import com.sudoplatform.sudoemail.graphql.OnEmailMessageCreatedSubscription
import com.sudoplatform.sudoemail.graphql.OnEmailMessageDeletedSubscription
import com.sudoplatform.sudoemail.graphql.OnEmailMessageUpdatedSubscription
import com.sudoplatform.sudoemail.graphql.ProvisionEmailAddressMutation
import com.sudoplatform.sudoemail.graphql.ScheduleSendDraftMessageMutation
import com.sudoplatform.sudoemail.graphql.SendEmailMessageMutation
import com.sudoplatform.sudoemail.graphql.SendEncryptedEmailMessageMutation
import com.sudoplatform.sudoemail.graphql.UnblockEmailAddressesMutation
import com.sudoplatform.sudoemail.graphql.UpdateCustomEmailFolderMutation
import com.sudoplatform.sudoemail.graphql.UpdateEmailAddressMetadataMutation
import com.sudoplatform.sudoemail.graphql.UpdateEmailMessagesMutation
import com.sudoplatform.sudoemail.graphql.type.BlockEmailAddressesInput
import com.sudoplatform.sudoemail.graphql.type.CancelScheduledDraftMessageInput
import com.sudoplatform.sudoemail.graphql.type.CheckEmailAddressAvailabilityInput
import com.sudoplatform.sudoemail.graphql.type.CreateCustomEmailFolderInput
import com.sudoplatform.sudoemail.graphql.type.DeleteCustomEmailFolderInput
import com.sudoplatform.sudoemail.graphql.type.DeleteEmailMessagesInput
import com.sudoplatform.sudoemail.graphql.type.DeleteMessagesByFolderIdInput
import com.sudoplatform.sudoemail.graphql.type.DeprovisionEmailAddressInput
import com.sudoplatform.sudoemail.graphql.type.GetEmailAddressBlocklistInput
import com.sudoplatform.sudoemail.graphql.type.ListEmailAddressesForSudoIdInput
import com.sudoplatform.sudoemail.graphql.type.ListEmailAddressesInput
import com.sudoplatform.sudoemail.graphql.type.ListEmailFoldersForEmailAddressIdInput
import com.sudoplatform.sudoemail.graphql.type.ListEmailMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.graphql.type.ListEmailMessagesForEmailFolderIdInput
import com.sudoplatform.sudoemail.graphql.type.ListEmailMessagesInput
import com.sudoplatform.sudoemail.graphql.type.ListScheduledDraftMessagesForEmailAddressIdInput
import com.sudoplatform.sudoemail.graphql.type.LookupEmailAddressesPublicInfoInput
import com.sudoplatform.sudoemail.graphql.type.ProvisionEmailAddressInput
import com.sudoplatform.sudoemail.graphql.type.ScheduleSendDraftMessageInput
import com.sudoplatform.sudoemail.graphql.type.SendEmailMessageInput
import com.sudoplatform.sudoemail.graphql.type.SendEncryptedEmailMessageInput
import com.sudoplatform.sudoemail.graphql.type.UnblockEmailAddressesInput
import com.sudoplatform.sudoemail.graphql.type.UpdateCustomEmailFolderInput
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailAddressMetadataInput
import com.sudoplatform.sudoemail.graphql.type.UpdateEmailMessagesInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailAddressInput
import com.sudoplatform.sudoemail.types.inputs.GetEmailMessageInput
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.amplify.GraphQLClient

/**
 * Wrapper around the GraphQLClient and it's generated queries and mutations.
 * Provides a testable class to handle API requests.
 *
 * @property graphQLClient [GraphQLClient] The GraphQLClient that will be used to make the requests
 */
open class ApiClient(
    private val graphQLClient: GraphQLClient,
    private val logger: Logger,
) {
    // Queries
    open suspend fun getEmailConfigQuery(): GraphQLResponse<GetEmailConfigQuery.Data> =
        this.graphQLClient.query<GetEmailConfigQuery, GetEmailConfigQuery.Data>(
            GetEmailConfigQuery.OPERATION_DOCUMENT,
            emptyMap(),
        )

    open suspend fun getSupportedEmailDomainsQuery(): GraphQLResponse<GetEmailDomainsQuery.Data> =
        this.graphQLClient.query<GetEmailDomainsQuery, GetEmailDomainsQuery.Data>(
            GetEmailDomainsQuery.OPERATION_DOCUMENT,
            emptyMap(),
        )

    open suspend fun getConfiguredEmailDomainsQuery(): GraphQLResponse<GetConfiguredEmailDomainsQuery.Data> =
        this.graphQLClient.query<GetConfiguredEmailDomainsQuery, GetConfiguredEmailDomainsQuery.Data>(
            GetConfiguredEmailDomainsQuery.OPERATION_DOCUMENT,
            emptyMap(),
        )

    open suspend fun checkEmailAddressAvailabilityQuery(
        input: CheckEmailAddressAvailabilityInput,
    ): GraphQLResponse<CheckEmailAddressAvailabilityQuery.Data> =
        this.graphQLClient.query<CheckEmailAddressAvailabilityQuery, CheckEmailAddressAvailabilityQuery.Data>(
            CheckEmailAddressAvailabilityQuery.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun getEmailAddressQuery(input: GetEmailAddressInput): GraphQLResponse<GetEmailAddressQuery.Data> =
        this.graphQLClient.query<GetEmailAddressQuery, GetEmailAddressQuery.Data>(
            GetEmailAddressQuery.OPERATION_DOCUMENT,
            mapOf("id" to input.id),
        )

    open suspend fun listEmailAddressesQuery(input: ListEmailAddressesInput): GraphQLResponse<ListEmailAddressesQuery.Data> =
        this.graphQLClient.query<ListEmailAddressesQuery, ListEmailAddressesQuery.Data>(
            ListEmailAddressesQuery.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun listEmailAddressesForSudoIdQuery(
        input: ListEmailAddressesForSudoIdInput,
    ): GraphQLResponse<ListEmailAddressesForSudoIdQuery.Data> =
        graphQLClient.query<ListEmailAddressesForSudoIdQuery, ListEmailAddressesForSudoIdQuery.Data>(
            ListEmailAddressesForSudoIdQuery.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun lookupEmailAddressesPublicInfoQuery(
        input: LookupEmailAddressesPublicInfoInput,
    ): GraphQLResponse<LookupEmailAddressesPublicInfoQuery.Data> =
        graphQLClient.query<LookupEmailAddressesPublicInfoQuery, LookupEmailAddressesPublicInfoQuery.Data>(
            LookupEmailAddressesPublicInfoQuery.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun listEmailFoldersForEmailAddressIdQuery(
        input: ListEmailFoldersForEmailAddressIdInput,
    ): GraphQLResponse<ListEmailFoldersForEmailAddressIdQuery.Data> =
        graphQLClient.query<ListEmailFoldersForEmailAddressIdQuery, ListEmailFoldersForEmailAddressIdQuery.Data>(
            ListEmailFoldersForEmailAddressIdQuery.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun getEmailMessageQuery(input: GetEmailMessageInput): GraphQLResponse<GetEmailMessageQuery.Data> =
        graphQLClient.query<GetEmailMessageQuery, GetEmailMessageQuery.Data>(
            GetEmailMessageQuery.OPERATION_DOCUMENT,
            mapOf("id" to input.id),
        )

    open suspend fun listEmailMessagesQuery(input: ListEmailMessagesInput): GraphQLResponse<ListEmailMessagesQuery.Data> =
        graphQLClient.query<ListEmailMessagesQuery, ListEmailMessagesQuery.Data>(
            ListEmailMessagesQuery.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun listEmailMessagesForEmailAddressIdQuery(
        input: ListEmailMessagesForEmailAddressIdInput,
    ): GraphQLResponse<ListEmailMessagesForEmailAddressIdQuery.Data> =
        graphQLClient.query<ListEmailMessagesForEmailAddressIdQuery, ListEmailMessagesForEmailAddressIdQuery.Data>(
            ListEmailMessagesForEmailAddressIdQuery.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun listEmailMessagesForEmailFolderIdQuery(
        input: ListEmailMessagesForEmailFolderIdInput,
    ): GraphQLResponse<ListEmailMessagesForEmailFolderIdQuery.Data> =
        graphQLClient.query<ListEmailMessagesForEmailFolderIdQuery, ListEmailMessagesForEmailFolderIdQuery.Data>(
            ListEmailMessagesForEmailFolderIdQuery.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun listScheduledDraftMessagesForEmailAddressIdQuery(
        input: ListScheduledDraftMessagesForEmailAddressIdInput,
    ): GraphQLResponse<ListScheduledDraftMessagesForEmailAddressIdQuery.Data> =
        graphQLClient.query<
            ListScheduledDraftMessagesForEmailAddressIdQuery,
            ListScheduledDraftMessagesForEmailAddressIdQuery.Data,
        >(
            ListScheduledDraftMessagesForEmailAddressIdQuery.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun getEmailAddressBlocklistQuery(
        input: GetEmailAddressBlocklistInput,
    ): GraphQLResponse<GetEmailAddressBlocklistQuery.Data> =
        graphQLClient.query<GetEmailAddressBlocklistQuery, GetEmailAddressBlocklistQuery.Data>(
            GetEmailAddressBlocklistQuery.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    // Mutations
    open suspend fun provisionEmailAddressMutation(
        input: ProvisionEmailAddressInput,
    ): GraphQLResponse<ProvisionEmailAddressMutation.Data> =
        this.graphQLClient.mutate<ProvisionEmailAddressMutation, ProvisionEmailAddressMutation.Data>(
            ProvisionEmailAddressMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun deprovisionEmailAddressMutation(
        input: DeprovisionEmailAddressInput,
    ): GraphQLResponse<DeprovisionEmailAddressMutation.Data> =
        this.graphQLClient.mutate<DeprovisionEmailAddressMutation, DeprovisionEmailAddressMutation.Data>(
            DeprovisionEmailAddressMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun updateEmailAddressMetadataMutation(
        input: UpdateEmailAddressMetadataInput,
    ): GraphQLResponse<UpdateEmailAddressMetadataMutation.Data> =
        this.graphQLClient.mutate<UpdateEmailAddressMetadataMutation, UpdateEmailAddressMetadataMutation.Data>(
            UpdateEmailAddressMetadataMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun createCustomEmailFolderMutation(
        input: CreateCustomEmailFolderInput,
    ): GraphQLResponse<CreateCustomEmailFolderMutation.Data> =
        graphQLClient.mutate<CreateCustomEmailFolderMutation, CreateCustomEmailFolderMutation.Data>(
            CreateCustomEmailFolderMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun deleteCustomEmailFolderMutation(
        input: DeleteCustomEmailFolderInput,
    ): GraphQLResponse<DeleteCustomEmailFolderMutation.Data> =
        graphQLClient.mutate<DeleteCustomEmailFolderMutation, DeleteCustomEmailFolderMutation.Data>(
            DeleteCustomEmailFolderMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun updateCustomEmailFolderMutation(
        input: UpdateCustomEmailFolderInput,
    ): GraphQLResponse<UpdateCustomEmailFolderMutation.Data> =
        graphQLClient.mutate<UpdateCustomEmailFolderMutation, UpdateCustomEmailFolderMutation.Data>(
            UpdateCustomEmailFolderMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun sendEmailMessageMutation(input: SendEmailMessageInput): GraphQLResponse<SendEmailMessageMutation.Data> =
        graphQLClient.mutate<SendEmailMessageMutation, SendEmailMessageMutation.Data>(
            SendEmailMessageMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun sendEncryptedEmailMessageMutation(
        input: SendEncryptedEmailMessageInput,
    ): GraphQLResponse<SendEncryptedEmailMessageMutation.Data> =
        graphQLClient.mutate<SendEncryptedEmailMessageMutation, SendEncryptedEmailMessageMutation.Data>(
            SendEncryptedEmailMessageMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun updateEmailMessagesMutation(input: UpdateEmailMessagesInput): GraphQLResponse<UpdateEmailMessagesMutation.Data> =
        graphQLClient.mutate<UpdateEmailMessagesMutation, UpdateEmailMessagesMutation.Data>(
            UpdateEmailMessagesMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun deleteEmailMessagesMutation(input: DeleteEmailMessagesInput): GraphQLResponse<DeleteEmailMessagesMutation.Data> =
        graphQLClient.mutate<DeleteEmailMessagesMutation, DeleteEmailMessagesMutation.Data>(
            DeleteEmailMessagesMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun scheduleSendDraftMessageMutation(
        input: ScheduleSendDraftMessageInput,
    ): GraphQLResponse<ScheduleSendDraftMessageMutation.Data> =
        graphQLClient.mutate<ScheduleSendDraftMessageMutation, ScheduleSendDraftMessageMutation.Data>(
            ScheduleSendDraftMessageMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun cancelScheduledDraftMessageMutation(
        input: CancelScheduledDraftMessageInput,
    ): GraphQLResponse<CancelScheduledDraftMessageMutation.Data> =
        graphQLClient.mutate<CancelScheduledDraftMessageMutation, CancelScheduledDraftMessageMutation.Data>(
            CancelScheduledDraftMessageMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun blockEmailAddressesMutation(input: BlockEmailAddressesInput): GraphQLResponse<BlockEmailAddressesMutation.Data> =
        graphQLClient.mutate<BlockEmailAddressesMutation, BlockEmailAddressesMutation.Data>(
            BlockEmailAddressesMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun unblockEmailAddressesMutation(
        input: UnblockEmailAddressesInput,
    ): GraphQLResponse<UnblockEmailAddressesMutation.Data> =
        graphQLClient.mutate<UnblockEmailAddressesMutation, UnblockEmailAddressesMutation.Data>(
            UnblockEmailAddressesMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    open suspend fun deleteEmailMessagesByFolderIdMutation(
        input: DeleteMessagesByFolderIdInput,
    ): GraphQLResponse<DeleteMessagesByFolderIdMutation.Data> =
        graphQLClient.mutate<
            DeleteMessagesByFolderIdMutation,
            DeleteMessagesByFolderIdMutation.Data,
        >(
            DeleteMessagesByFolderIdMutation.OPERATION_DOCUMENT,
            mapOf("input" to input),
        )

    // Subscriptions
    open suspend fun onEmailMessageCreatedSubscription(
        owner: String,
        onSubscriptionEstablished: Consumer<GraphQLResponse<OnEmailMessageCreatedSubscription.Data>>,
        onSubscription: Consumer<GraphQLResponse<OnEmailMessageCreatedSubscription.Data>>,
        onSubscriptionCompleted: Action,
        onFailure: Consumer<ApiException>,
    ): GraphQLOperation<OnEmailMessageCreatedSubscription.Data>? =
        graphQLClient.subscribe<OnEmailMessageCreatedSubscription, OnEmailMessageCreatedSubscription.Data>(
            OnEmailMessageCreatedSubscription.OPERATION_DOCUMENT,
            mapOf("owner" to owner),
            onSubscriptionEstablished,
            onSubscription,
            onSubscriptionCompleted,
            onFailure,
        )

    open suspend fun onEmailMessageDeletedSubscription(
        owner: String,
        onSubscriptionEstablished: Consumer<GraphQLResponse<OnEmailMessageDeletedSubscription.Data>>,
        onSubscription: Consumer<GraphQLResponse<OnEmailMessageDeletedSubscription.Data>>,
        onSubscriptionCompleted: Action,
        onFailure: Consumer<ApiException>,
    ): GraphQLOperation<OnEmailMessageDeletedSubscription.Data>? =
        graphQLClient.subscribe<OnEmailMessageDeletedSubscription, OnEmailMessageDeletedSubscription.Data>(
            OnEmailMessageDeletedSubscription.OPERATION_DOCUMENT,
            mapOf("owner" to owner),
            onSubscriptionEstablished,
            onSubscription,
            onSubscriptionCompleted,
            onFailure,
        )

    open suspend fun onEmailMessageUpdatedSubscription(
        owner: String,
        onSubscriptionEstablished: Consumer<GraphQLResponse<OnEmailMessageUpdatedSubscription.Data>>,
        onSubscription: Consumer<GraphQLResponse<OnEmailMessageUpdatedSubscription.Data>>,
        onSubscriptionCompleted: Action,
        onFailure: Consumer<ApiException>,
    ): GraphQLOperation<OnEmailMessageUpdatedSubscription.Data>? =
        graphQLClient.subscribe<OnEmailMessageUpdatedSubscription, OnEmailMessageUpdatedSubscription.Data>(
            OnEmailMessageUpdatedSubscription.OPERATION_DOCUMENT,
            mapOf("owner" to owner),
            onSubscriptionEstablished,
            onSubscription,
            onSubscriptionCompleted,
            onFailure,
        )
}
