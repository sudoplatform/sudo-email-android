/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.subscription

import com.amplifyframework.api.ApiException
import com.amplifyframework.api.graphql.GraphQLResponse
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import com.sudoplatform.sudoemail.graphql.OnEmailMessageCreatedSubscription
import com.sudoplatform.sudoemail.graphql.OnEmailMessageDeletedSubscription
import com.sudoplatform.sudoemail.graphql.OnEmailMessageUpdatedSubscription
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudoemail.types.transformers.EmailMessageTransformer
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudouser.SudoUserClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Manage the subscriptions of email message modifications.
 */
internal class SubscriptionService(
    private val apiClient: ApiClient,
    private val deviceKeyManager: DeviceKeyManager,
    private val userClient: SudoUserClient,
    private val logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO)),
) : AutoCloseable {

    companion object {
        private const val ERROR_UNAUTHENTICATED_MSG = "User client does not have subject. Is the user authenticated?"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val createSubscriptionManager = EmailMessageSubscriptionManager<OnEmailMessageCreatedSubscription.Data>()
    private val deleteSubscriptionManager = EmailMessageSubscriptionManager<OnEmailMessageDeletedSubscription.Data>()
    private val updateSubscriptionManager = EmailMessageSubscriptionManager<OnEmailMessageUpdatedSubscription.Data>()

    suspend fun subscribeEmailMessages(id: String, subscriber: EmailMessageSubscriber) {
        val userSubject = userClient.getSubject()
            ?: throw SudoEmailClient.EmailMessageException.AuthenticationException(ERROR_UNAUTHENTICATED_MSG)

        createSubscriptionManager.replaceSubscriber(id, subscriber)
        deleteSubscriptionManager.replaceSubscriber(id, subscriber)
        updateSubscriptionManager.replaceSubscriber(id, subscriber)

        scope.launch {
            if (createSubscriptionManager.watcher == null) {
                val watcher =
                    apiClient.onEmailMessageCreatedSubscription(
                        userSubject,
                        createCallback.onSubscriptionEstablished,
                        createCallback.onSubscription,
                        createCallback.onSubscriptionCompleted,
                        createCallback.onFailure,
                    )
                createSubscriptionManager.watcher = watcher
            }

            if (deleteSubscriptionManager.watcher == null) {
                val watcher =
                    apiClient.onEmailMessageDeletedSubscription(
                        userSubject,
                        deleteCallback.onSubscriptionEstablished,
                        deleteCallback.onSubscription,
                        deleteCallback.onSubscriptionCompleted,
                        deleteCallback.onFailure,
                    )
                deleteSubscriptionManager.watcher = watcher
            }

            if (updateSubscriptionManager.watcher == null) {
                val watcher =
                    apiClient.onEmailMessageUpdatedSubscription(
                        userSubject,
                        updateCallback.onSubscriptionEstablished,
                        updateCallback.onSubscription,
                        updateCallback.onSubscriptionCompleted,
                        updateCallback.onFailure,
                    )
                updateSubscriptionManager.watcher = watcher
            }
        }.join()
    }

    fun unsubscribeEmailMessages(id: String) {
        createSubscriptionManager.removeSubscriber(id)
        deleteSubscriptionManager.removeSubscriber(id)
    }

    fun unsubscribeAllEmailMessages() {
        createSubscriptionManager.removeAllSubscribers()
        deleteSubscriptionManager.removeAllSubscribers()
    }

    override fun close() {
        unsubscribeAllEmailMessages()
        scope.cancel()
    }

    private val createCallback = object {
        val onSubscriptionEstablished: (GraphQLResponse<OnEmailMessageCreatedSubscription.Data>) -> Unit =
            {
                createSubscriptionManager.connectionStatusChanged(
                    Subscriber.ConnectionState.CONNECTED,
                )
            }
        val onSubscription: (GraphQLResponse<OnEmailMessageCreatedSubscription.Data>) -> Unit = {
            scope.launch {
                val createEmailMessage = it.data?.onEmailMessageCreated ?: return@launch
                createSubscriptionManager.emailMessageChanged(
                    EmailMessageTransformer.toEntity(
                        deviceKeyManager,
                        createEmailMessage.sealedEmailMessage,
                    ),
                    EmailMessageSubscriber.ChangeType.CREATED,
                )
            }
        }
        val onSubscriptionCompleted = {
            createSubscriptionManager.connectionStatusChanged(Subscriber.ConnectionState.DISCONNECTED)
        }
        val onFailure: (ApiException) -> Unit = {
            logger.error("Email message create subscription error $it")
        }
    }

    private val deleteCallback = object {
        val onSubscriptionEstablished: (GraphQLResponse<OnEmailMessageDeletedSubscription.Data>) -> Unit =
            {
                deleteSubscriptionManager.connectionStatusChanged(
                    Subscriber.ConnectionState.CONNECTED,
                )
            }
        val onSubscription: (GraphQLResponse<OnEmailMessageDeletedSubscription.Data>) -> Unit = {
            scope.launch {
                val deleteEmailMessage = it.data?.onEmailMessageDeleted ?: return@launch
                deleteSubscriptionManager.emailMessageChanged(
                    EmailMessageTransformer.toEntity(
                        deviceKeyManager,
                        deleteEmailMessage.sealedEmailMessage,
                    ),
                    EmailMessageSubscriber.ChangeType.DELETED,
                )
            }
        }
        val onSubscriptionCompleted = {
            deleteSubscriptionManager.connectionStatusChanged(Subscriber.ConnectionState.DISCONNECTED)
        }
        val onFailure: (ApiException) -> Unit = {
            logger.error("Email message delete subscription error $it")
        }
    }

    private val updateCallback = object {
        val onSubscriptionEstablished: (GraphQLResponse<OnEmailMessageUpdatedSubscription.Data>) -> Unit =
            {
                updateSubscriptionManager.connectionStatusChanged(
                    Subscriber.ConnectionState.CONNECTED,
                )
            }
        val onSubscription: (GraphQLResponse<OnEmailMessageUpdatedSubscription.Data>) -> Unit = {
            scope.launch {
                val updateEmailMessage = it.data?.onEmailMessageUpdated ?: return@launch
                updateSubscriptionManager.emailMessageChanged(
                    EmailMessageTransformer.toEntity(
                        deviceKeyManager,
                        updateEmailMessage.sealedEmailMessage,
                    ),
                    EmailMessageSubscriber.ChangeType.UPDATED,
                )
            }
        }
        val onSubscriptionCompleted = {
            updateSubscriptionManager.connectionStatusChanged(Subscriber.ConnectionState.DISCONNECTED)
        }
        val onFailure: (ApiException) -> Unit = {
            logger.error("Email message update subscription error $it")
        }
    }
}
