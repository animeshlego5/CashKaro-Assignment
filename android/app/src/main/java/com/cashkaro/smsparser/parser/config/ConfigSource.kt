package com.cashkaro.smsparser.parser.config

/**
 * Supplies a fully-parsed [ParserConfig].
 *
 * Implementations:
 *  - AssetConfigSource  — reads Android assets (bridge layer; android.* allowed).
 *  - TestConfigSource   — reads JVM test resources (src/test/resources).
 *
 * Both MUST yield an identical ParserConfig from identical JSON. Injecting config
 * this way is what makes the pure-Kotlin parser core unit-testable on the JVM
 * with no Android Context (C1/C5).
 */
interface ConfigSource {
    fun load(): ParserConfig
}
