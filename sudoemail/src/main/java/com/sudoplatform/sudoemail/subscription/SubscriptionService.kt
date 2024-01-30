/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.subscription

import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.amazonaws.mobileconnectors.appsync.AppSyncSubscriptionCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.graphql.OnEmailMessageCreatedSubscription
import com.sudoplatform.sudoemail.graphql.OnEmailMessageDeletedSubscription
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
    private val appSyncClient: AWSAppSyncClient,
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

    suspend fun subscribeEmailMessages(id: String, subscriber: EmailMessageSubscriber) {
        val userSubject = userClient.getSubject()
            ?: throw SudoEmailClient.EmailMessageException.AuthenticationException(ERROR_UNAUTHENTICATED_MSG)

        createSubscriptionManager.replaceSubscriber(id, subscriber)
        deleteSubscriptionManager.replaceSubscriber(id, subscriber)

        scope.launch {
            if (createSubscriptionManager.watcher == null && createSubscriptionManager.pendingWatcher == null) {
                val watcher = appSyncClient.subscribe(
                    OnEmailMessageCreatedSubscription.builder()
                        .owner(userSubject)
                        .build(),
                )
                createSubscriptionManager.pendingWatcher = watcher
                watcher.execute(createCallback)
            }

            if (deleteSubscriptionManager.watcher == null && deleteSubscriptionManager.pendingWatcher == null) {
                val watcher = appSyncClient.subscribe(
                    OnEmailMessageDeletedSubscription.builder()
                        .owner(userSubject)
                        .build(),
                )
                deleteSubscriptionManager.pendingWatcher = watcher
                watcher.execute(deleteCallback)
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

    private val createCallback = object : AppSyncSubscriptionCall.StartedCallback<OnEmailMessageCreatedSubscription.Data> {
        override fun onFailure(e: ApolloException) {
            logger.error("EmailMessage created subscription error $e")
            createSubscriptionManager.connectionStatusChanged(Subscriber.ConnectionState.DISCONNECTED)
        }

        override fun onResponse(response: Response<OnEmailMessageCreatedSubscription.Data>) {
            scope.launch {
                val newEmailMessage = response.data()?.onEmailMessageCreated()
                    ?: return@launch
                createSubscriptionManager.emailMessageChanged(
                    EmailMessageTransformer.toEntity(deviceKeyManager, newEmailMessage.fragments().sealedEmailMessage()),
                )
            }
        }

        override fun onCompleted() {
            createSubscriptionManager.connectionStatusChanged(Subscriber.ConnectionState.DISCONNECTED)
        }

        override fun onStarted() {
            createSubscriptionManager
                .watcher = createSubscriptionManager.pendingWatcher
            createSubscriptionManager.connectionStatusChanged(
                Subscriber.ConnectionState.CONNECTED,
            )
        }
    }

    private val deleteCallback = object : AppSyncSubscriptionCall.StartedCallback<OnEmailMessageDeletedSubscription.Data> {
        override fun onFailure(e: ApolloException) {
            logger.error("EmailMessage delete subscription error $e")
            deleteSubscriptionManager.connectionStatusChanged(Subscriber.ConnectionState.DISCONNECTED)
        }

        override fun onResponse(response: Response<OnEmailMessageDeletedSubscription.Data>) {
            scope.launch {
                val deletedEmailMessage = response.data()?.onEmailMessageDeleted()
                    ?: return@launch
                deleteSubscriptionManager.emailMessageChanged(
                    EmailMessageTransformer.toEntity(deviceKeyManager, deletedEmailMessage.fragments().sealedEmailMessage()),
                )
            }
        }

        override fun onCompleted() {
            deleteSubscriptionManager.connectionStatusChanged(Subscriber.ConnectionState.DISCONNECTED)
        }

        override fun onStarted() {
            deleteSubscriptionManager
                .watcher = deleteSubscriptionManager.pendingWatcher
            deleteSubscriptionManager.connectionStatusChanged(
                Subscriber.ConnectionState.CONNECTED,
            )
        }
    }
}
