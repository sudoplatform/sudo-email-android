/*
 * Copyright © 2020 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudoprofiles.ListOption
import com.sudoplatform.sudoprofiles.SudoProfilesClient
import com.sudoplatform.sudouser.SudoUserClient
import com.sudoplatform.sudouser.TESTAuthenticationProvider
import io.kotlintest.shouldBe
import timber.log.Timber

/**
 * Test the operation of the [SudoEmailClient].
 *
 * @since 2020-08-04
 */
abstract class BaseIntegrationTest {

    protected val context: Context = ApplicationProvider.getApplicationContext<Context>()

    protected val userClient by lazy {
        SudoUserClient.builder(context)
            .setNamespace("eml-client-test")
            .build()
    }

    protected val sudoClient by lazy {
        val containerURI = context.cacheDir.toURI()
        SudoProfilesClient.builder(context, userClient, containerURI)
            .build()
    }

    protected val keyManager by lazy {
        KeyManagerFactory(context).createAndroidKeyManager()
    }

    private suspend fun register() {
        userClient.isRegistered() shouldBe false

        val privateKey = readTextFile("register_key.private")
        val keyId = readTextFile("register_key.id")

        val authProvider = TESTAuthenticationProvider(
            name = "eml-client-test",
            privateKey = privateKey,
            publicKey = null,
            keyManager = keyManager,
            keyId = keyId
        )

        userClient.registerWithAuthenticationProvider(authProvider, "eml-client-test")
    }

    private fun readTextFile(fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use {
            it.readText().trim()
        }
    }

    protected suspend fun signInAndRegister() {
        if (!userClient.isRegistered()) {
            register()
        }
        userClient.isRegistered() shouldBe true
        if (userClient.isSignedIn()) {
            userClient.getRefreshToken()?.let { userClient.refreshTokens(it) }
        } else {
            userClient.signInWithKey()
        }
        userClient.isSignedIn() shouldBe true
    }

    protected fun clientConfigFilesPresent(): Boolean {
        val configFiles = context.assets.list("")?.filter { fileName ->
            fileName == "sudoplatformconfig.json" ||
                fileName == "register_key.private" ||
                fileName == "register_key.id"
        } ?: emptyList()
        Timber.d("config files present ${configFiles.size}")
        return configFiles.size == 3
    }

    protected suspend fun deleteAllSudos() {
        if (userClient.isRegistered()) {
            sudoClient.listSudos(ListOption.REMOTE_ONLY).forEach {
                try {
                    sudoClient.deleteSudo(it)
                } catch (e: Throwable) {
                    Timber.e(e)
                }
            }
        }
    }
}
