/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import com.sudoplatform.sudoemail.notifications.MessageReceivedNotification
import com.sudoplatform.sudoemail.util.Constants
import com.sudoplatform.sudonotification.types.NotificationConfiguration
import com.sudoplatform.sudonotification.types.NotificationFilterItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// By default, disable all notifications we do not know how to handle
internal val DEFAULT_FIRST_RULE_STRING = JsonObject(
    mapOf(
        Pair(
            "!=",
            JsonArray(
                listOf(
                    JsonObject(
                        mapOf(
                            Pair("var", JsonPrimitive("meta.type")),
                        ),
                    ),
                    JsonPrimitive(MessageReceivedNotification.TYPE),
                ),
            ),
        ),
    ),
).toString()

// Disable notification types other than those we know how to handle
internal val DEFAULT_FIRST_RULE = NotificationFilterItem(
    name = Constants.SERVICE_NAME,
    status = NotificationConfiguration.DISABLE_STR,
    rules = DEFAULT_FIRST_RULE_STRING,
)

internal const val DEFAULT_LAST_RULE_STRING = NotificationConfiguration.DEFAULT_RULE_STRING

// Enable all otherwise unfiltered out notifications
internal val DEFAULT_LAST_RULE = NotificationFilterItem(
    name = Constants.SERVICE_NAME,
    status = NotificationConfiguration.ENABLE_STR,
    rules = DEFAULT_LAST_RULE_STRING,
)

internal fun isRuleMatchingEmailAddressId(rule: String?, emailAddressId: String): Boolean {
    return isRuleMatchingSingleMeta(rule, "emailAddressId", emailAddressId)
}

internal fun isRuleMatchingSudoId(rule: String?, sudoId: String): Boolean {
    return isRuleMatchingSingleMeta(rule, "sudoId", sudoId)
}

internal fun isRuleMatchingSingleMeta(rule: String?, metaName: String, metaValue: String): Boolean {
    if (rule == null) {
        return false
    }

    val jsonRules = Json.decodeFromString<JsonObject>(rule)
    val equality = jsonRules["=="]
    if (equality is JsonArray && equality.size == 2) {
        val lhs = equality[0]
        val rhs = equality[1]

        // "var meta.emailAddressId == emailAddressId
        if (lhs is JsonObject && rhs is JsonPrimitive && rhs.isString) {
            val v = lhs["var"]
            if (v is JsonPrimitive && v.isString && v.content == "meta.$metaName" && rhs.content == metaValue) {
                return true
            }
        }

        // "emailAddressId == var meta.emailAddressId
        else if (rhs is JsonObject && lhs is JsonPrimitive && lhs.isString) {
            val v = rhs["var"]
            if (v is JsonPrimitive && v.isString && v.content == "meta.$metaName" && lhs.content == metaValue) {
                return true
            }
        }
    }

    return false
}

/**
 * Extension function to ensure a [NotificationConfiguration] is initialized for
 * receipt of email service notifications.
 *
 * @return New NotificationConfiguration with updated rules
 */
fun NotificationConfiguration.initEmailNotifications(): NotificationConfiguration {
    val newConfigs = this.configs
        .filter { it.name != Constants.SERVICE_NAME }
        .toMutableList()

    val emServiceConfigs = this.configs
        .filter { it.name == Constants.SERVICE_NAME }
        // Filter out any current or historic default rules.
        // We'll add current default rules back in
        .filter { it.rules != DEFAULT_FIRST_RULE_STRING && it.rules != DEFAULT_LAST_RULE_STRING }
        .toMutableList()

    newConfigs.add(DEFAULT_FIRST_RULE)
    newConfigs.addAll(emServiceConfigs)
    newConfigs.add(DEFAULT_LAST_RULE)

    return NotificationConfiguration(
        configs = newConfigs.toList(),
    )
}

