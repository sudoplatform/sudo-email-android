/*
* Copyright © 2023 Anonyome Labs, Inc. All rights reserved.
*/

package com.sudoplatform.sudoemail.keys

import com.sudoplatform.sudokeymanager.KeyType
import com.sudoplatform.sudokeymanager.SecureKeyDelegateInterface
import com.sudoplatform.sudokeymanager.StoreInterface

/**
 * Basic KeyManager store implementation mainly used for testing.
 */
class InMemoryStore() : StoreInterface {

    private val keys: MutableMap<Pair<String, KeyType>, ByteArray> = mutableMapOf()

    @Synchronized
    override fun insertKey(
        keyBytes: ByteArray,
        name: String,
        type: KeyType,
        isExportable: Boolean,
    ) {
        this.keys[Pair(name, type)] = keyBytes
    }

    @Synchronized
    override fun updateKey(data: ByteArray, name: String, type: KeyType) {
        this.keys[Pair(name, type)] = data
    }

    override fun getKey(name: String, type: KeyType): ByteArray? {
        return this.keys[Pair(name, type)]
    }

    @Synchronized
    override fun deleteKey(name: String, type: KeyType) {
        this.keys.remove(Pair(name, type))
    }

    @Synchronized
    override fun reset() {
        this.keys.clear()
    }

    override fun isExportable(): Boolean {
        return true
    }

    override fun close() {}

    override fun setSecureKeyDelegate(secureKeyDelegate: SecureKeyDelegateInterface) {}

    override fun getKeyNames(): Set<String> {
        val names = this.keys.keys.map { it.first }
        return names.toSet()
    }
}
