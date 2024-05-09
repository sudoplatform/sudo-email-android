/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.enums.EnumEntries

internal abstract class DefaultingEnumSerializer<T : Enum<T>>(values: EnumEntries<out T>, private val defaultValue: T) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(values.first()::class.qualifiedName!!, PrimitiveKind.STRING)
    private val serialized = values.associateBy({ it }, { it.serialName })
    private val deserialized = values.associateBy { it.serialName }

    private val Enum<T>.serialName: String
        get() = this::class.java.getField(this.name).getAnnotation(SerialName::class.java)?.value ?: name

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(serialized.getValue(value))
    }

    override fun deserialize(decoder: Decoder): T {
        return deserialized.getOrDefault(decoder.decodeString(), defaultValue)
    }
}
