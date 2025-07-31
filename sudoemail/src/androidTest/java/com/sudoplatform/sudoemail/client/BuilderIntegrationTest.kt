/*
 * Copyright © 2025 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudoapiclient.ApiClientManager
import com.sudoplatform.sudoemail.BaseIntegrationTest
import com.sudoplatform.sudoemail.SudoEmailClient
import com.sudoplatform.sudoemail.api.ApiClient
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test the operation of [SudoEmailClient.builder].
 */
@RunWith(AndroidJUnit4::class)
class BuilderIntegrationTest : BaseIntegrationTest() {
    @Before
    fun setup() {
        sudoClient.reset()
        sudoClient.generateEncryptionKey()
    }

    @After
    fun teardown() = runTest {
        sudoClient.reset()
    }

    @Test
    fun shouldThrowIfRequiredItemsNotProvidedToBuilder() {
        // All required items not provided
        shouldThrow<NullPointerException> {
            SudoEmailClient.builder().build()
        }

        // Context not provided
        shouldThrow<NullPointerException> {
            SudoEmailClient.builder()
                .setSudoUserClient(userClient)
                .build()
        }

        // SudoUserClient not provided
        shouldThrow<NullPointerException> {
            SudoEmailClient.builder()
                .setContext(context)
                .build()
        }
    }

    @Test
    fun shouldNotThrowIfTheRequiredItemsAreProvidedToBuilder() {
        SudoEmailClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .build()
    }

    @Test
    fun shouldNotThrowIfAllItemsAreProvidedToBuilder() {
        val apiClient = ApiClient(ApiClientManager.getClient(context, userClient), logger)

        SudoEmailClient.builder()
            .setContext(context)
            .setSudoUserClient(userClient)
            .setApiClient(apiClient)
            .setKeyManager(keyManager)
            .setLogger(logger)
            .build()
    }
}
