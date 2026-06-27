package com.cashkaro.smsparser

import android.content.res.AssetManager
import com.cashkaro.smsparser.parser.config.ConfigSource
import com.cashkaro.smsparser.parser.config.JsonConfigParser
import com.cashkaro.smsparser.parser.config.ParserConfig

/**
 * The SOLE Android-aware [ConfigSource]: reads the bundled config JSON from
 * assets/parser-config/. It lives in the bridge layer (android.* allowed) so the
 * parser core stays pure and JVM-testable. Parsing itself is delegated to the
 * pure [JsonConfigParser]; this class only supplies the bytes.
 */
class AssetConfigSource(
    private val assets: AssetManager,
    private val parser: JsonConfigParser = JsonConfigParser(),
    private val basePath: String = "parser-config",
) : ConfigSource {
    override fun load(): ParserConfig = parser.parse { fileName ->
        assets.open("$basePath/$fileName").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
