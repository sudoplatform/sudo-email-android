/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.google.firebase.messaging.RemoteMessage
import com.sudoplatform.sudoemail.keys.DeviceKeyManager
import com.sudoplatform.sudoemail.notifications.MessageReceivedNotification
import com.sudoplatform.sudoemail.types.EmailMessage
import com.sudoplatform.sudoemail.types.EmailMessageReceivedNotification
import com.sudoplatform.sudoemail.types.EncryptionStatus
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import io.kotlintest.matchers.collections.shouldContainAll
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.shouldBe
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import java.util.Base64
import java.util.Date

/**
 * Test the correct operation of the [SudoEmailNotifiableClient] using mocks
 * and spies.
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailNotifiableClientTest : BaseTests() {
    private val mockDeviceKeyManager by before {
        mock<DeviceKeyManager>()
    }

    private val mockRemoteMessage by before {
        mock<RemoteMessage>()
    }

    private val testNotificationHandler =
        object : SudoEmailNotificationHandler {
            val messageReceivedNotifications = mutableListOf<EmailMessageReceivedNotification>()

            override fun onEmailMessageReceived(message: EmailMessageReceivedNotification) {
                messageReceivedNotifications.add(message)
            }
        }

    private val client by before {
        DefaultSudoEmailNotifiableClient(
            mockDeviceKeyManager,
            testNotificationHandler,
            mockLogger,
        )
    }

    @Test
    fun `serviceName should be emService`() {
        client.serviceName shouldBe "emService"
    }

    @Test
    fun `getSchema should return schema`() {
        val schema = client.getSchema()

        schema.serviceName shouldBe "emService"

        schema.schema shouldHaveSize 5

        schema.schema.forEach {
            it.type shouldBe "string"
            it.fieldName shouldStartWith "meta."
        }

        schema.schema.map { it.fieldName } shouldContainAll
            listOf(
                "meta.type",
                "meta.emailAddressId",
                "meta.folderId",
                "meta.sudoId",
                "meta.keyId",
            )
    }

    @Test
    fun `processPayload does nothing for badly formatted payloads`() {
        val payloads =
            listOf(
                mapOf(),
                mapOf(Pair("sudoplatform", "this is not JSON")),
                mapOf(Pair("sudoplatform", "{}")),
                mapOf(Pair("sudoplatform", "{\"servicename\":\"sudoService\",\"data\":\"\"}")),
                mapOf(Pair("sudoplatform", "{\"servicename\":\"emService\",\"data\":\"this is not json\"}")),
                mapOf(Pair("sudoplatform", "{\"servicename\":\"emService\",\"data\":\"{\\\"wrong\\\":\\\"property\\\"}\"}")),
                mapOf(
                    Pair(
                        "sudoplatform",
                        "{\"servicename\":\"emService\",\"data\":" +
                            "\"{\\\"keyId\\\":\\\"key-id\\\"," +
                            "\\\"algorithm\\\":\\\"algorithm\\\"," +
                            "\\\"sealed\\\":\\\"invalid-sealed-data\\\"}\"}",
                    ),
                ),
            )

        payloads.forEach { payload ->
            reset(mockRemoteMessage)
            mockRemoteMessage.stub { on { data } doReturn payload }

            client.processPayload(mockRemoteMessage)

            testNotificationHandler.messageReceivedNotifications shouldHaveSize 0
        }
    }

    @Test
    fun `processPayload invokes application handler for messageReceived messages`() {
        val mockSealedData =
            "0123456789ABCDEF0123456789ABCDEF" +
                "0123456789ABCDEF0123456789ABCDEF" +
                "0123456789ABCDEF0123456789ABCDEF" +
                "0123456789ABCDEF0123456789ABCDEF" +
                "0123456789ABCDEF0123456789ABCDEF" +
                "0123456789ABCDEF0123456789ABCDEF" +
                "0123456789ABCDEF0123456789ABCDEF" +
                "0123456789ABCDEF0123456789ABCDEF" +
                "aaaaaaaaaa"
        val mockSealedDataBase64 = Base64.getEncoder().encodeToString(mockSealedData.toByteArray())

        val keyId = "key-id"
        val algorithm = "key-algorithm"
        val payload =
            mapOf(
                Pair(
                    "sudoplatform",
                    "{\"servicename\":\"emService\",\"data\":\"" +
                        "{\\\"keyId\\\":\\\"${keyId}\\\"," +
                        "\\\"algorithm\\\":\\\"${algorithm}\\\"," +
                        "\\\"sealed\\\":\\\"${mockSealedDataBase64}\\\"}\"}",
                ),
            )

        mockRemoteMessage.stub { on { data } doReturn payload }

        val internalNotification =
            MessageReceivedNotification(
                type = "messageReceived",
                messageId = "message-id",
                owner = "owner-id",
                sudoId = "sudo-id",
                emailAddressId = "email-address-id",
                folderId = "folder-id",
                encryptionStatus = EncryptionStatus.ENCRYPTED,
                hasAttachments = false,
                subject = "email subject",
                from = EmailMessage.EmailAddress("address@company.com"),
                receivedAtEpochMs = 2000,
                sentAtEpochMs = 1000,
            )

        val decryptedAesKey = "decrypted-aes-key"
        val encodedInternalMessageReceivedNotification = Json.encodeToString(MessageReceivedNotification.serializer(), internalNotification)
        mockDeviceKeyManager.stub {
            on { decryptWithKeyPairId(any(), any(), any()) } doReturn decryptedAesKey.toByteArray()
            on { decryptWithSymmetricKey(any(), any(), isNull()) } doReturn encodedInternalMessageReceivedNotification.toByteArray()
        }

        client.processPayload(mockRemoteMessage)

        testNotificationHandler.messageReceivedNotifications shouldHaveSize 1

        testNotificationHandler.messageReceivedNotifications[0] shouldBe
            EmailMessageReceivedNotification(
                id = internalNotification.messageId,
                owner = internalNotification.owner,
                sudoId = internalNotification.sudoId,
                emailAddressId = internalNotification.emailAddressId,
                folderId = internalNotification.folderId,
                encryptionStatus = internalNotification.encryptionStatus,
                hasAttachments = internalNotification.hasAttachments,
                from = internalNotification.from,
                replyTo = internalNotification.replyTo,
                subject = internalNotification.subject,
                receivedAt = Date(internalNotification.receivedAtEpochMs),
                sentAt = Date(internalNotification.sentAtEpochMs),
            )

        val dataCaptor = argumentCaptor<ByteArray>()
        val keyIdCaptor = argumentCaptor<String>()
        val algorithmCaptor = argumentCaptor<KeyManagerInterface.PublicKeyEncryptionAlgorithm>()
        verify(mockDeviceKeyManager).decryptWithKeyPairId(
            dataCaptor.capture(),
            keyIdCaptor.capture(),
            algorithmCaptor.capture(),
        )
        // First 256 chars are interpreted as the sealed
        dataCaptor.firstValue shouldBe mockSealedData.subSequence(0, 256).toString().toByteArray()
        keyIdCaptor.firstValue shouldBe keyId
        algorithmCaptor.firstValue shouldBe KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_PKCS1

        val keyCaptor = argumentCaptor<ByteArray>()
        verify(mockDeviceKeyManager).decryptWithSymmetricKey(
            keyCaptor.capture(),
            dataCaptor.capture(),
            isNull(),
        )

        keyCaptor.firstValue shouldBe decryptedAesKey.toByteArray()
        dataCaptor.secondValue shouldBe mockSealedData.subSequence(256, mockSealedData.length).toString().toByteArray()
    }
}
