/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sudoplatform.sudokeymanager.KeyManagerFactory
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudokeymanager.SecureKeyArchiveException
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Test that key archives import only into the namespace they were exported from, and that an
 * archive that can't be imported leaves the existing keys in place.
 *
 * Another device is stood in for by a second key manager in the same namespace, used once the
 * source's keys have been exported and removed (both share the emulator's key store). A key manager
 * in another namespace checks that an archive it can't use leaves its keys in place.
 */
@RunWith(AndroidJUnit4::class)
class KeyArchiveTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val userClient =
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "mockUserId"
        }

    private val algorithm = KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1

    private lateinit var sourceKeyManager: KeyManagerInterface
    private lateinit var targetKeyManager: KeyManagerInterface
    private lateinit var otherKeyManager: KeyManagerInterface
    private lateinit var source: DefaultServiceKeyManager
    private lateinit var target: DefaultServiceKeyManager
    private lateinit var other: DefaultServiceKeyManager

    private fun serviceKeyManager(keyManager: KeyManagerInterface) =
        DefaultServiceKeyManager(
            keyRingServiceName = "sudo-email",
            userClient = userClient,
            keyManager = keyManager,
        )

    @Before
    fun setUp() {
        val namespace = "eml-archive-${UUID.randomUUID()}"
        sourceKeyManager = KeyManagerFactory(context).createAndroidKeyManager(namespace)
        targetKeyManager = KeyManagerFactory(context).createAndroidKeyManager(namespace)
        otherKeyManager = KeyManagerFactory(context).createAndroidKeyManager("eml-archive-other-${UUID.randomUUID()}")
        source = serviceKeyManager(sourceKeyManager)
        target = serviceKeyManager(targetKeyManager)
        other = serviceKeyManager(otherKeyManager)
    }

    @After
    fun tearDown() {
        runCatching { sourceKeyManager.removeAllKeys() }
        runCatching { otherKeyManager.removeAllKeys() }
    }

    /** Exports the source's keys and removes them, so [target] starts as a device without them. */
    private fun exportAndRemoveSourceKeys(): ByteArray {
        val archive = source.exportKeys()
        source.removeAllKeys()
        return archive
    }

    @Test
    fun importKeysShouldMakeEncryptedDataReadableOnAnotherStoreInTheSameNamespace() {
        val keyPair = source.generateKeyPair()
        val clearData = "hello world".toByteArray()
        val encrypted = source.encryptWithKeyPairId(keyPair.keyId, clearData, algorithm)

        val archive = exportAndRemoveSourceKeys()
        target.privateKeyExists(keyPair.keyId) shouldBe false

        target.importKeys(archive)

        target.privateKeyExists(keyPair.keyId) shouldBe true
        target.decryptWithKeyPairId(encrypted, keyPair.keyId, algorithm) shouldBe clearData
    }

    @Test
    fun importKeysShouldReplaceRatherThanMergeTheExistingKeys() {
        val sourceKeyPair = source.generateKeyPair()
        val archive = exportAndRemoveSourceKeys()
        val targetOwnKeyPair = target.generateKeyPair()

        target.importKeys(archive)

        target.privateKeyExists(sourceKeyPair.keyId) shouldBe true
        target.privateKeyExists(targetOwnKeyPair.keyId) shouldBe false
    }

    @Test
    fun importKeysShouldRejectDataThatIsNotAnArchiveAndKeepTheKeys() {
        val own = target.generateKeyPair()

        shouldThrow<DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException> {
            target.importKeys("this is not a key archive".toByteArray())
        }

        target.privateKeyExists(own.keyId) shouldBe true
    }

    @Test
    fun importKeysFromAnotherNamespaceShouldFailAndKeepTheKeys() {
        source.generateKeyPair()
        val own = other.generateKeyPair()

        val error =
            shouldThrow<DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException> {
                other.importKeys(source.exportKeys())
            }

        (error.cause as SecureKeyArchiveException).reason shouldBe SecureKeyArchiveException.NO_KEYS_FOR_NAMESPACE
        other.privateKeyExists(own.keyId) shouldBe true
    }

    @Test
    fun importKeysOfAnArchiveWithNoNameSpaceShouldFailAndKeepTheKeys() {
        // Archives exported before key manager 10.0.0 don't record a namespace.
        val exported = source.generateKeyPair()
        val json = JSONObject(String(GZIPInputStream(source.exportKeys().inputStream()).use { it.readBytes() }))
        val keys = json.getJSONArray("Keys")
        for (i in 0 until keys.length()) keys.getJSONObject(i).remove("NameSpace")
        val archive = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(json.toString().toByteArray()) } }
        source.removeAllKeys()
        val own = target.generateKeyPair()

        val error =
            shouldThrow<DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException> {
                target.importKeys(archive.toByteArray())
            }

        (error.cause as SecureKeyArchiveException).reason shouldBe SecureKeyArchiveException.NO_KEYS_FOR_NAMESPACE
        target.privateKeyExists(own.keyId) shouldBe true
        target.privateKeyExists(exported.keyId) shouldBe false
    }
}
