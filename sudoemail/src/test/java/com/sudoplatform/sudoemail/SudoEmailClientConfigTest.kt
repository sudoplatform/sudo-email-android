/*
 * Copyright © 2020 - Anonyome Labs, Inc. - All rights reserved
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import com.nhaarman.mockitokotlin2.mock
import com.sudoplatform.sudoconfigmanager.SudoConfigManager
import com.sudoplatform.sudoemail.logging.LogConstants
import com.sudoplatform.sudologging.AndroidUtilsLogDriver
import com.sudoplatform.sudologging.LogLevel
import io.kotlintest.shouldThrow
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test the handling of the JSON config items.
 *
 * @since 2020-08-07
 */
@RunWith(RobolectricTestRunner::class)
class SudoEmailClientConfigTest : BaseTests() {

    private val mockContext by before {
        mock<Context>()
    }

    private fun configManager(configJson: String): SudoConfigManager {
        return object : SudoConfigManager {
            override fun getConfigSet(namespace: String): JSONObject? {
                if (namespace == "identityService") {
                    return JSONObject(configJson)
                }
                return null
            }
        }
    }

    @Test
    fun shouldThrowIfConfigMissing() {

        val logger = com.sudoplatform.sudologging.Logger(LogConstants.SUDOLOG_TAG, AndroidUtilsLogDriver(LogLevel.INFO))

        val noConfigJson = ""
        shouldThrow<SudoEmailClient.Builder.ConfigurationException> {
            SudoEmailClient.readConfiguration(mockContext, logger, configManager(noConfigJson))
        }

        val emptyConfigJson = "{}"
        shouldThrow<SudoEmailClient.Builder.ConfigurationException> {
            SudoEmailClient.readConfiguration(mockContext, logger, configManager(emptyConfigJson))
        }

        val missingRegionJson = """
            {
                "bucket": "foo",
                "transientBucket": "ids-userdata-eml-dev-transientuserdatabucket0d043-5tkr1hts9sja"
            }
        """.trimIndent()

        shouldThrow<SudoEmailClient.Builder.ConfigurationException> {
            SudoEmailClient.readConfiguration(mockContext, logger, configManager(missingRegionJson))
        }

        val missingBucketJson = """
            {
                "region": "us-east-1"
                "transientBucket": "ids-userdata-eml-dev-transientuserdatabucket0d043-5tkr1hts9sja"
            }
        """.trimIndent()

        shouldThrow<SudoEmailClient.Builder.ConfigurationException> {
            SudoEmailClient.readConfiguration(mockContext, logger, configManager(missingBucketJson))
        }

        val missingTransientBucketJson = """
            {
                "region": "us-east-1"
                "bucket": "foo"
            }
        """.trimIndent()

        shouldThrow<SudoEmailClient.Builder.ConfigurationException> {
            SudoEmailClient.readConfiguration(mockContext, logger, configManager(missingTransientBucketJson))
        }

        val completeConfigJson = """
            {
                "region": "us-east-1",
                "bucket": "foo",
                "transientBucket": "ids-userdata-eml-dev-transientuserdatabucket0d043-5tkr1hts9sja"
            }
        """.trimIndent()

        SudoEmailClient.readConfiguration(mockContext, logger, configManager(completeConfigJson))
    }
}
