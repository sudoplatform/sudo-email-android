/*
 * Copyright © 2026 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.keys

import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudokeymanager.KeyManager
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Test that [DefaultDeviceKeyManager.importKeys] refuses an archive holding keys for another
 * namespace, rather than importing only the keys for its own.
 */
@RunWith(RobolectricTestRunner::class)
class KeyArchiveNamespaceTest : BaseTests() {
    private fun deviceKeyManager(namespace: String) = DefaultDeviceKeyManager(KeyManager(InMemoryStore(namespace)), mockLogger)

    private fun readArchive(archive: ByteArray) = JSONObject(String(GZIPInputStream(archive.inputStream()).use { it.readBytes() }))

    private fun writeArchive(json: JSONObject) =
        ByteArrayOutputStream()
            .also { out -> GZIPOutputStream(out).use { it.write(json.toString().toByteArray()) } }
            .toByteArray()

    @Test
    fun `importKeys() should refuse an archive with keys in another namespace and keep the existing keys`() {
        val source = deviceKeyManager("sudo-email").apply { generateNewCurrentSymmetricKey() }
        val other = deviceKeyManager("other-namespace").apply { generateNewCurrentSymmetricKey() }
        val mixed = readArchive(source.exportKeys())
        val otherKeys = readArchive(other.exportKeys()).getJSONArray("Keys")
        for (i in 0 until otherKeys.length()) mixed.getJSONArray("Keys").put(otherKeys.getJSONObject(i))

        val target = deviceKeyManager("sudo-email")
        val ownKeyId = target.generateNewCurrentSymmetricKey()

        val error =
            shouldThrow<DeviceKeyManager.DeviceKeyManagerException.SecureKeyArchiveException> {
                target.importKeys(writeArchive(mixed))
            }

        error.message?.contains("\"other-namespace\"") shouldBe true
        target.getCurrentSymmetricKeyId() shouldBe ownKeyId
    }

    @Test
    fun `importKeys() should import an archive whose keys are all in its namespace`() {
        val source = deviceKeyManager("sudo-email")
        val sourceKeyId = source.generateNewCurrentSymmetricKey()

        val target = deviceKeyManager("sudo-email").apply { generateNewCurrentSymmetricKey() }

        target.importKeys(source.exportKeys())

        target.getCurrentSymmetricKeyId() shouldBe sourceKeyId
    }
}
