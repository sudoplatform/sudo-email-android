/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudonotification.types.NotificationConfiguration
import com.sudoplatform.sudonotification.types.NotificationFilterItem
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test correct manipulation of Notification SDK's NotificationConfiguration class by the Email SDK
 * extensions to this class.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationConfigurationExtensionsTest {
    @Test
    fun `initEmailNotifications should accept all message received notifications`() {
        val emptyConfig = NotificationConfiguration(configs = listOf())
        val config = emptyConfig.initEmailNotifications()

        val configs = config.configs

        configs shouldHaveSize 2

        // First rule should be to exclude unrecognized notifications
        configs[0].status shouldBe NotificationConfiguration.DISABLE_STR
        configs[0].rules shouldBe "{\"!=\":[{\"var\":\"meta.type\"},\"messageReceived\"]}"

        // Second rule should be permit everything
        configs[1].status shouldBe NotificationConfiguration.ENABLE_STR
        configs[1].rules shouldBe NotificationConfiguration.DEFAULT_RULE_STRING
    }

    @Test
    fun `initEmailNotifications() should preserve existing rules`() {
        val existingItem =
            NotificationFilterItem(
                name = "emService",
                status = NotificationConfiguration.DISABLE_STR,
                rules = "{\"==\":[{\"var\":\"meta.emailAddressId\"}, \"ignored-email-address-id\"]}",
            )

        val initialConfig = NotificationConfiguration(configs = listOf(existingItem))

        val config = initialConfig.initEmailNotifications()

        val configs = config.configs

        configs shouldHaveSize 3

        // First rule should be to exclude unrecognized notifications
        configs[0].status shouldBe NotificationConfiguration.DISABLE_STR
        configs[0].rules shouldBe "{\"!=\":[{\"var\":\"meta.type\"},\"messageReceived\"]}"

        // Second rule should match existing entry
        configs[1] shouldBe existingItem

        // Third rule should be permit everything
        configs[2].status shouldBe NotificationConfiguration.ENABLE_STR
        configs[2].rules shouldBe NotificationConfiguration.DEFAULT_RULE_STRING
    }

    @Test
    fun `initEmailNotifications() should preserve existing rules on reinitialisation`() {
        val existingItem =
            NotificationFilterItem(
                name = "emService",
                status = NotificationConfiguration.DISABLE_STR,
                rules = "{\"==\":[{\"var\":\"meta.emailAddressId\"}, \"ignored-email-address-id\"]}",
            )

        val initialConfig =
            NotificationConfiguration(configs = listOf(existingItem))
                .initEmailNotifications()

        val config = initialConfig.initEmailNotifications()

        val configs = config.configs

        configs shouldHaveSize 3

        // First rule should be to exclude unrecognized notifications
        configs[0].status shouldBe NotificationConfiguration.DISABLE_STR
        configs[0].rules shouldBe "{\"!=\":[{\"var\":\"meta.type\"},\"messageReceived\"]}"

        // Second rule should match existing entry
        configs[1] shouldBe existingItem

        // Third rule should be permit everything
        configs[2].status shouldBe NotificationConfiguration.ENABLE_STR
        configs[2].rules shouldBe NotificationConfiguration.DEFAULT_RULE_STRING
    }

    @Test
    fun `enabling address when not disabled has no effect`() {
        val initialConfig =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()

        val config =
            initialConfig.setEmailNotificationsForAddressId(
                emailAddressId = "email-address-id",
                enabled = true,
            )

        config shouldBe initialConfig
    }

    fun `disabling address when not disabled adds rule`() {
        val initialConfig =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()

        val emailAddressId = "email-address-id"
        val config =
            initialConfig.setEmailNotificationsForAddressId(
                emailAddressId = emailAddressId,
                enabled = false,
            )

        val expectedItem =
            NotificationFilterItem(
                name = "emService",
                status = NotificationConfiguration.DISABLE_STR,
                rules = "{\"==\":[{\"var\":\"meta.emailAddressId\"},\"$emailAddressId\"]}",
            )

        config.configs shouldHaveSize 3

        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[1] shouldBe expectedItem
        config.configs[2] shouldBe initialConfig.configs[1]
    }

    @Test
    fun `disabling address when already disabled has no effect`() {
        val emailAddressId = "email-address-id"

        val initialConfig =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()
                .setEmailNotificationsForAddressId(emailAddressId = emailAddressId, enabled = false)

        val config =
            initialConfig.setEmailNotificationsForAddressId(
                emailAddressId = emailAddressId,
                enabled = false,
            )

        config shouldBe initialConfig
    }

    @Test
    fun `enabling address when disabled removes disable rule`() {
        val emailAddressId = "email-address-id"

        val initialConfig =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()
                .setEmailNotificationsForAddressId(emailAddressId = emailAddressId, enabled = false)

        val config =
            initialConfig.setEmailNotificationsForAddressId(
                emailAddressId = emailAddressId,
                enabled = true,
            )

        config.configs shouldHaveSize 2

        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[1] shouldBe initialConfig.configs[2]
    }

    @Test
    fun `disabling address preserves existing rule`() {
        val emailAddressId1 = "email-address-id-1"
        val emailAddressId2 = "email-address-id-2"
        val sudoId1 = "sudo-id-1"

        val initialConfig =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()
                .setEmailNotificationsForAddressId(emailAddressId = emailAddressId1, enabled = false)
                .setEmailNotificationsForSudoId(sudoId = sudoId1, enabled = false)

        val config =
            initialConfig.setEmailNotificationsForAddressId(
                emailAddressId = emailAddressId2,
                enabled = false,
            )

        val expectedItem =
            NotificationFilterItem(
                name = "emService",
                status = NotificationConfiguration.DISABLE_STR,
                rules = "{\"==\":[{\"var\":\"meta.emailAddressId\"},\"$emailAddressId2\"]}",
            )

        config.configs shouldHaveSize 5

        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[4] shouldBe initialConfig.configs[3]

        // Not really order dependent but easiest to verify this way
        config.configs[1] shouldBe initialConfig.configs[1]
        config.configs[2] shouldBe initialConfig.configs[2]
        config.configs[3] shouldBe expectedItem
    }

    @Test
    fun `enabling address preserves existing rules`() {
        val emailAddressId1 = "email-address-id-1"
        val emailAddressId2 = "email-address-id-2"
        val sudoId1 = "sudo-id-1"

        val initialConfig =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()
                .setEmailNotificationsForAddressId(emailAddressId = emailAddressId1, enabled = false)
                .setEmailNotificationsForAddressId(emailAddressId = emailAddressId2, enabled = false)
                .setEmailNotificationsForSudoId(sudoId = sudoId1, enabled = false)

        val config =
            initialConfig.setEmailNotificationsForAddressId(
                emailAddressId = emailAddressId2,
                enabled = true,
            )

        config.configs shouldHaveSize 4

        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[3] shouldBe initialConfig.configs[4]

        // Not really order dependent but easiest to verify this way
        config.configs[1] shouldBe initialConfig.configs[1]
        config.configs[2] shouldBe initialConfig.configs[3]
    }

    @Test
    fun `enabling sudo when not disabled has no effect`() {
        val initialConfig =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()

        val config =
            initialConfig.setEmailNotificationsForSudoId(
                sudoId = "sudo-id",
                enabled = true,
            )

        config shouldBe initialConfig
    }

    @Test
    fun `disabling sudo when not disabled adds rule`() {
        val initialConfig =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()

        val sudoId = "sudo-id"
        val config =
            initialConfig.setEmailNotificationsForSudoId(
                sudoId = sudoId,
                enabled = false,
            )

        val expectedItem =
            NotificationFilterItem(
                name = "emService",
                status = NotificationConfiguration.DISABLE_STR,
                rules = "{\"==\":[{\"var\":\"meta.sudoId\"},\"$sudoId\"]}",
            )

        config.configs shouldHaveSize 3

        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[1] shouldBe expectedItem
        config.configs[2] shouldBe initialConfig.configs[1]
    }

    @Test
    fun `disabling sudo when already disabled has no effect`() {
        val sudoId = "sudo-id"

        val initialConfig =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()
                .setEmailNotificationsForSudoId(sudoId = sudoId, enabled = false)

        val config =
            initialConfig.setEmailNotificationsForSudoId(
                sudoId = sudoId,
                enabled = false,
            )

        config shouldBe initialConfig
    }

    @Test
    fun `enabling sudo when disabledR removes disable rule`() {
        val sudoId = "sudo-id"

        val initialConfig =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()
                .setEmailNotificationsForSudoId(sudoId = sudoId, enabled = false)

        val config =
            initialConfig.setEmailNotificationsForSudoId(
                sudoId = sudoId,
                enabled = true,
            )

        config.configs shouldHaveSize 2

        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[1] shouldBe initialConfig.configs[2]
    }

    @Test
    fun `disabling sudo preserves existing rules`() {
        val emailAddressId1 = "email-address-id-1"
        val sudoId1 = "sudo-id-1"
        val sudoId2 = "sudo-id-2"

        val initialConfig =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()
                .setEmailNotificationsForSudoId(sudoId = sudoId1, enabled = false)
                .setEmailNotificationsForAddressId(emailAddressId = emailAddressId1, enabled = false)

        val config =
            initialConfig.setEmailNotificationsForSudoId(
                sudoId = sudoId2,
                enabled = false,
            )

        val expectedItem =
            NotificationFilterItem(
                name = "emService",
                status = NotificationConfiguration.DISABLE_STR,
                rules = "{\"==\":[{\"var\":\"meta.sudoId\"},\"$sudoId2\"]}",
            )

        config.configs shouldHaveSize 5

        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[4] shouldBe initialConfig.configs[3]

        // Not really order dependent but easiest to verify this way
        config.configs[1] shouldBe initialConfig.configs[1]
        config.configs[2] shouldBe initialConfig.configs[2]
        config.configs[3] shouldBe expectedItem
    }

    @Test
    fun `enabling sudo preserves existing rules`() {
        val emailAddressId1 = "email-address-id-1"
        val sudoId1 = "sudo-id-1"
        val sudoId2 = "sudo-id-2"

        val initialConfig =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()
                .setEmailNotificationsForAddressId(emailAddressId = emailAddressId1, enabled = false)
                .setEmailNotificationsForSudoId(sudoId = sudoId1, enabled = false)
                .setEmailNotificationsForSudoId(sudoId = sudoId2, enabled = false)

        val config =
            initialConfig.setEmailNotificationsForSudoId(
                sudoId = sudoId1,
                enabled = true,
            )

        config.configs shouldHaveSize 4

        config.configs[0] shouldBe initialConfig.configs[0]
        config.configs[3] shouldBe initialConfig.configs[4]

        // Not really order dependent but easiest to verify this way
        config.configs[1] shouldBe initialConfig.configs[1]
        config.configs[2] shouldBe initialConfig.configs[3]
    }

    @Test
    fun `isEmailNotificationForAddressIdEnabled() should return true for initial configuration`() {
        val config =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()

        config.isEmailNotificationForAddressIdEnabled(emailAddressId = "email-address-id") shouldBe true
    }

    @Test
    fun `isEmailNotificationForAddressIdEnabled() should return false if disabled`() {
        val emailAddressId = "email-address-id"

        val config =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()
                .setEmailNotificationsForAddressId(emailAddressId = emailAddressId, enabled = false)

        config.isEmailNotificationForAddressIdEnabled(emailAddressId = "email-address-id") shouldBe false
    }

    @Test
    fun `isEmailNotificationForAddressIdEnabled() should return true if other address is disabled`() {
        val emailAddressId1 = "email-address-id-1"
        val emailAddressId2 = "email-address-id-2"

        val config =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()
                .setEmailNotificationsForAddressId(emailAddressId = emailAddressId1, enabled = false)

        config.isEmailNotificationForAddressIdEnabled(emailAddressId = emailAddressId2) shouldBe true
    }

    @Test
    fun `isEmailNotificationForAddressIdEnabled() should return false for multiple disabled addresses`() {
        val emailAddressId1 = "email-address-id-1"
        val emailAddressId2 = "email-address-id-2"

        val config =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()
                .setEmailNotificationsForAddressId(emailAddressId = emailAddressId1, enabled = false)
                .setEmailNotificationsForAddressId(emailAddressId = emailAddressId2, enabled = false)

        config.isEmailNotificationForAddressIdEnabled(emailAddressId = emailAddressId1) shouldBe false
        config.isEmailNotificationForAddressIdEnabled(emailAddressId = emailAddressId2) shouldBe false
    }

    @Test
    fun `isEmailNotificationForSudoIdEnabled() should return true for initial configuration`() {
        val config =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()

        config.isEmailNotificationForSudoIdEnabled(sudoId = "sudo-id") shouldBe true
    }

    @Test
    fun `isEmailNotificationForSudoIdEnabled() should return false if disabled`() {
        val sudoId = "sudo-id"

        val config =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()
                .setEmailNotificationsForSudoId(
                    sudoId = sudoId,
                    enabled = false,
                )

        config.isEmailNotificationForSudoIdEnabled(sudoId = sudoId) shouldBe false
    }

    @Test
    fun `isEmailNotificationForSudoIdEnabled() should return true if other address is disabled`() {
        val sudoId1 = "sudo-id-1"
        val sudoId2 = "sudo-id-2"

        val config =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()
                .setEmailNotificationsForSudoId(sudoId = sudoId1, enabled = false)

        config.isEmailNotificationForSudoIdEnabled(sudoId = sudoId2) shouldBe true
    }

    @Test
    fun `isEmailNotificationForSudoIdEnabled() should return false for multiple disabled addresses`() {
        val sudoId1 = "sudo-id-1"
        val sudoId2 = "sudo-id-2"

        val config =
            NotificationConfiguration(configs = listOf())
                .initEmailNotifications()
                .setEmailNotificationsForSudoId(sudoId = sudoId1, enabled = false)
                .setEmailNotificationsForSudoId(sudoId = sudoId2, enabled = false)

        config.isEmailNotificationForSudoIdEnabled(sudoId = sudoId1) shouldBe false
        config.isEmailNotificationForSudoIdEnabled(sudoId = sudoId2) shouldBe false
    }
}
