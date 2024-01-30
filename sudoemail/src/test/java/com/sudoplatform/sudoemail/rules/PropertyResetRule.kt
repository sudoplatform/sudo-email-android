/*
 * Copyright © 2024 Anonyome Labs, Inc. All rights reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sudoplatform.sudoemail.rules

import org.junit.Rule
import org.junit.rules.ExternalResource
import kotlin.reflect.KProperty

/**
 * Allows property setup to be done inline instead of inside a [org.junit.Before]
 * annotated method, e.g.
 * ```
 * @Rule @JvmField val reset = PropertyResetRule()
 *
 * val createdUsers by reset.before { ArrayList() }
 * ```
 *
 * For more convenient usage include in your test classes via by property delegation, e.g.
 * ```
 * class SomeTest : PropertyResetter by ActualPropertyResetter() {
 *   private val someProp by before { ... }
 * }
 * ```
 */
class PropertyResetRule : ExternalResource() {

    private val befores = mutableListOf<PropertyResetDelegate<*>>()

    override fun before() {
        befores.forEach { it.reset() }
    }

    /**
     * Creates a property delegate that will reset the variable before running
     * each test.
     */
    fun <T> before(initializer: () -> T): PropertyResetDelegate<T> {
        val propertyResetDelegate = PropertyResetDelegate(initializer)
        befores.add(propertyResetDelegate)
        return propertyResetDelegate
    }
}

/**
 * @see [PropertyResetRule]
 */
class PropertyResetDelegate<out T>(private val initializer: () -> T) {

    private var value: T = initializer()

    operator fun getValue(thisRef: Any?, property: KProperty<*>) = value

    fun reset() {
        value = initializer()
    }
}

interface PropertyResetter {

    @get:Rule
    val reset: PropertyResetRule

    fun <T> before(initializer: () -> T) = reset.before(initializer)
}

class ActualPropertyResetter : PropertyResetter {
    override val reset: PropertyResetRule = PropertyResetRule()
}
