/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.types.transformers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amazonaws.util.Base64
import com.sudoplatform.sudoemail.BaseTests
import com.sudoplatform.sudoemail.graphql.fragment.BlockedAddress
import com.sudoplatform.sudoemail.graphql.fragment.EmailAddressWithoutFolders
import com.sudoplatform.sudoemail.graphql.fragment.SealedAttribute
import com.sudoplatform.sudoemail.keys.DefaultServiceKeyManager
import com.sudoplatform.sudoemail.types.SymmetricKeyEncryptionAlgorithm
import com.sudoplatform.sudoemail.types.transformers.Unsealer.Companion.DEFAULT_ALGORITHM
import com.sudoplatform.sudokeymanager.AndroidSQLiteStore
import com.sudoplatform.sudokeymanager.KeyManager
import com.sudoplatform.sudokeymanager.KeyManagerInterface
import com.sudoplatform.sudouser.SudoUserClient
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import timber.log.Timber
import java.util.UUID

/**
 * Test the operation of [Unsealer] under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class UnsealerTest : BaseTests() {

    private val keyRingServiceName = "sudo-email"

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val mockUserClient by before {
        mock<SudoUserClient>().stub {
            on { getSubject() } doReturn "mockSubject"
        }
    }

    private val keyManager by before {
        KeyManager(AndroidSQLiteStore(context))
    }

    private val serviceKeyManager by before {
        DefaultServiceKeyManager(
            userClient = mockUserClient,
            keyRingServiceName = keyRingServiceName,
            keyManager = keyManager,
        )
    }

    private val symmetricKeyId = "symmetricKey"
    private val publicKeyId = UUID.randomUUID().toString()
    private val keyInfo = KeyInfo(publicKeyId, KeyType.PRIVATE_KEY, DEFAULT_ALGORITHM)
    private val keyInfo2 = KeyInfo(symmetricKeyId, KeyType.SYMMETRIC_KEY, DEFAULT_ALGORITHM)

    private val unsealer by before {
        Unsealer(
            serviceKeyManager,
            keyInfo,
        )
    }

    private val stringUnsealer by before {
        Unsealer(
            serviceKeyManager,
            keyInfo2,
        )
    }

    private fun seal(value: String): String {
        val encryptedSymmetricKeyBytes = keyManager.encryptWithPublicKey(
            publicKeyId,
            keyManager.getSymmetricKeyData(symmetricKeyId)!!,
            KeyManagerInterface.PublicKeyEncryptionAlgorithm.RSA_ECB_OAEPSHA1,
        )
        encryptedSymmetricKeyBytes.size shouldBe Unsealer.KEY_SIZE_AES

        val encryptedData = keyManager.encryptWithSymmetricKey(
            symmetricKeyId,
            value.toByteArray(),
            KeyManagerInterface.SymmetricEncryptionAlgorithm.AES_CBC_PKCS7_256,
        )

        val data = ByteArray(encryptedSymmetricKeyBytes.size + encryptedData.size)
        encryptedSymmetricKeyBytes.copyInto(data)
        encryptedData.copyInto(data, Unsealer.KEY_SIZE_AES)

        return String(Base64.encode(data), Charsets.UTF_8)
    }

    private fun sealString(value: String): String {
        val encryptedMetadata = serviceKeyManager.encryptWithSymmetricKeyId(
            symmetricKeyId,
            value.toByteArray(Charsets.UTF_8),
        )
        return String(Base64.encode(encryptedMetadata), Charsets.UTF_8)
    }

    @Before
    fun init() {
        Timber.plant(Timber.DebugTree())
        keyManager.removeAllKeys()
        keyManager.generateKeyPair(publicKeyId, true)
        keyManager.generateSymmetricKey(symmetricKeyId)
    }

    @After
    fun fini() = runTest {
        Timber.uprootAll()
    }

    @Test
    fun `unseal string`() {
        val clearData = "The owl and the pussycat went to sea in a beautiful pea green boat."
        val sealedData = seal(clearData)
        unsealer.unseal(sealedData) shouldBe clearData
    }

    @Test
    fun `unseal should throw if data too short`() {
        val shortData = String(Base64.encode("hello".toByteArray()), Charsets.UTF_8)
        shouldThrow<Unsealer.UnsealerException.SealedDataTooShortException> {
            unsealer.unseal(shortData)
        }
    }

    @Test
    fun `unseal EmailAddressWithoutFolders Alias should throw for unsupported algorithm`() {
        val sealedAlias = EmailAddressWithoutFolders.Alias(
            "Alias",
            SealedAttribute(
                "unsupported-algorithm",
                symmetricKeyId,
                "json-string",
                sealString("alias"),
            ),
        )

        shouldThrow<Unsealer.UnsealerException.UnsupportedAlgorithmException> {
            stringUnsealer.unseal(sealedAlias)
        }
    }

    @Test
    fun `unseal EmailAddressWithoutFolders Alias`() {
        val sealedAlias = EmailAddressWithoutFolders.Alias(
            "Alias",
            SealedAttribute(
                SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                symmetricKeyId,
                "json-string",
                sealString("alias"),
            ),
        )

        val alias = stringUnsealer.unseal(sealedAlias)
        alias shouldBe "alias"
    }

    @Test
    fun `unseal BlockedAddress SealedValue`() {
        val sealedBlockedValue = BlockedAddress.SealedValue(
            "SealedValue",
            SealedAttribute(
                SymmetricKeyEncryptionAlgorithm.AES_CBC_PKCS7PADDING.toString(),
                symmetricKeyId,
                "string",
                sealString("dummyValue"),
            ),
        )

        val unsealed = stringUnsealer.unseal(sealedBlockedValue)
        unsealed shouldBe "dummyValue"
    }
}
