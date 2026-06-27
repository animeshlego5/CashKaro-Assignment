package com.cashkaro.smsparser.parser

import com.cashkaro.smsparser.parser.config.TestConfigSource
import com.cashkaro.smsparser.parser.model.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1: the assembled pipeline (real Normalizer + MalformedGate; stub stages
 * for the Phase 2 components) returns schema-valid output for every input. With
 * stubs the pipeline excludes everything (never a false-positive INCLUDE); the
 * malformed gate is the one real classifier exercised here.
 */
class PipelineShapeTest {

    private val parser = SmsParser.create(TestConfigSource().load())

    @Test
    fun every_result_is_schema_valid_through_the_real_pipeline_shape() {
        val inputs = listOf(
            "INR 1,250.00 spent on HDFC Bank Credit Card xx5678 at SWIGGY on 03-04-2026. Avl Limit: INR 1,45,300.00.",
            "Use 458219 as your OTP for HDFC Bank Net Banking login. Do NOT share with anyone.",
            "Spent Rs. 2,4",
            "",
        )
        val results = parser.parseAll(inputs)

        assertEquals(inputs.size, results.size)
        results.forEachIndexed { i, r ->
            assertEquals("rawSms must be echoed verbatim", inputs[i], r.rawSms)
            assertTrue("confidence must be in 0..1", r.confidence in 0.0..1.0)
            if (r.decision == Decision.EXCLUDE) {
                assertNotNull("every EXCLUDE needs a reason", r.excludeReason)
                assertNull("every EXCLUDE has a null transaction", r.transaction)
            }
        }
    }

    @Test
    fun malformed_input_fails_safe_to_MALFORMED_SMS() {
        val r = parser.parse("Spent Rs. 2,4")
        assertEquals(Decision.EXCLUDE, r.decision)
        assertEquals("MALFORMED_SMS", r.excludeReason?.name)
        assertNull(r.transaction)
        assertTrue("malformed confidence ~0.1", r.confidence <= 0.2)
    }

    @Test
    fun empty_batch_returns_empty_and_blank_is_malformed() {
        assertTrue(parser.parseAll(emptyList()).isEmpty())
        assertEquals(Decision.EXCLUDE, parser.parse("").decision)
    }
}
