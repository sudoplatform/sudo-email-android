/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.google.firebase.messaging.RemoteMessage
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudoemail.notifications.MessageReceivedNotification
import com.sudoplatform.sudoemail.notifications.SealedNotification
import com.sudoplatform.sudoemail.types.transformers.EmailMessageReceivedNotificationTransformer
import com.sudoplatform.sudoemail.types.transformers.NotificationUnsealer
import com.sudoplatform.sudoemail.util.Constants
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import com.sudoplatform.sudologging.Logger
import com.sudoplatform.sudonotification.SudoNotificationClient
import com.sudoplatform.sudonotification.types.NotificationMetaData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Default implementation of the [SudoEmailNotifiableClient] interface.
 *
 * @property deviceKeyManager [DeviceKeyManager] On device management of key storage.
 * @property notificationHandler [SudoEmailNotificationHandler] ...
 * @property logger [Logger] Errors and warnings will be logged here.
 */
internal class DefaultSudoEmailNotifiableClient(
    private val deviceKeyManager: DeviceKeyManager,
    private val notificationHandler: SudoEmailNotificationHandler,
    private val logger: Logger =
        Logger(
            LogConstants.SUDOLOG_TAG,
            AndroidUtilsLogDriver(LogLevel.INFO),
        ),
) : SudoEmailNotifiableClient {
    /**
     * Email Service service name key in sudoplatformconfig.json and notifications.
     */
    override val serviceName = Constants.SERVICE_NAME

    /**
     * Return Email Service notification filter schema to [SudoNotificationClient].
     */
    override fun getSchema(): NotificationMetaData =
        SudoEmailNotificationMetaData(
            serviceName = Constants.SERVICE_NAME,
            schema =
                listOf(
                    SudoEmailNotificationSchemaEntry(
                        description = "Type of notification message",
                        fieldName = "meta.type",
                        type = "string",
                    ),
                    SudoEmailNotificationSchemaEntry(
                        description = "ID of email address to match",
                        fieldName = "meta.emailAddressId",
                        type = "string",
                    ),
                    SudoEmailNotificationSchemaEntry(
                        description = "ID of Sudo owning the email address",
                        fieldName = "meta.sudoId",
                        type = "string",
                    ),
                    SudoEmailNotificationSchemaEntry(
                        description = "ID of folder into which message was received",
                        fieldName = "meta.folderId",
                        type = "string",
                    ),
                    SudoEmailNotificationSchemaEntry(
                        description = "ID of key pair used to seal notification content",
                        fieldName = "meta.keyId",
                        type = "string",
                    ),
                ),
        )

    /**
     * Process [RemoteMessage].
     *
     * Unseals the sealed payload, determines the notification type and delegates further
     * to the application's handler.
     *
     * @param message [RemoteMessage] The remote message to process.
     */
    override fun processPayload(message: RemoteMessage) {
        logger.debug { "Received notification: ${message.data}" }

        val json = Json { ignoreUnknownKeys = true }

        @Serializable
        data class Sudoplatform(
            val servicename: String,
            val data: String,
        )

        val sudoplatform =
            try {
                json.decodeFromString<Sudoplatform>(message.data["sudoplatform"]!!)
            } catch (e: Exception) {
                logger.error { "Unable to decode Sudoplatform notification envelope ${e.message}" }
                return
            }

        // SudoNotificationClient will already have verified that this notification
        // matches our service name but best check.
        if (sudoplatform.servicename != Constants.SERVICE_NAME) {
            logger.error {
                "Unexpectedly handling notification for service " +
                    "${sudoplatform.servicename} when expecting only ${Constants.SERVICE_NAME}"
            }
            return
        }

        val sealedNotification =
            try {
                json.decodeFromString<SealedNotification>(sudoplatform.data)
            } catch (e: Exception) {
                logger.error { "Unable to decode SealedNotification ${e.message}" }
                return
            }

        val notification =
            try {
                NotificationUnsealer.toNotification(this.deviceKeyManager, sealedNotification)
            } catch (e: Exception) {
                logger.error {
                    "Unable to unseal SealedNotification with " +
                        "${sealedNotification.algorithm} key ${sealedNotification.keyId}" +
                        ": ${e.message}"
                }
                return
            }

        // Delegate handling to the registered application handler.
        //
        // Do not catch Exceptions thrown by the application handler.
        // Allow the app to fail so an application errors  can be
        // more easily identified and fixed.
        when (notification) {
            is MessageReceivedNotification -> {
                this.notificationHandler.onEmailMessageReceived(
                    EmailMessageReceivedNotificationTransformer.toEntity(notification),
                )
            }
        }
    }
}
