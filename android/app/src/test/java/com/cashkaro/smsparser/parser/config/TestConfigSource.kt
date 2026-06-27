package com.cashkaro.smsparser.parser.config

/**
 * JVM-test [ConfigSource]: reads the mirrored config from
 * src/test/resources/parser-config/ via the classpath. It mirrors
 * AssetConfigSource (which reads the same JSON from Android assets), so the pure
 * parser core is fully unit-testable on the JVM with no Android Context.
 */
class TestConfigSource(
    private val parser: JsonConfigParser = JsonConfigParser(),
    private val basePath: String = "parser-config",
) : ConfigSource {
    override fun load(): ParserConfig = parser.parse { fileName ->
        val path = "$basePath/$fileName"
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: error("Missing test config resource: $path")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
