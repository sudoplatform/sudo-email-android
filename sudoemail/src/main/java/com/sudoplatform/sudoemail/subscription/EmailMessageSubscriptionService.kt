/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
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
 * Manage the subscriptions of email message updates.
 *
 * @since 2020-08-19
 */
internal class EmailMessageSubscriptionService(
    private val appSyncClient: AWSAppSyncClient,
    private val deviceKeyManager: DeviceKeyManager,
    private val userClient: SudoUserClient,
    private val logger: Logger = Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))
) : AutoCloseable {

    companion object {
        private const val ERROR_UNAUTHENTICATED_MSG = "User client does not have subject. Is the user authenticated?"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val createSubscriptionManager = SubscriptionManager<OnEmailMessageCreatedSubscription.Data>()
    private val deleteSubscriptionManager = SubscriptionManager<OnEmailMessageDeletedSubscription.Data>()

    suspend fun subscribe(id: String, subscriber: EmailMessageSubscriber) {

        val userSubject = userClient.getSubject()
            ?: throw SudoEmailClient.EmailMessageException.AuthenticationException(ERROR_UNAUTHENTICATED_MSG)

        createSubscriptionManager.replaceSubscriber(id, subscriber)
        deleteSubscriptionManager.replaceSubscriber(id, subscriber)

        scope.launch {
            if (createSubscriptionManager.watcher == null) {
                val watcher = appSyncClient.subscribe(
                    OnEmailMessageCreatedSubscription.builder()
                        .userId(userSubject)
                        .build()
                )
                createSubscriptionManager.watcher = watcher
                watcher.execute(createCallback)
            }

            if (deleteSubscriptionManager.watcher == null) {
                val watcher = appSyncClient.subscribe(
                    OnEmailMessageDeletedSubscription.builder()
                        .userId(userSubject)
                        .build()
                )
                deleteSubscriptionManager.watcher = watcher
                watcher.execute(deleteCallback)
            }
        }.join()
    }

    fun unsubscribe(id: String) {
        createSubscriptionManager.removeSubscriber(id)
        deleteSubscriptionManager.removeSubscriber(id)
    }

    fun unsubscribeAll() {
        createSubscriptionManager.removeAllSubscribers()
        deleteSubscriptionManager.removeAllSubscribers()
    }

    override fun close() {
        unsubscribeAll()
        scope.cancel()
    }

    private val createCallback = object : AppSyncSubscriptionCall.Callback<OnEmailMessageCreatedSubscription.Data> {
        override fun onFailure(e: ApolloException) {
            logger.error("EmailMessage created subscription error $e")
            createSubscriptionManager.connectionStatusChanged(EmailMessageSubscriber.ConnectionState.DISCONNECTED)
        }

        override fun onResponse(response: Response<OnEmailMessageCreatedSubscription.Data>) {
            scope.launch {
                val newEmailMessage = response.data()?.onEmailMessageCreated()
                    ?: return@launch
                createSubscriptionManager.emailMessageCreated(
                    EmailMessageTransformer.toEntityFromCreateSubscription(deviceKeyManager, newEmailMessage)
                )
            }
        }

        override fun onCompleted() {
            createSubscriptionManager.connectionStatusChanged(EmailMessageSubscriber.ConnectionState.DISCONNECTED)
        }
    }

    private val deleteCallback = object : AppSyncSubscriptionCall.Callback<OnEmailMessageDeletedSubscription.Data> {
        override fun onFailure(e: ApolloException) {
            logger.error("EmailMessage delete subscription error $e")
            deleteSubscriptionManager.connectionStatusChanged(EmailMessageSubscriber.ConnectionState.DISCONNECTED)
        }

        override fun onResponse(response: Response<OnEmailMessageDeletedSubscription.Data>) {
            scope.launch {
                val deletedEmailMessage = response.data()?.onEmailMessageDeleted()
                    ?: return@launch
                deleteSubscriptionManager.emailMessageDeleted(
                    EmailMessageTransformer.toEntityFromDeleteSubscription(deviceKeyManager, deletedEmailMessage)
                )
            }
        }

        override fun onCompleted() {
            createSubscriptionManager.connectionStatusChanged(EmailMessageSubscriber.ConnectionState.DISCONNECTED)
        }
    }
}
