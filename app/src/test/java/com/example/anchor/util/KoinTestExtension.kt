package com.example.anchor.util

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module

/**
 * JUnit 5 extension that starts a fresh Koin context before each test
 * and tears it down after, preventing state leaking between tests.
 *
 * Usage:
 *   @RegisterExtension
 *   val koin = KoinTestExtension.create { modules(myModule) }
 */
class KoinTestExtension(
    private val moduleList: List<Module>
) : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        startKoin { modules(moduleList) }
    }

    override fun afterEach(context: ExtensionContext) {
        stopKoin()
    }

    companion object {
        fun create(block: org.koin.core.KoinApplication.() -> Unit): KoinTestExtension {
            val app = org.koin.core.context.GlobalContext
                .getOrNull()
                ?: org.koin.dsl.koinApplication(block)
            val modules = mutableListOf<Module>()
            val tempApp = org.koin.dsl.koinApplication {
                block()
                modules.addAll(this.koin.getAll())
            }
            return KoinTestExtension(modules)
        }

        /** Simpler factory when you already have the module list. */
        fun create(vararg modules: Module) = KoinTestExtension(modules.toList())
    }
}