package com.cashkaro.smsparser.parser.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Phase 1: ConfigSource loads a populated config, and Asset/Test sources agree. */
class ConfigLoadTest {

    @Test
    fun testConfigSource_loads_a_populated_config() {
        val config = TestConfigSource().load()
        assertTrue("banks should be seeded", config.banks.isNotEmpty())
        assertTrue("exclusion rules should be seeded", config.exclusionRules.isNotEmpty())
        assertTrue("credit-card signals should be seeded", config.creditCardSignals.isNotEmpty())
        assertTrue("non-card signals should be seeded", config.nonCardSignals.isNotEmpty())
        assertTrue("currencies should include INR", config.currencies.any { it.code == "INR" })
        assertTrue("currencies should include USD", config.currencies.any { it.code == "USD" })
        assertTrue("date formats should be seeded", config.dateFormats.isNotEmpty())
        assertTrue("card products should be seeded", config.cardProducts.isNotEmpty())
        assertTrue("tokens are lowercased at load", config.creditCardSignals.all { it == it.lowercase() })
    }

    @Test
    fun assets_and_test_resources_are_byte_identical() {
        // AssetConfigSource (Android) can't run on the JVM, but it reads the SAME
        // JSON. Assert the test-resource mirror matches the asset originals byte
        // for byte, so both ConfigSources yield an identical ParserConfig and the
        // mirror can't silently drift (important once Phase 2 agents edit assets).
        val assetsDir = File("src/main/assets/parser-config")
        val testDir = File("src/test/resources/parser-config")
        assertTrue("assets config dir should exist at ${assetsDir.absolutePath}", assetsDir.isDirectory)
        for (name in JsonConfigParser.FILE_NAMES) {
            val asset = File(assetsDir, name)
            val mirror = File(testDir, name)
            assertTrue("missing asset config $name", asset.isFile)
            assertTrue("missing mirrored test config $name", mirror.isFile)
            assertEquals("mirror drift in $name", asset.readText(), mirror.readText())
        }
    }
}