internal fun NotificationConfiguration.setEmailNotificationsForSingleMeta(
    metaName: String,
    metaValue: String,
    enabled: Boolean,
): NotificationConfiguration {
    // Start with any rules for other services
    val newRules = this.configs
        .filter { it.name != Constants.SERVICE_NAME }.toMutableList()

    // Then find all the email service rules except our defaults and
    // any existing rule matching this meta.
    val newEmServiceRules = this.configs
        .filter { it.name == Constants.SERVICE_NAME }
        // Filter out any current or historic default rules.
        // We'll add current default rules back in
        .filter { it.rules != DEFAULT_FIRST_RULE_STRING && it.rules != DEFAULT_LAST_RULE_STRING }
        // Filter out any rule specific to our meta name and value
        .filter { !isRuleMatchingSingleMeta(it.rules, metaName, metaValue) }

    // Re-add DEFAULT_FIRST_RULE
    newRules.add(DEFAULT_FIRST_RULE)

    // Re-add other email service rules
    newRules.addAll(newEmServiceRules)

    // If we're disabling notifications for this meta value then
    // add an explicit rule for that
    if (!enabled) {
        val newJsonRule = JsonObject(
            mapOf(
                Pair(
                    "==",
                    JsonArray(
                        listOf(
                            JsonObject(
                                mapOf(
                                    Pair("var", JsonPrimitive("meta.$metaName")),
                                ),
                            ),
                            JsonPrimitive(metaValue),
                        ),
                    ),
                ),
            ),
        )

        newRules.add(
            NotificationFilterItem(
                name = Constants.SERVICE_NAME,
                status = NotificationConfiguration.DISABLE_STR,
                rules = newJsonRule.toString(),
            ),
        )
    }

    // Re-add the default catch all enabling rule
    newRules.add(DEFAULT_LAST_RULE)

    return NotificationConfiguration(
        configs = newRules.toList(),
    )
}

/**
 * Extension function to add rules to a [NotificationConfiguration] for enabling
 * or disabling email service notifications for a particular email address ID.
 *
 * Once all notification configurations across all Sudo platform SDKs have
 * been performed, call the
 * [com.sudoplatform.sudonotification.SudoNotificationClient.setNotificationConfiguration]
 * to set the full notification configuration for your application.
 *
 * @param emailAddressId
 *      ID of email address to set email service notification enablement for
 *
 * @param enabled
 *      Whether or not email service notifications are to be enabled or disabled for the
 *      email address with the specified ID.
 *
 * @return New NotificationConfiguration with updated rules
 */
fun NotificationConfiguration.setEmailNotificationsForAddressId(emailAddressId: String, enabled: Boolean): NotificationConfiguration {
    return setEmailNotificationsForSingleMeta("emailAddressId", emailAddressId, enabled)
}

/**
 * Extension function to add rules to a [NotificationConfiguration] for enabling
 * or disabling email service notifications for a particular sudo ID.
 *
 * Once all notification configurations across all Sudo platform SDKs have
 * been performed, call the
 * [com.sudoplatform.sudonotification.SudoNotificationClient.setNotificationConfiguration]
 * to set the full notification configuration for your application.
 *
 * @param sudoId
 *      ID of Sudo to set email service notification enablement for
 *
 * @param enabled
 *      Whether or not email service notifications are to be enabled or disabled for the
 *      Sudo with the specified ID.
 *
 * @return New NotificationConfiguration with updated rules
 */
fun NotificationConfiguration.setEmailNotificationsForSudoId(sudoId: String, enabled: Boolean): NotificationConfiguration {
    return setEmailNotificationsForSingleMeta("sudoId", sudoId, enabled)
}

/**
 * Test whether or not email service notifications are enabled for a particular email address
 *
 * @param emailAddressId ID of email address to test
 *
 * @return Whether or not email service notifications are enabled for the email address with the specified ID
 */
fun NotificationConfiguration.isEmailNotificationForAddressIdEnabled(emailAddressId: String): Boolean {
    val emailAddressRule = this.configs
        .filter { it.name == Constants.SERVICE_NAME }
        // Filter out any current or historic default rules.
        // We'll add current default rules back in
        .filter { it.rules != DEFAULT_FIRST_RULE_STRING && it.rules != DEFAULT_LAST_RULE_STRING }
        // Filter out any rule specific to our emailAddressId
        .find { isRuleMatchingEmailAddressId(it.rules, emailAddressId) }

    // Notifications are enabled for this email address if either there
    // is no matching rule (because the default enables it) or if the
    // matching rule explicitly enables them.
    return emailAddressRule == null ||
        emailAddressRule.status == NotificationConfiguration.ENABLE_STR
}

/**
 * Test whether or not email service notifications are enabled for a particular Sudo
 *
 * @param sudoId ID of Sudo to test
 *
 * @return Whether or not email service notifications are enabled for the Sudo with the specified ID
 */
fun NotificationConfiguration.isEmailNotificationForSudoIdEnabled(sudoId: String): Boolean {
    val sudoRule = this.configs
        .filter { it.name == Constants.SERVICE_NAME }
        // Filter out any current or historic default rules.
        // We'll add current default rules back in
        .filter { it.rules != DEFAULT_FIRST_RULE_STRING && it.rules != DEFAULT_LAST_RULE_STRING }
        // Filter out any rule specific to our sudoId
        .find { isRuleMatchingSudoId(it.rules, sudoId) }

    // Notifications are enabled for this Sudo if either there
    // is no matching rule (because the default enables it) or if the
    // matching rule explicitly enables them.
    return sudoRule == null ||
        sudoRule.status == NotificationConfiguration.ENABLE_STR
}
